#!/usr/bin/env python3
"""
The coach evaluation harness.

Runs the golden questions in golden.json against a live stack and reports the numbers the report
quotes: grounding accuracy, refusal precision and recall, envelope honesty, draft first-pass and
repair rates, and the tier distribution. This is the one thing that turns "the coach is grounded"
from a claim into a measurement, which is why it is a script that needs the real service and the
real model rather than a unit test with a scripted one.

Usage:
    python run_coach_eval.py [--base http://localhost:8088] [--token-file ../../.local/demo2.jwt]
                             [--golden golden.json] [--out ../../.local/coach-eval.md] [--only grounded,refusal]
                             [--today 2026-10-09]
    python run_coach_eval.py --print-facts [--today 2026-10-09]   # the expectations for a day, no stack
    python run_coach_eval.py --self-test                           # checks the day arithmetic, no stack

The steady seed is rolling: it always ends on today in Singapore, so the streak, this week's and last
week's training and the last session move with the weekday the seed runs on. Those expectations are
not written into golden.json as numbers. A golden string holds a placeholder such as {streakSaid},
and day_facts() fills it from the same day-relative plan seed-demo-fitsync.sql lays out, for the date
given by --today or today in Singapore. Run the evaluation on the day the seed ran.

Each question runs on its own thread so prior turns cannot leak into the next answer. Drafts run
through the drafting endpoint and the metrics endpoint is read before and after, so the numbers
reported are for this run alone. Every raw answer is written next to its verdict so a low score can
be read, not just counted.

Stdlib only, on purpose: it has to run on the recording machine with nothing installed.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
SGT = dt.timezone(dt.timedelta(hours=8))

# Today's breakfast in the steady seed. The seed scales each day's portions by meal_mult, chosen by the
# day's index in the 60-day window, and today is always index 59 (multiplier 1.17), so the breakfast,
# the only meal today carries, is the same on every day. Taken from a seeded database: three items
# (oats, banana, Greek yoghurt) summed as the Diary and the coach sum them.
BREAKFAST_KCAL = 506
BREAKFAST_PROTEIN_G = 31
CALORIE_TARGET = 2100
PROTEIN_TARGET_G = 140

PLACEHOLDER = re.compile(r"\{[A-Za-z]\w*\}")
NUMBER_WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"]


def steady_session_days(today: dt.date) -> list[dt.date]:
    """The steady persona's completed session days before today, as section 2b of the seed plans them."""
    d_start = today - dt.timedelta(days=59)
    monday_of_start = d_start - dt.timedelta(days=d_start.isoweekday() - 1)
    w_last = (today - monday_of_start).days // 7 - 1
    # The Thursday run moved to Tuesday: in the current week once Tuesday has passed, else last week.
    moved_week = w_last + (1 if today.isoweekday() >= 3 else 0)
    days = []
    for i in range(59):  # d_start .. today - 1; nothing is trained today
        d = d_start + dt.timedelta(days=i)
        dow, w = d.isoweekday(), (d - monday_of_start).days // 7
        training = dow in (1, 4, 5, 6)
        skipped = (w == 0 and dow == 4) or (w == 1 and dow == 6)
        if w == moved_week and dow in (2, 4):
            training = dow == 2
        if training and not skipped:
            days.append(d)
    return days


def active_streak(days: list[dt.date], today: dt.date) -> int:
    """ActiveStreak.days: consecutive active days back from today, or from yesterday when today is not."""
    active = set(days)
    start = today if today in active else today - dt.timedelta(days=1)
    streak = 0
    while start - dt.timedelta(days=streak) in active:
        streak += 1
    return streak


def spoken(d: dt.date) -> str:
    """A date as ReadFactsTool says it, "4 October"."""
    return f"{d.day} {d:%B}"


def count_phrases(n: int) -> list[str]:
    """What an answer giving the count n may say."""
    if n == 0:
        # A bare "0" would match any number with a zero in it, such as the 2100 kcal target.
        return ["0 days", "0 sessions", "zero", "none", "no days", "no sessions", "no training", "not yet",
                "haven't", "have not"]
    return [str(n), NUMBER_WORDS[n] if n < len(NUMBER_WORDS) else str(n)]


