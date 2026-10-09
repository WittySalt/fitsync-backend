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
                             [--now 2026-10-09T02:00] [--pace 0.6]
    python run_coach_eval.py --print-facts [--now 2026-10-09T02:00]   # the expectations for a run, no stack
    python run_coach_eval.py --self-test                           # checks the day arithmetic and the 429 handling, no stack

The steady seed is rolling: it always ends on today in Singapore, so the streak, this week's and last
week's training and the last session move with the weekday the seed runs on. Those expectations are
not written into golden.json as numbers. A golden string holds a placeholder such as {streakSaid},
and day_facts() fills it from the same day-relative plan seed-demo-fitsync.sql lays out, for the seed's
run time given by --now (or --today, taken as 12:00), else now in Singapore. Pass the time the seed ran:
a run before 07:30 has no breakfast today. Run the evaluation on the day the seed ran.

Each question runs on its own thread so prior turns cannot leak into the next answer. Drafts run
through the drafting endpoint and the metrics endpoint is read before and after, so the numbers
reported are for this run alone. Every raw answer is written next to its verdict so a low score can
be read, not just counted.

Every call honours a 429. The gateway's per-user rate limit (code RATE_LIMITED) is waited out for its
Retry-After header, else the seconds in its message, else 5 s, and the call is tried again, up to six
times. The coach's own limit (code COACH_LIMIT) is waited out the same way only when it frees within a
minute; a longer wait fails the call as before. --pace spaces the calls out so a cloud run stays under
the gateway limit in the first place. The retries are counted in the console and the report.

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
# day's index in the 60-day window, and today is always index 59 (multiplier 1.17), so the breakfast is
# the same on every day. Taken from a seeded database: three items (oats, banana, Greek yoghurt) summed
# as the Diary and the coach sum them. The seed writes it only once 07:30 has passed on the run's clock.
BREAKFAST_KCAL = 506
BREAKFAST_PROTEIN_G = 31
BREAKFAST_AT = dt.time(7, 30)
CALORIE_TARGET = 2100
PROTEIN_TARGET_G = 140

# How a call answers a 429: up to MAX_RETRIES more attempts, each after the wait the refusal asks for.
MAX_RETRIES = 6
DEFAULT_WAIT_S = 5.0
# The coach's own limit is waited out only when it frees this soon; an hourly window that is full is not.
COACH_LIMIT_MAX_WAIT_S = 60.0
RETRY_IN_MESSAGE = re.compile(r"try again in (\d+(?:\.\d+)?) seconds?", re.IGNORECASE)

PLACEHOLDER = re.compile(r"\{[A-Za-z]\w*\}")
# A needle that starts with "re:" is a regular expression, matched without regard to case.
REGEX_PREFIX = "re:"
# A zero intake as a figure: "0 calories", "0 kcal", "0 g", "0 grams". Not preceded by a digit, a point or
# a comma, so "2100 kcal", "140 g" and "20 g" do not match.
ZERO_FIGURE = r"re:(?<![\d.,])0 ?(calories|kcal|grams|g)\b"
WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
NUMBER_WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"]
PLAN_DAYS = (1, 4, 5, 6)  # Monday and Friday strength, a Thursday run, Saturday badminton
# steady's WORKOUT_FREQUENCY goal. Every session counts toward it, the rest-day mobility included, so the
# seed sets six: four plan days and two or three rest days a week.
WEEKLY_GOAL = 6


def steady_sessions(today: dt.date) -> list[tuple[dt.date, int | None]]:
    """The steady persona's completed sessions before today, as section 2b of the seed plans them: each
    one's date and the plan weekday it counts for (None for a light mobility day outside the plan)."""
    d_start = today - dt.timedelta(days=59)
    monday_of_start = d_start - dt.timedelta(days=d_start.isoweekday() - 1)
    w_last = (today - monday_of_start).days // 7 - 1
    # The Thursday run moved to Tuesday: in the current week once Tuesday has passed, else last week.
    moved_week = w_last + (1 if today.isoweekday() >= 3 else 0)
    sessions = []
    for i in range(59):  # d_start .. today - 1; nothing is trained today
        d = d_start + dt.timedelta(days=i)
        dow, w, o = d.isoweekday(), (d - monday_of_start).days // 7, (today - d).days
        plan_dow = dow if dow in PLAN_DAYS else None
        skipped = (w == 0 and dow == 4) or (w == 1 and dow == 6)
        if w == moved_week and dow in (2, 4):
            plan_dow, skipped = (4 if dow == 2 else None), False
        if plan_dow is not None and not skipped:
            sessions.append((d, plan_dow))
        elif 1 <= o <= 14:
            sessions.append((d, None))  # the light mobility session of a rest day in the last two weeks
    return sessions


