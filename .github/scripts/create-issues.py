"""Creates the GitHub tracking for the roadmap: labels, milestones, one issue per epic,
one sub-issue per story with its "blocked by" links, and the tracking table in roadmap §8.

Safe to run again: existing labels, milestones and issues (matched by their "[E1-S2]" title
prefix) are reused, and story issue bodies are left alone so ticked boxes survive.

Usage: REPO=owner/name python3 -I .github/scripts/create-issues.py [--dry-run]
Needs the gh CLI, authenticated with the repo scope.
"""

import json
import os
import re
import subprocess
import sys

REPO = os.environ.get("REPO", "")
ROADMAP = os.environ.get("ROADMAP", "docs/roadmap/ai-tutor.md")
DRY_RUN = "--dry-run" in sys.argv

LABELS = {
    "epic": ("3e4b9e", "Parent issue grouping stories"),
    "backend": ("bfd4f2", "Spring Boot API"),
    "frontend": ("bfd4f2", "Angular app"),
    "ai": ("5319e7", "Model calls, prompts and AI flows"),
    "rag": ("c5def5", "Embeddings, vector store and retrieval"),
    "auth": ("c5def5", "Sign-in, roles and user links"),
    "security": ("b60205", "Authentication, authorization and data exposure"),
    "testing": ("0e8a16", "Tests, seed data and coverage"),
    "infra": ("1d76db", "Docker, local stack and scripts"),
    "ci": ("5319e7", "CI and automation"),
    "observability": ("fbca04", "Metrics, logs and dashboards"),
    "documentation": ("0075ca", "Docs and study material"),
    "size:S": ("ededed", "Up to 1 day"),
    "size:M": ("ededed", "2-3 days"),
    "size:L": ("ededed", "4-5 days"),
}

DEFINITION_OF_DONE = """---

**Definition of done**
- CI is green: `backend`, `frontend`, `coverage-gate`, `issue-sync`, `roadmap-review`
- Every review finding is fixed or answered
- Docs and `CLAUDE.md` are updated when a command, port, module or convention changed
- Every AC and task box above is ticked

Before starting, read `CLAUDE.md` and the design docs named under Context and Contract."""


def gh(*args, payload=None):
    result = subprocess.run(
        ["gh", *args], capture_output=True, text=True, input=json.dumps(payload) if payload else None
    )
    if result.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args)}\n{result.stderr.strip()}")
    return result.stdout


def api(method, path, payload=None):
    args = ["api", "-X", method, f"repos/{REPO}/{path}"]
    if payload is not None:
        args += ["--input", "-"]
    out = gh(*args, payload=payload)
    return json.loads(out) if out.strip() else None


def parse(text):
    backlog = text.split("## 7. Backlog", 1)[1].split("## 8. GitHub tracking", 1)[0]
    epics = []
    for block in re.split(r"^(?=### E\d+ · )", backlog, flags=re.M)[1:]:
        head = re.match(r"### (E\d+) · (.+)\n\n(.+?)\n", block)
        epic = {"id": head.group(1), "title": head.group(2).strip(), "intro": head.group(3).strip(), "stories": []}
        for story_block in re.split(r"^(?=#### E\d+-S\d+ · )", block, flags=re.M)[1:]:
            lines = story_block.strip("\n").split("\n")
            title = re.match(r"#### (E\d+-S\d+) · (.+)", lines[0])
            meta = re.match(r"Labels: (.+) · Size: (\w) · Blocked by: (.+)", lines[1])
            if not title or not meta:
                raise ValueError(f"Cannot parse the first two lines of: {lines[0]}")
            blockers = [] if meta.group(3).strip() == "none" else [b.strip() for b in meta.group(3).split(",")]
            epic["stories"].append({
                "id": title.group(1),
                "title": title.group(2).strip(),
                "labels": re.findall(r"`([^`]+)`", meta.group(1)) + [f"size:{meta.group(2)}"],
                "size": meta.group(2),
                "blocked_by": blockers,
                "body": "\n".join(lines[2:]).strip(),
            })
        epics.append(epic)
    return epics


def story_body(epic, story):
    blockers = ", ".join(story["blocked_by"]) or "nothing"
    return (
        f"Roadmap story **{story['id']}** in epic {epic['id']} ({epic['title']}). "
        f"Source of truth: [{ROADMAP}](https://github.com/{REPO}/blob/main/{ROADMAP}). "
        f"If this issue and the roadmap differ, the roadmap wins.\n\n"
        f"Size: **{story['size']}** · Blocked by: {blockers}\n\n"
        f"{story['body']}\n\n"
        f"PR description must include `Roadmap story: {story['id']}`.\n\n"
        f"{DEFINITION_OF_DONE}\n"
    )