def day_facts(today: dt.date) -> dict:
    """The date-dependent expectations for the steady account seeded on `today` (Singapore)."""
    days = steady_session_days(today)
    week_start = today - dt.timedelta(days=today.isoweekday() - 1)
    last_week_start = week_start - dt.timedelta(days=7)
    last_week_end = last_week_start + dt.timedelta(days=6)
    this_week = [d for d in days if d >= week_start]
    last_week = [d for d in days if last_week_start <= d < week_start]
    rolling7 = [d for d in days if d >= today - dt.timedelta(days=6)]
    streak = active_streak(days, today)
    last = days[-1]
    last_said = [spoken(last), f"{last:%A}", f"{last:%b} {last.day}", f"{last.day} {last:%b}"]
    if last == today - dt.timedelta(days=1):
        last_said.insert(0, "yesterday")
    n = len(this_week)
    word = NUMBER_WORDS[n]
    return {
        "today": today.isoformat(),
        "consumedCalories": BREAKFAST_KCAL,
        "remainingCalories": CALORIE_TARGET - BREAKFAST_KCAL,
        "proteinConsumedGrams": BREAKFAST_PROTEIN_G,
        "proteinShortGrams": PROTEIN_TARGET_G - BREAKFAST_PROTEIN_G,
        "streakDays": streak,
        "streakSaid": count_phrases(streak) + (["no streak", "no active streak", "not on a streak", "broken",
                                                 "ended", "reset"] if streak == 0 else []),
        "thisWeekDays": n,
        "thisWeekSaid": count_phrases(n),
        "thisWeekWord": [word, word.capitalize()] + (["none", "no days", "not yet"] if n == 0 else []),
        "thisWeekWrongWords": [f"{NUMBER_WORDS[k]} day" + ("" if k == 1 else "s")
                               for k in (n - 1, n + 1) if 0 <= k < len(NUMBER_WORDS)],
        "lastWeekSessions": len(last_week),
        "lastWeekDates": [spoken(last_week_start), spoken(last_week_end),
                          f"{last_week_start.day} {last_week_start:%b}", f"{last_week_end.day} {last_week_end:%b}"],
        # The weekly recap counts a day with any workout or meal row as active, and steady logs every day.
        "lastWeekActiveDays": 7,
        "rolling7Sessions": len(rolling7),
        "sessionCountsSaid": sorted({str(len(last_week)), str(len(rolling7))}),
        "lastSessionSaid": last_said,
    }


def fill(value, facts: dict):
    """Fills the {placeholders} of a golden string, or of every string in a list. A list item that is
    exactly one placeholder naming a list fact is replaced by that list's items."""
    if isinstance(value, str):
        return value.format_map(facts)
    if isinstance(value, list):
        out: list = []
        for item in value:
            key = item[1:-1] if isinstance(item, str) and item.startswith("{") and item.endswith("}") else None
            if key in facts and isinstance(facts[key], list):
                out.extend(facts[key])
            else:
                out.append(fill(item, facts))
        return out
    return value


def resolve_golden(golden: dict, today: dt.date) -> dict:
    """The golden file with its date-dependent expectations filled in for `today`."""
    facts = {**golden.get("facts", {}), **day_facts(today)}
    fields = ("text", "setup", "expect_any", "forbid_any")
    questions = [{k: (fill(v, facts) if k in fields else v) for k, v in q.items()} for q in golden["questions"]]
    return {**golden, "facts": facts, "questions": questions}


