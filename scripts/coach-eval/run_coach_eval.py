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
import sys
import time
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent


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

    def ask(self, thread_id: str, text: str) -> dict:
        status, body = self._call("POST", f"/api/v1/coach/threads/{thread_id}/messages", {"message": text})
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


def contains_any(text: str, needles: list[str]) -> bool:
    lowered = text.lower()
    return any(n.lower() in lowered for n in needles)


def run(args: argparse.Namespace) -> int:
    golden = json.loads(pathlib.Path(args.golden).read_text(encoding="utf-8"))
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
            response = client.ask(thread_id, q["text"])
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
            ok = contains_any(answer, q["expect_any"]) and not verdict.startswith("REFUSED")
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
    lines.append(f"Base `{args.base}` · golden `{pathlib.Path(args.golden).name}` · "
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
    return run(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())