def epic_body(epic):
    rows = "\n".join(
        f"| {s['id']} | {s['title']} | {s['size']} | {', '.join(s['blocked_by']) or 'none'} |" for s in epic["stories"]
    )
    return (
        f"Roadmap epic **{epic['id']}**. Source of truth: "
        f"[{ROADMAP}](https://github.com/{REPO}/blob/main/{ROADMAP}).\n\n"
        f"{epic['intro']}\n\n"
        f"| Story | Title | Size | Blocked by |\n|---|---|---|---|\n{rows}\n\n"
        f"The stories are this issue's sub-issues. It closes by itself when the last one closes; "
        f"never close it from a pull request.\n"
    )


def existing_issues():
    found = {}
    page = 1
    while True:
        batch = api("GET", f"issues?state=all&per_page=100&page={page}")
        for issue in batch:
            match = re.match(r"\[(E\d+(?:-S\d+)?)\] ", issue["title"])
            if match and "pull_request" not in issue:
                found[match.group(1)] = issue
        if len(batch) < 100:
            return found
        page += 1


def main():
    if not REPO:
        sys.exit("Set REPO=owner/name")
    text = open(ROADMAP).read()
    epics = parse(text)
    stories = [s for e in epics for s in e["stories"]]
    known = {s["id"] for s in stories}
    for story in stories:
        for blocker in story["blocked_by"]:
            if blocker not in known:
                sys.exit(f"{story['id']} is blocked by unknown story {blocker}")
        for label in story["labels"]:
            if label not in LABELS:
                sys.exit(f"{story['id']} uses unknown label {label}")
    print(f"{len(epics)} epics, {len(stories)} stories parsed.")
    if DRY_RUN:
        print(story_body(epics[0], epics[0]["stories"][0]))
        return

    for name, (color, description) in LABELS.items():
        gh("label", "create", name, "-R", REPO, "--color", color, "--description", description, "--force")

    milestones = {m["title"]: m["number"] for m in api("GET", "milestones?state=all&per_page=100")}
    for epic in epics:
        title = f"M{epic['id'][1:]} {epic['title']}"
        if title not in milestones:
            milestones[title] = api("POST", "milestones", {"title": title})["number"]
        epic["milestone"] = milestones[title]

    issues = existing_issues()

    def ensure(key, title, body, labels, milestone):
        if key in issues:
            return issues[key]
        created = api("POST", "issues", {"title": title, "body": body, "labels": labels, "milestone": milestone})
        issues[key] = created
        print(f"created #{created['number']} {title}")
        return created

    for epic in epics:
        parent = ensure(epic["id"], f"[{epic['id']}] {epic['title']}", epic_body(epic), ["epic"], epic["milestone"])
        children = {c["number"] for c in api("GET", f"issues/{parent['number']}/sub_issues?per_page=100")}
        for story in epic["stories"]:
            child = ensure(
                story["id"], f"[{story['id']}] {story['title']}", story_body(epic, story), story["labels"], epic["milestone"]
            )
            if child["number"] not in children:
                api("POST", f"issues/{parent['number']}/sub_issues", {"sub_issue_id": child["id"]})

    failed_links = []
    for story in stories:
        number = issues[story["id"]]["number"]
        try:
            linked = {b["number"] for b in api("GET", f"issues/{number}/dependencies/blocked_by?per_page=100")}
        except RuntimeError as error:
            failed_links.append(f"{story['id']}: {error}")
            continue
        for blocker in story["blocked_by"]:
            if issues[blocker]["number"] in linked:
                continue
            try:
                api("POST", f"issues/{number}/dependencies/blocked_by", {"issue_id": issues[blocker]["id"]})
            except RuntimeError as error:
                failed_links.append(f"{story['id']} <- {blocker}: {error}")

    rows = "\n".join(
        f"| {e['id']} | #{issues[e['id']]['number']} | "
        + ", ".join(f"{s['id'].split('-')[1]} #{issues[s['id']]['number']}" for s in e["stories"])
        + " |"
        for e in epics
    )
    head, tracking = text.split("## 8. GitHub tracking", 1)
    table_start = tracking.index("| Epic | Issue |")
    table_head = "| Epic | Issue | Stories (sub-issues) |\n|---|---|---|\n"
    tracking = tracking[:table_start] + table_head + rows + "\n"
    open(ROADMAP, "w").write(head + "## 8. GitHub tracking" + tracking)
    print(f"Tracking table written to {ROADMAP}.")

    if failed_links:
        print("\nCould not set these 'blocked by' links:")
        for line in failed_links:
            print(f"  {line}")
        sys.exit(1)


if __name__ == "__main__":
    main()