def steady_session_days(today: dt.date) -> list[dt.date]:
    return [d for d, _ in steady_sessions(today)]


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


def day_facts(now: dt.datetime | dt.date) -> dict:
    """The date-dependent expectations for the steady account seeded at `now` (Singapore wall clock).
    A bare date means a run at 12:00, after breakfast."""
    if not isinstance(now, dt.datetime):
        now = dt.datetime.combine(now, dt.time(12, 0))
    today = now.date()
    breakfast = now.time() >= BREAKFAST_AT
    sessions = steady_sessions(today)
    days = [d for d, _ in sessions]
    week_start = today - dt.timedelta(days=today.isoweekday() - 1)
    last_week_start = week_start - dt.timedelta(days=7)
    last_week_end = last_week_start + dt.timedelta(days=6)
    this_week = [d for d in days if d >= week_start]
    planned_done = len({p for d, p in sessions if d >= week_start and p is not None})
    last_week = [d for d in days if last_week_start <= d < week_start]
    rolling7 = [d for d in days if d >= today - dt.timedelta(days=6)]
    streak = active_streak(days, today)
    last = days[-1]
    last_said = [spoken(last), f"{last:%A}", f"{last:%b} {last.day}", f"{last.day} {last:%b}"]
    if last == today - dt.timedelta(days=1):
        last_said.insert(0, "yesterday")
    # "How many days did I train this week" can be answered with every session this week (light days
    # included) or with the plan days done, so both counts are right and neither is a wrong word.
    n, p = len(this_week), planned_done
    valid = {n, p}
    words = [w for k in sorted(valid) for w in (NUMBER_WORDS[k], NUMBER_WORDS[k].capitalize())]
    goal_left = max(0, WEEKLY_GOAL - n)
    kcal = BREAKFAST_KCAL if breakfast else 0
    protein = BREAKFAST_PROTEIN_G if breakfast else 0
    # A zero figure is a right answer before breakfast ("You have eaten 0 calories today"). It is a pattern,
    # not a plain "0 kcal" or "0 g", because those would match inside "2100 kcal", "140 g" and "20 g".
    nothing_yet = ["nothing", "no meals", "not logged", "haven't logged", "have not logged", "zero", "none", "yet to",
                   ZERO_FIGURE]
    # "Tuesday two weeks ago": the Tuesday of the Monday-to-Sunday week two weeks before this one.
    tuesday = week_start - dt.timedelta(days=14) + dt.timedelta(days=1)
    near = [tuesday + dt.timedelta(days=k) for k in range(-7, 8) if k != 0]
    # Three months from today, for the weight question: the date must not slip back a year.
    month = today.month + 3
    in_three_months = dt.date(today.year + (month - 1) // 12, (month - 1) % 12 + 1, 1)
    return {
        "today": today.isoformat(),
        "seedTime": now.strftime("%Y-%m-%d %H:%M"),
        "breakfastLogged": breakfast,
        "consumedCalories": kcal,
        "remainingCalories": CALORIE_TARGET - kcal,
        "proteinConsumedGrams": protein,
        "proteinShortGrams": PROTEIN_TARGET_G - protein,
        "consumedSaid": [str(kcal)] if breakfast else nothing_yet,
        "proteinSaid": [str(protein)] if breakfast else nothing_yet,
        "streakDays": streak,
        "streakSaid": count_phrases(streak) + (["no streak", "no active streak", "not on a streak", "broken",
                                                 "ended", "reset"] if streak == 0 else []),
        "thisWeekDays": n,
        "thisWeekPlanDaysDone": p,
        "thisWeekSaid": sorted(set(count_phrases(n) + count_phrases(p))),
        "thisWeekWord": words + (["none", "no days", "not yet"] if 0 in valid else []),
        "thisWeekWrongWords": [f"{NUMBER_WORDS[k]} day" + ("" if k == 1 else "s")
                               for k in sorted({n - 1, n + 1, p - 1, p + 1} - valid) if 0 <= k < len(NUMBER_WORDS)],
        "weeklyGoal": WEEKLY_GOAL,
        "goalLeft": goal_left,
        # "two to go" on a Friday; once the goal is met, the answer says so.
        "goalLeftSaid": (count_phrases(goal_left) if goal_left else ["met", "reached", "hit", "done"]),
        "lastWeekSessions": len(last_week),
        # The recap's active days against the goal: 7 against 6, so last week reads as met.
        "lastWeekGoalMet": 7 >= WEEKLY_GOAL,
        "lastWeekDates": [spoken(last_week_start), spoken(last_week_end),
                          f"{last_week_start.day} {last_week_start:%b}", f"{last_week_end.day} {last_week_end:%b}"],
        # The weekly recap counts a day with any workout or meal row as active, and steady logs every day.
        "lastWeekActiveDays": 7,
        "rolling7Sessions": len(rolling7),
        "sessionCountsSaid": sorted({str(len(last_week)), str(len(rolling7))}),
        "lastSessionSaid": last_said,
        "twoWeeksAgoTuesday": tuesday.isoformat(),
        "twoWeeksAgoTuesdaySaid": [spoken(tuesday), f"{tuesday.day} {tuesday:%b}", f"{tuesday:%B} {tuesday.day}",
                                   f"{tuesday:%b} {tuesday.day}", tuesday.isoformat()],
        # Another day of those weeks called Tuesday, such as "Tuesday 24 September" for a Thursday, or the
        # right date given another weekday's name.
        "twoWeeksAgoTuesdayWrong": sorted({f"Tuesday {d.day} " for d in near} | {f"Tuesday, {d.day} " for d in near}
                                          | {f"{name} {tuesday.day} " for name in WEEKDAYS if name != "Tuesday"}),
        "threeMonthsWrongYear": [f"{in_three_months.year - 1}-{in_three_months.month:02d}",
                                 f"{in_three_months:%B} {in_three_months.year - 1}"],
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


def resolve_golden(golden: dict, now: dt.datetime | dt.date) -> dict:
    """The golden file with its date-dependent expectations filled in for a seed run at `now`."""
    facts = {**golden.get("facts", {}), **day_facts(now)}
    fields = ("text", "setup", "expect_any", "forbid_any")
    questions = [{k: (fill(v, facts) if k in fields else v) for k, v in q.items()} for q in golden["questions"]]
    return {**golden, "facts": facts, "questions": questions}


def self_test() -> int:
    """Checks the day arithmetic against dates worked out by hand from the seed, and that every
    placeholder in golden.json resolves on every weekday."""
    t = dt.datetime
    # Worked out from the verification tables of seeded databases (see scripts/README.md). Light mobility
    # sessions fill every rest day of the last 14 days, so the streak always ends yesterday.
    checks = {
        # Fri 9 Oct 02:00: before breakfast. This week Mon 5 strength, Tue 6 the moved Thursday run, Wed 7
        # and Thu 8 mobility; two plan days done; Friday strength still to do.
        t(2026, 10, 9, 2, 0): {"streakDays": 15, "thisWeekDays": 4, "thisWeekPlanDaysDone": 2, "goalLeft": 2,
                               "goalLeftSaid": "two", "lastWeekGoalMet": True,
                               "lastWeekSessions": 7, "rolling7Sessions": 6, "consumedCalories": 0,
                               "breakfastLogged": False, "lastWeekDates": "28 September",
                               "lastSessionSaid": "yesterday", "consumedSaid": "nothing"},
        t(2026, 10, 9, 10, 0): {"streakDays": 15, "consumedCalories": 506, "remainingCalories": 1594},
        # Sat 10 Oct 10:00: Friday strength done, badminton today.
        t(2026, 10, 10, 10, 0): {"streakDays": 16, "thisWeekDays": 5, "thisWeekPlanDaysDone": 3, "goalLeft": 1,
                                 "lastWeekSessions": 7, "consumedCalories": 506},
        t(2026, 10, 5, 10, 0): {"streakDays": 14, "thisWeekDays": 0, "thisWeekPlanDaysDone": 0, "goalLeft": 6,
                                "lastWeekDates": "4 October"},
        t(2026, 10, 6, 10, 0): {"streakDays": 15, "thisWeekDays": 1, "thisWeekPlanDaysDone": 1},
        t(2026, 10, 8, 10, 0): {"streakDays": 14, "thisWeekDays": 3, "thisWeekPlanDaysDone": 2},
        t(2026, 10, 11, 10, 0): {"streakDays": 17, "thisWeekDays": 6, "thisWeekPlanDaysDone": 4, "goalLeft": 0,
                                 "goalLeftSaid": "met"},
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
        right = {facts["thisWeekDays"], facts["thisWeekPlanDaysDone"]}
        if any(w.startswith(NUMBER_WORDS[k] + " ") for w in facts["thisWeekWrongWords"] for k in right):
            failures += 1
            print(f"FAIL {day}: a right count is listed as a wrong word")
    # Answers from the run of 9 Oct 02:19, before breakfast, and what each must and must not match.
    before_breakfast = day_facts(t(2026, 10, 9, 2, 19))
    answer_checks = [
        ("You have eaten 0 calories today. Your target is 2100 calories.", before_breakfast["consumedSaid"], True),
        ("You have had 0 g of protein today. Your target is 140 g.", before_breakfast["proteinSaid"], True),
        ("You have had 0g of protein so far.", before_breakfast["proteinSaid"], True),
        ("You have had 20 g of protein against 140 g.", before_breakfast["proteinSaid"], False),
        ("You have eaten 2100 kcal and 1.0 kcal.", before_breakfast["consumedSaid"], False),
        ("On Tuesday 22 September you had 2449 calories.", before_breakfast["twoWeeksAgoTuesdaySaid"], True),
        ("On Tuesday 24 September you had 2449 calories.", before_breakfast["twoWeeksAgoTuesdayWrong"], True),
        ("On Tuesday 22 September you had 2449 calories.", before_breakfast["twoWeeksAgoTuesdayWrong"], False),
        ("In three months, around 2026-01-09, you would be lighter.", before_breakfast["threeMonthsWrongYear"], True),
        ("In three months, around 2027-01-09, you would be lighter.", before_breakfast["threeMonthsWrongYear"], False),
    ]
    for answer, needles, want in answer_checks:
        if contains_any(answer, needles) != want:
            failures += 1
            print(f"FAIL answer check: {answer!r} should {'' if want else 'not '}match {needles!r}")
    lift_total = next(q for q in json.loads((HERE / "golden.json").read_text(encoding="utf-8"))["questions"]
                      if q["id"] == "e03")["forbid_any"]
    for answer, want in [("You lifted a total of 12,400 kg last month.", True),
                         ("Your total volume was 8,350.5 kg.", True),
                         ("You completed 8 strength sessions totalling 400 minutes. Your heaviest set was 22.5 kg "
                          "for 10 reps on the bench press.", False)]:
        if contains_any(answer, lift_total) != want:
            failures += 1
            print(f"FAIL e03 forbid: {answer!r} should {'' if want else 'not '}be forbidden")
    if before_breakfast["twoWeeksAgoTuesday"] != "2026-09-22":
        failures += 1
        print(f"FAIL Tuesday two weeks ago: {before_breakfast['twoWeeksAgoTuesday']}")
    golden = json.loads((HERE / "golden.json").read_text(encoding="utf-8"))
    for day in [t(2026, 10, 5 + i, h, 0) for i in range(7) for h in (2, 10)]:
        resolved = resolve_golden(golden, day)
        unresolved = [q["id"] for q in resolved["questions"]
                      if PLACEHOLDER.search(json.dumps([q.get(k) for k in ("text", "setup", "expect_any", "forbid_any")]))]
        if unresolved:
            failures += 1
            print(f"FAIL {day}: unresolved placeholders in {unresolved}")
    failures += retry_self_test()
    print("self-test passed" if failures == 0 else f"self-test: {failures} failure(s)")
    return 0 if failures == 0 else 1


def _seconds(value) -> float | None:
    try:
        seconds = float(value)
    except (TypeError, ValueError):
        return None
    return seconds if seconds >= 0 else None


def retry_wait(status: int, headers, body) -> float | None:
    """How long to wait before trying a refused call again, or None when it is not to be retried.

    A gateway refusal (RATE_LIMITED, or any other 429) is waited out for its Retry-After header, else the
    seconds named in its message, else DEFAULT_WAIT_S. The coach's own COACH_LIMIT is waited out only when
    its retryAfterSeconds is COACH_LIMIT_MAX_WAIT_S or less, because a full hourly window will not free
    while the run waits."""
    if status != 429:
        return None
    body = body if isinstance(body, dict) else {}
    if body.get("code") == "COACH_LIMIT":
        seconds = _seconds(body.get("retryAfterSeconds"))
        if seconds is None and headers is not None:
            seconds = _seconds(headers.get("Retry-After"))
        return seconds if seconds is not None and seconds <= COACH_LIMIT_MAX_WAIT_S else None
    seconds = _seconds(headers.get("Retry-After")) if headers is not None else None
    if seconds is None:
        match = RETRY_IN_MESSAGE.search(str(body.get("message") or ""))
        seconds = float(match.group(1)) if match else DEFAULT_WAIT_S
    return seconds


def http_send(base: str, token: str, method: str, path: str, body: dict | None) -> tuple[int, object, object]:
    """One HTTP call: the status, the response headers and the decoded body."""
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            raw = resp.read()
            return resp.status, resp.headers, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as err:
        raw = err.read()
        try:
            return err.code, err.headers, json.loads(raw)
        except Exception:
            return err.code, err.headers, {"raw": raw.decode(errors="replace")[:300]}


class Client:
    """The coach API. Every call goes through _call, which paces the calls and waits out a 429."""

    def __init__(self, base: str, token: str, pace: float = 0.0, send=None, sleep=time.sleep,
                 clock=time.monotonic, log=print):
        self.base = base.rstrip("/")
        self.token = token
        self.pace = max(0.0, pace)
        self._send = send or (lambda method, path, body: http_send(self.base, self.token, method, path, body))
        self._sleep = sleep
        self._clock = clock
        self._log = log
        self._last_sent: float | None = None
        self.retries = 0
        self.retry_wait_s = 0.0

    def _paced_send(self, method: str, path: str, body: dict | None):
        if self.pace and self._last_sent is not None:
            gap = self._last_sent + self.pace - self._clock()
            if gap > 0:
                self._sleep(gap)
        self._last_sent = self._clock()
        return self._send(method, path, body)

    def _call(self, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None]:
        attempt = 0
        while True:
            status, headers, payload = self._paced_send(method, path, body)
            wait = retry_wait(status, headers, payload)
            if wait is None or attempt == MAX_RETRIES:
                return status, payload
            attempt += 1
            self.retries += 1
            self.retry_wait_s += wait
            code = payload.get("code") if isinstance(payload, dict) else None
            self._log(f"    429 {code or ''} on {method} {path}, waiting {wait:g}s (retry {attempt} of {MAX_RETRIES})",
                  flush=True)
            self._sleep(wait)

    def new_thread(self) -> str:
        status, body = self._call("POST", "/api/v1/coach/threads", {})
        if status != 201:
            hint = ""
            if status == 429 and isinstance(body, dict) and body.get("code") == "COACH_LIMIT":
                hint = (" (the coach's per-user limit is full: raise FITSYNC_COACH_TURNSPERHOUR on the target, "
                        "see scripts/README.md)")
            raise SystemExit(f"could not create a thread: {status} {body}{hint}")
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


def retry_self_test() -> int:
    """Checks the 429 handling against fake responses, without a stack and without really sleeping."""
    failures = 0

    def check(label: str, got, want) -> None:
        nonlocal failures
        if got != want:
            failures += 1
            print(f"FAIL retry {label}: expected {want!r}, got {got!r}")

    gateway = {"code": "RATE_LIMITED", "message": "Too many requests. Try again in 3 seconds."}
    check("Retry-After header", retry_wait(429, {"Retry-After": "7"}, gateway), 7.0)
    check("message seconds", retry_wait(429, {}, gateway), 3.0)
    check("fallback", retry_wait(429, {}, {"raw": "<html>Too Many Requests</html>"}), DEFAULT_WAIT_S)
    check("not a 429", retry_wait(500, {"Retry-After": "1"}, gateway), None)
    check("short coach limit", retry_wait(429, {}, {"code": "COACH_LIMIT", "retryAfterSeconds": 42}), 42.0)
    check("long coach limit", retry_wait(429, {"Retry-After": "1800"},
                                         {"code": "COACH_LIMIT", "retryAfterSeconds": 1800}), None)

    now = [0.0]
    slept: list[float] = []

    def sleep(seconds: float) -> None:
        slept.append(seconds)
        now[0] += seconds

    def fake(replies):
        return Client("http://fake", "token", send=lambda m, p, b: replies.pop(0) if len(replies) > 1 else replies[0],
                      sleep=sleep, clock=lambda: now[0], log=lambda line, **kw: None)

    # Two gateway refusals and then the thread: two retries, each waited out as the refusal asked.
    client = fake([(429, {"Retry-After": "1"}, gateway), (429, {}, gateway), (201, {}, {"threadId": "t-1"})])
    check("thread after retries", client.new_thread(), "t-1")
    check("retry count", client.retries, 2)
    check("waits", slept, [1.0, 3.0])

    # Pacing: two calls back to back are spaced by the pace, a call after a long gap is not delayed.
    slept.clear()
    client = fake([(200, {}, {})])
    client.pace = 0.6
    client._call("GET", "/a")
    client._call("GET", "/b")
    now[0] += 10
    client._call("GET", "/c")
    check("pace", [round(s, 3) for s in slept], [0.6])

    # A refusal that never clears gives up after MAX_RETRIES and hands the 429 back.
    slept.clear()
    client = fake([(429, {}, gateway)])
    check("gives up", client._call("GET", "/d")[0], 429)
    check("gives up retries", client.retries, MAX_RETRIES)

    # A coach limit that frees in an hour is not retried and fails as before, naming the setting to raise.
    client = fake([(429, {"Retry-After": "900"}, {"code": "COACH_LIMIT", "retryAfterSeconds": 900})])
    try:
        client.new_thread()
        check("long coach limit fails", "returned", "SystemExit")
    except SystemExit as exc:
        check("long coach limit message", "FITSYNC_COACH_TURNSPERHOUR" in str(exc), True)
    check("long coach limit retries", client.retries, 0)
    return failures


def with_card_period(context: dict) -> dict:
    """A card without dates is asked about the last 28 days, so the golden file never goes stale."""
    card = context.get("card")
    if not card or ("from" in card and "to" in card):
        return context
    today = dt.date.today()
    return {**context, "card": {**card, "from": (today - dt.timedelta(days=27)).isoformat(), "to": today.isoformat()}}


def contains_any(text: str, needles: list[str]) -> bool:
    lowered = text.lower()
    return any(re.search(n[len(REGEX_PREFIX):], text, re.IGNORECASE) if n.startswith(REGEX_PREFIX)
               else n.lower() in lowered for n in needles)


def run(args: argparse.Namespace) -> int:
    if args.now:
        seeded = dt.datetime.fromisoformat(args.now)
    elif args.today:
        seeded = dt.date.fromisoformat(args.today)
    else:
        seeded = dt.datetime.now(SGT).replace(tzinfo=None)
    golden = resolve_golden(json.loads(pathlib.Path(args.golden).read_text(encoding="utf-8")), seeded)
    if args.print_facts:
        print(json.dumps(golden["facts"], indent=2))
        return 0
    token = pathlib.Path(args.token_file).read_text(encoding="utf-8").strip()
    client = Client(args.base, token, pace=args.pace)
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

    print(f"\n429 retries: {client.retries} ({client.retry_wait_s:g}s waited)")
    report = render(golden, rows, draft_rows, before, after, tiers, started, args,
                    client.retries, client.retry_wait_s)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8")
    print(f"\nreport written to {out}")
    return 0


def rate(hits: int, total: int) -> str:
    return "n/a" if total == 0 else f"{hits}/{total} = {100.0 * hits / total:.0f}%"


def render(golden, rows, draft_rows, before, after, tiers, started, args, retries=0, retry_wait_s=0.0) -> str:
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
                 f"{len(rows)} chat questions, {len(draft_rows)} drafts · pace {args.pace:g} s · "
                 f"{retries} retries after a 429 ({retry_wait_s:g} s waited).")
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
                        help="the date the seed ran on, YYYY-MM-DD, taken as a run at 12:00")
    parser.add_argument("--now", default=None,
                        help="when the seed ran, Singapore time, YYYY-MM-DDTHH:MM (default: now in Singapore); "
                             "today's breakfast is in the seed only when this is 07:30 or later")
    parser.add_argument("--pace", type=float, default=0.0,
                        help="minimum seconds between requests (default 0); 0.6 keeps a cloud run under the "
                             "gateway's 120 requests a minute")
    parser.add_argument("--print-facts", action="store_true", help="print the expectations for the day and stop")
    parser.add_argument("--self-test", action="store_true", help="check the day arithmetic and the 429 handling, and stop")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    return run(args)


if __name__ == "__main__":
    sys.exit(main())
