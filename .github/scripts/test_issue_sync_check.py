import contextlib
import importlib.util
import io
import os
import pathlib
import unittest
from unittest import mock

SCRIPT = pathlib.Path(__file__).with_name("issue-sync-check.py")
ROADMAP = str(pathlib.Path(__file__).with_name("testdata") / "roadmap.md")

spec = importlib.util.spec_from_file_location("issue_sync_check", SCRIPT)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)

STORY_BODY = """Roadmap story **E1-S2**.

- **AC**
  - [{0}] `GET /users?role=STUDENT&q=sa` returns a page of matching users for an admin
  - [{0}] Creating a guardian link returns `201`; the same pair again returns `409`
  - [{0}] Deleting a link returns `204`, and `404` the second time
- **Tests:** unit
- **Tasks**
  - [{0}] Migration and entities
"""


def issue(ticked, state="open", milestone="M1 Identity"):
    return {
        "state": state,
        "milestone": {"title": milestone},
        "body": STORY_BODY.format("x" if ticked else " "),
    }


def run(pr_body, issues=None):
    def fake_issue(number, suffix=""):
        if suffix == "/parent":
            return {"number": 66}
        return (issues or {}).get(number)

    out = io.StringIO()
    with mock.patch.dict(os.environ, {"PR_BODY": pr_body, "PR_BODY_FILE": ""}), \
         mock.patch.object(check, "ROADMAP", ROADMAP), \
         mock.patch.object(check, "gh_issue", side_effect=fake_issue), \
         contextlib.redirect_stdout(out):
        return check.main(), out.getvalue()


class RoadmapParsingTest(unittest.TestCase):
    def test_tracking_table_maps_stories_and_epics_to_issues(self):
        issue_of, epic_of = check.roadmap_index(open(ROADMAP).read())
        self.assertEqual(issue_of["E1"], 66)
        self.assertEqual(issue_of["E1-S2"], 68)
        self.assertEqual(epic_of["E1-S2"], "E1")

    def test_section_items_reads_only_the_named_section(self):
        items = check.section_items(STORY_BODY.format("x"), "Tasks")
        self.assertEqual(items, [(True, "Migration and entities")])


class MainTest(unittest.TestCase):
    def test_pr_outside_the_roadmap_is_skipped(self):
        code, out = run("Fix typo. Closes #5")
        self.assertEqual(code, 0)
        self.assertIn("No roadmap stories referenced", out)

    def test_story_without_issue_reference_fails(self):
        code, out = run("Roadmap story: E1-S2")
        self.assertEqual(code, 1)
        self.assertIn("add 'Closes #68'", out)

    def test_closing_with_unticked_boxes_fails(self):
        code, out = run("Roadmap story: E1-S2\nCloses #68", {68: issue(ticked=False)})
        self.assertEqual(code, 1)
        self.assertIn("unticked", out)

    def test_closing_with_everything_ticked_passes(self):
        code, _ = run("Roadmap story: E1-S2\nCloses #68", {68: issue(ticked=True)})
        self.assertEqual(code, 0)

    def test_partial_pr_may_leave_boxes_unticked(self):
        code, _ = run("Roadmap story: E1-S2\nPart of #68", {68: issue(ticked=False)})
        self.assertEqual(code, 0)

    def test_closing_an_epic_fails(self):
        code, out = run("Roadmap story: E1-S2\nPart of #68\nCloses #66", {68: issue(ticked=False)})
        self.assertEqual(code, 1)
        self.assertIn("epic", out)

    def test_roadmap_issue_not_named_as_story_fails(self):
        code, out = run("Roadmap story: E1-S2\nPart of #68\nPart of #67", {68: issue(ticked=False)})
        self.assertEqual(code, 1)
        self.assertIn("#67 is roadmap story E1-S1", out)

    def test_issue_with_stale_ac_fails(self):
        stale = issue(ticked=False)
        stale["body"] = stale["body"].replace("`404` the second time", "`410` the second time")
        code, out = run("Roadmap story: E1-S2\nPart of #68", {68: stale})
        self.assertEqual(code, 1)
        self.assertIn("differ from the roadmap", out)

    def test_wrong_milestone_fails(self):
        code, out = run("Roadmap story: E1-S2\nPart of #68", {68: issue(ticked=False, milestone="M2 Courses and CMS")})
        self.assertEqual(code, 1)
        self.assertIn("milestone M1", out)


if __name__ == "__main__":
    unittest.main()