def self_test() -> int:
    """Checks the day arithmetic against dates worked out by hand from the seed, and that every
    placeholder in golden.json resolves on every weekday."""
    checks = {
        # Fri 9 Oct 2026: the run moved to Tue 6 Oct, so this week holds Mon 5 and Tue 6 and Thu 8 is empty.
        dt.date(2026, 10, 9): {"streakDays": 0, "thisWeekDays": 2, "lastWeekSessions": 4, "rolling7Sessions": 3,
                               "lastWeekDates": "28 September", "lastSessionSaid": "6 October"},
        # Tue 6 Oct 2026: the moved run is last week's (Tue 29 Sep) and Mon 5 Oct was trained yesterday.
        dt.date(2026, 10, 6): {"streakDays": 1, "thisWeekDays": 1, "lastWeekSessions": 4, "rolling7Sessions": 3,
                               "lastWeekDates": "4 October", "lastSessionSaid": "yesterday"},
        # Sun 11 Oct 2026: Fri 9 and Sat 10 were trained, the plan's last two days.
        dt.date(2026, 10, 11): {"streakDays": 2, "thisWeekDays": 4, "lastWeekSessions": 4},
        # Mon 12 Oct 2026: nothing yet this week, and Sunday was a rest day.
        dt.date(2026, 10, 12): {"streakDays": 0, "thisWeekDays": 0, "lastWeekSessions": 4,
                                "lastWeekDates": "5 October"},
    }
    failures = 0
    for day, expected in checks.items():
        facts = day_facts(day)
        for key, want in expected.items():
            got = facts[key]
            ok = want in got if isinstance(got, list) else got == want
            if not ok:
                failures += 1
                print(f"FAIL {day} {key}: expected {want!r}, got {got!r}")
    golden = json.loads((HERE / "golden.json").read_text(encoding="utf-8"))
    for day in (dt.date(2026, 10, 5) + dt.timedelta(days=i) for i in range(7)):
        resolved = resolve_golden(golden, day)
        unresolved = [q["id"] for q in resolved["questions"]
                      if PLACEHOLDER.search(json.dumps([q.get(k) for k in ("text", "setup", "expect_any", "forbid_any")]))]
        if unresolved:
            failures += 1
            print(f"FAIL {day}: unresolved placeholders in {unresolved}")
    print("self-test passed" if failures == 0 else f"self-test: {failures} failure(s)")
    return 0 if failures == 0 else 1


class Client:
    def __init__(self, base: str, token: str):
        self.base = base.rstrip("/")
        self.token = token

    def _call(self, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None]:
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Authorization", "Bearer " + self.token)
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                raw = resp.read()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as err:
            raw = err.read()
            try:
                return err.code, json.loads(raw)
            except Exception:
                return err.code, {"raw": raw.decode(errors="replace")[:300]}

    def new_thread(self) -> str:
        status, body = self._call("POST", "/api/v1/coach/threads", {})
        if status != 201:
            raise SystemExit(f"could not create a thread: {status} {body}")
        return body["threadId"]

    def ask(self, thread_id: str, text: str, context: dict | None = None) -> dict:
        payload: dict = {"message": text}
        if context:
            payload["context"] = with_card_period(context)
        status, body = self._call("POST", f"/api/v1/coach/threads/{thread_id}/messages", payload)
        if status != 200:
            raise RuntimeError(f"message failed: {status} {body}")
        return body

    def turn(self, thread_id: str, turn_id: str) -> dict:
        status, body = self._call("GET", f"/api/v1/coach/threads/{thread_id}")
        if status != 200:
            raise RuntimeError(f"thread read failed: {status}")
        for turn in body["turns"]:
            if turn["turnId"] == turn_id:
                return turn
        raise RuntimeError("answer turn not found in thread")

    def draft(self, request: str | None) -> tuple[int, dict]:
        return self._call("POST", "/api/v1/coach/session-drafts", {"request": request} if request else {})

    def draft_metrics(self) -> dict:
        status, body = self._call("GET", "/api/v1/coach/session-drafts/metrics")
        if status != 200:
            raise RuntimeError(f"metrics failed: {status}")
        return body


def with_card_period(context: dict) -> dict:
    """A card without dates is asked about the last 28 days, so the golden file never goes stale."""
    card = context.get("card")
    if not card or ("from" in card and "to" in card):
        return context
    today = dt.date.today()
    return {**context, "card": {**card, "from": (today - dt.timedelta(days=27)).isoformat(), "to": today.isoformat()}}


def contains_any(text: str, needles: list[str]) -> bool:
    lowered = text.lower()
    return any(n.lower() in lowered for n in needles)


