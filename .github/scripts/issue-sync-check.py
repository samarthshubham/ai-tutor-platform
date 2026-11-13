"""Checks that a PR's roadmap issues are linked and up to date before it merges.

For every story the PR names (`Roadmap story: E1-S2`, comma-separated for more):
- the PR references the story's issue with `Closes #n` or `Part of #n`
- the issue is open, is a sub-issue of its epic and has the epic's milestone
- the issue's AC lines match the roadmap text (ticks ignored), so the issue isn't stale
- a closing PR leaves no unticked AC or task boxes in the issue
And: no roadmap issue is referenced without its story, and no epic is closed by a PR.

Ticked boxes are claims; the roadmap-review agent checks them against the diff.

Env: REPO, GH_TOKEN, PR_BODY or PR_BODY_FILE. ROADMAP overrides the roadmap path.
Exit 1 on any problem, listing all of them.
"""

import json
import os
import re
import subprocess
import sys

REPO = os.environ.get("REPO", "")
ROADMAP = os.environ.get("ROADMAP", "docs/roadmap/ai-tutor.md")
STORY_ID = r"E\d+-S\d+"
CHECKBOX = re.compile(r"^\s*- \[( |x|X)\] (.+?)\s*$")


def gh_issue(number, suffix=""):
    result = subprocess.run(
        ["gh", "api", f"repos/{REPO}/issues/{number}{suffix}"], capture_output=True, text=True
    )
    if result.returncode != 0:
        return None
    return json.loads(result.stdout)


def pr_body():
    if os.environ.get("PR_BODY_FILE"):
        return open(os.environ["PR_BODY_FILE"]).read()
    return os.environ.get("PR_BODY", "")


def roadmap_index(text):
    tracking = text.split("## 8. GitHub tracking", 1)[1]
    issue_of, epic_of = {}, {}
    for epic, epic_issue, stories in re.findall(r"^\| (E\d+) \| #(\d+) \| (.+?) \|$", tracking, re.M):
        issue_of[epic] = int(epic_issue)
        for story, number in re.findall(r"(S\d+) #(\d+)", stories):
            issue_of[f"{epic}-{story}"] = int(number)
            epic_of[f"{epic}-{story}"] = epic
    return issue_of, epic_of


def section_items(text, heading):
    lines, inside = [], False
    for line in text.splitlines():
        if line.startswith("- **"):
            inside = line.startswith(f"- **{heading}**")
            continue
        if inside:
            match = CHECKBOX.match(line)
            if match:
                lines.append((match.group(1) != " ", match.group(2)))
            elif line.strip() and not line.startswith("  "):
                inside = False
    return lines


def roadmap_story(text, story):
    match = re.search(rf"^#### {story} · .*?(?=^#### |^### |^## |\Z)", text, re.M | re.S)
    return match.group(0) if match else ""


def main():
    body = pr_body()
    roadmap = open(ROADMAP).read()
    issue_of, epic_of = roadmap_index(roadmap)
    story_of_issue = {number: story for story, number in issue_of.items()}

    line = re.search(r"Roadmap story:\s*(.*)", body)
    stories = re.findall(STORY_ID, line.group(1)) if line else []
    closes = {int(n) for n in re.findall(r"\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?) #(\d+)", body, re.I)}
    part_of = {int(n) for n in re.findall(r"\bpart of #(\d+)", body, re.I)}

    problems = []
    for number in sorted(closes | part_of):
        story = story_of_issue.get(number)
        if story and "-" not in story:
            problems.append(f"#{number} is the {story} epic. Reference its story issues; epics close by themselves.")
        elif story and story not in stories:
            problems.append(f"#{number} is roadmap story {story}, but the PR's 'Roadmap story:' line doesn't name it.")

    for story in stories:
        number = issue_of.get(story)
        if number is None:
            problems.append(f"{story} has no issue in the roadmap's GitHub tracking table.")
            continue
        closing = number in closes
        if not closing and number not in part_of:
            problems.append(f"{story}: add 'Closes #{number}' (story finished) or 'Part of #{number}' (partial) to the PR description.")
            continue

        issue = gh_issue(number)
        if issue is None:
            problems.append(f"{story}: issue #{number} could not be read.")
            continue
        if issue["state"] != "open":
            problems.append(f"{story}: issue #{number} is already closed. Reopen it or reference the right issue.")

        epic = epic_of[story]
        parent = gh_issue(number, "/parent")
        if not parent or parent.get("number") != issue_of[epic]:
            problems.append(f"{story}: issue #{number} is not a sub-issue of epic #{issue_of[epic]} ({epic}).")
        if not issue.get("milestone") or not issue["milestone"]["title"].startswith(f"M{epic[1:]} "):
            problems.append(f"{story}: issue #{number} should have milestone M{epic[1:]}.")

        issue_body = issue.get("body") or ""
        planned = [text for _, text in section_items(roadmap_story(roadmap, story), "AC")]
        tracked = [text for _, text in section_items(issue_body, "AC")]
        if planned != tracked:
            problems.append(
                f"{story}: issue #{number}'s AC differ from the roadmap. Copy the story's AC from {ROADMAP} into the issue."
            )

        if closing:
            open_boxes = [text for heading in ("AC", "Tasks") for done, text in section_items(issue_body, heading) if not done]
            if open_boxes:
                listed = "; ".join(open_boxes)
                problems.append(
                    f"{story}: the PR closes #{number}, but these boxes are unticked: {listed}. "
                    f"Tick what this PR delivers, or use 'Part of #{number}' and leave the rest open."
                )

    if not stories and not (closes | part_of) & set(story_of_issue):
        print("No roadmap stories referenced; nothing to check.")
        return 0
    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        print("\nUpdate the issue or PR description, then re-run this check (editing the issue re-runs it).")
        return 1
    print(f"Issues for {', '.join(stories)} are linked and up to date.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