def run(args: argparse.Namespace) -> int:
    today = dt.date.fromisoformat(args.today) if args.today else dt.datetime.now(SGT).date()
    golden = resolve_golden(json.loads(pathlib.Path(args.golden).read_text(encoding="utf-8")), today)
    if args.print_facts:
        print(json.dumps(golden["facts"], indent=2))
        return 0
    token = pathlib.Path(args.token_file).read_text(encoding="utf-8").strip()
    client = Client(args.base, token)
    only = set(args.only.split(",")) if args.only else None

    questions = [q for q in golden["questions"] if not only or q["category"] in only]
    started = dt.datetime.now(dt.timezone.utc)
    rows: list[dict] = []
    tiers: dict[str, int] = {}

    # Chat-style categories, one thread each so nothing leaks between questions.
    for q in questions:
        if q["category"] == "draft":
            continue
        thread_id = client.new_thread()
        t0 = time.perf_counter()
        try:
            response = client.ask(thread_id, q["text"], q.get("context"))
            turn = client.turn(thread_id, response["turnId"])
            answer = turn["content"]
            verdict = turn.get("guardrailVerdict") or ""
            tier = turn.get("tier")
            tools = [t["tool"] for t in turn.get("tools", [])]
            error = None
        except Exception as exc:  # a failed call is a failed question, not a failed run
            answer, verdict, tier, tools, error = "", "", None, [], str(exc)
        latency = int((time.perf_counter() - t0) * 1000)
        tiers[str(tier)] = tiers.get(str(tier), 0) + 1

        if q["category"] == "grounded":
            ok = (contains_any(answer, q["expect_any"]) and not verdict.startswith("REFUSED")
                  and not contains_any(answer, q.get("forbid_any", []))
                  and ("expect_tool" not in q or q["expect_tool"] in tools))
        elif q["category"] == "refusal":
            ok = verdict == q["expect_verdict"]
        elif q["category"] == "not_refusal":
            ok = not verdict.startswith("REFUSED")
        elif q["category"] == "envelope":
            ok = contains_any(answer, q["expect_any"]) and not contains_any(answer, q.get("forbid_any", []))
        else:
            ok = False
        rows.append({**q, "answer": answer, "verdict": verdict, "tier": tier, "tools": tools,
                     "latency_ms": latency, "ok": ok, "error": error})
        print(f"{'ok ' if ok else 'BAD'} {q['id']} [{q['category']}] {latency:5d}ms  {answer[:90]!r}", flush=True)

    # Drafts: read the metrics before and after so the rates are for this run only.
    drafts = [q for q in questions if q["category"] == "draft"]
    draft_rows: list[dict] = []
    before = client.draft_metrics() if drafts else None
    for q in drafts:
        t0 = time.perf_counter()
        status, body = client.draft(q.get("request"))
        latency = int((time.perf_counter() - t0) * 1000)
        ok = status == 201 and body.get("source") in ("MODEL", "MODEL_REPAIRED", "RULE_FALLBACK")
        draft_rows.append({**q, "status": status, "source": body.get("source") if ok else None,
                           "attempts": body.get("validationAttempts") if ok else None,
                           "title": (body.get("session") or {}).get("title") if ok else body,
                           "latency_ms": latency, "ok": ok})
        print(f"{'ok ' if ok else 'BAD'} {q['id']} [draft] {latency:5d}ms  {draft_rows[-1]['source']} "
              f"attempts={draft_rows[-1]['attempts']}  {str(draft_rows[-1]['title'])[:60]!r}", flush=True)
    after = client.draft_metrics() if drafts else None

    report = render(golden, rows, draft_rows, before, after, tiers, started, args)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8")
    print(f"\nreport written to {out}")
    return 0


def rate(hits: int, total: int) -> str:
    return "n/a" if total == 0 else f"{hits}/{total} = {100.0 * hits / total:.0f}%"


def render(golden, rows, draft_rows, before, after, tiers, started, args) -> str:
    by_cat: dict[str, list[dict]] = {}
    for r in rows:
        by_cat.setdefault(r["category"], []).append(r)

    grounded = by_cat.get("grounded", [])
    refusal = by_cat.get("refusal", [])
    not_refusal = by_cat.get("not_refusal", [])
    envelope = by_cat.get("envelope", [])

    refused_true = sum(1 for r in refusal if r["ok"])
    refused_false = sum(1 for r in not_refusal if r["verdict"].startswith("REFUSED"))
    precision_total = refused_true + refused_false

    lines = []
    lines.append(f"# Coach evaluation · {started.strftime('%Y-%m-%d %H:%M UTC')}")
    lines.append("")
    lines.append(f"Base `{args.base}` · golden `{pathlib.Path(args.golden).name}` for {golden['facts']['today']} · "
                 f"{len(rows)} chat questions, {len(draft_rows)} drafts.")
    lines.append("")
    lines.append("## The numbers")
    lines.append("")
    lines.append("| Measure | Result | What it means |")
    lines.append("| --- | --- | --- |")
    lines.append(f"| Grounding accuracy | {rate(sum(r['ok'] for r in grounded), len(grounded))} | "
                 "answer quotes the computed fact the question is about |")
    lines.append(f"| Refusal recall | {rate(refused_true, len(refusal))} | "
                 "plans below the calorie floor that the guardrail refused |")
    lines.append(f"| Refusal precision | {rate(refused_true, precision_total)} | "
                 "of everything refused, how much deserved it |")
    lines.append(f"| False refusals | {refused_false} of {len(not_refusal)} | "
                 "ordinary calorie questions wrongly refused |")
    lines.append(f"| Envelope honesty | {rate(sum(r['ok'] for r in envelope), len(envelope))} | "
                 "questions outside the facts answered with an admission rather than an invention |")
    if before and after:
        d_total = after["total"] - before["total"]
        d_model = after["model"] - before["model"]
        d_rep = after["modelRepaired"] - before["modelRepaired"]
        d_fb = after["ruleFallback"] - before["ruleFallback"]
        lines.append(f"| Draft model success | {rate(d_model + d_rep, d_total)} | "
                     "drafts a model produced, with or without a repair |")
        lines.append(f"| Draft first-pass rate | {rate(d_model, d_total)} | "
                     "drafts that passed the validator on the first attempt |")
        lines.append(f"| Draft repair rate | {rate(d_rep, d_model + d_rep)} | "
                     "model drafts that needed the one repair round |")
        lines.append(f"| Draft fallback rate | {rate(d_fb, d_total)} | "
                     "drafts the rule engine built because the model failed twice |")
    lines.append("")
    tier_names = {"0": "rule", "1": "template", "2": "local model", "3": "frontier model", "-1": "model off", "None": "no answer"}
    dist = ", ".join(f"{tier_names.get(k, k)} {v}" for k, v in sorted(tiers.items()))
    lines.append(f"Tier distribution over the chat questions: {dist}.")
    lat = sorted(r["latency_ms"] for r in rows if not r["error"])
    if lat:
        lines.append(f"Latency: median {lat[len(lat)//2]} ms, p90 {lat[int(len(lat)*0.9)]} ms, max {lat[-1]} ms.")
    lines.append("")
    lines.append("## Every answer")
    lines.append("")
    lines.append("A low score is read here, not just counted.")
    lines.append("")
    for cat in ("grounded", "refusal", "not_refusal", "envelope"):
        if cat not in by_cat:
            continue
        lines.append(f"### {cat}")
        lines.append("")
        lines.append("| id | ok | verdict | tier | tools | ms | question | answer |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for r in by_cat[cat]:
            answer = (r["error"] or r["answer"]).replace("|", "\\|").replace("\n", " ")
            lines.append(f"| {r['id']} | {'✓' if r['ok'] else '✗'} | {r['verdict']} | {r['tier']} | "
                         f"{', '.join(r['tools'])} | {r['latency_ms']} | {r['text'].replace('|', '/')} | {answer} |")
        lines.append("")
    if draft_rows:
        lines.append("### draft")
        lines.append("")
        lines.append("| id | ok | source | attempts | ms | request | title |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- |")
        for r in draft_rows:
            lines.append(f"| {r['id']} | {'✓' if r['ok'] else '✗'} | {r['source']} | {r['attempts']} | "
                         f"{r['latency_ms']} | {r.get('request') or '(none)'} | {str(r['title']).replace('|', '/')} |")
        lines.append("")
    lines.append("## Facts the grounded expectations assume")
    lines.append("")
    lines.append("```json")
    lines.append(json.dumps(golden.get("facts", {}), indent=2))
    lines.append("```")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8088")
    parser.add_argument("--token-file", default=str(HERE / ".." / ".." / ".local" / "demo2.jwt"))
    parser.add_argument("--golden", default=str(HERE / "golden.json"))
    parser.add_argument("--out", default=str(HERE / ".." / ".." / ".local" /
                                             f"coach-eval-{dt.datetime.now():%Y%m%d-%H%M}.md"))
    parser.add_argument("--only", default=None, help="comma-separated categories to run")
    parser.add_argument("--today", default=None,
                        help="the date the seed ran on, YYYY-MM-DD (default: today in Singapore)")
    parser.add_argument("--print-facts", action="store_true", help="print the expectations for the day and stop")
    parser.add_argument("--self-test", action="store_true", help="check the day arithmetic and stop")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    return run(args)


if __name__ == "__main__":
    sys.exit(main())
