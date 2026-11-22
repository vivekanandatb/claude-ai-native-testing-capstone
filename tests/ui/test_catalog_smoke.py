"""Deterministic UI smoke tests for the catalogue frontend (starter_code/frontend).

Written for: capstone testers running the CI UI smoke job.

Determinism:
- /api/courses is answered by the test at the browser network layer, so no backend,
  database, or live data is needed (the backend cannot start in CI; see docs/ci-cd.md).
- Waits use web-first assertions and observable requests. No fixed sleeps.
- Only synthetic course data is used.

Locators come from the rendered components: the level filter is a role=group named
"Filter by level" with native buttons; course titles are h2 headings.

Run locally (frontend must be serving on the base URL):
    CATALOG_BASE_URL=http://localhost:5173 python3 -m pytest
"""
import json
import os

from playwright.sync_api import Page, Route, expect

BASE_URL = os.environ.get("CATALOG_BASE_URL", "http://localhost:5173")
API_PATTERN = "**/api/courses**"

# Synthetic fixtures only. Shape matches what CourseCard renders: code, title, level, credits.
STUB_COURSES = [
    {"id": 1, "code": "SYN1001", "title": "Synthetic Foundation Course", "level": "FOUNDATION", "credits": 5},
    {"id": 2, "code": "SYN2001", "title": "Synthetic Advanced Course", "level": "ADVANCED", "credits": 10},
]


def stub_courses(page: Page, status: int = 200) -> None:
    """Answer every catalogue API request in the browser without touching a backend."""

    def handler(route: Route) -> None:
        if status == 200:
            route.fulfill(status=200, content_type="application/json", body=json.dumps(STUB_COURSES))
        else:
            route.fulfill(status=status, content_type="text/plain", body="synthetic failure")

    page.route(API_PATTERN, handler)


def test_heading_and_level_filter_are_visible(page: Page) -> None:
    stub_courses(page)
    page.goto(BASE_URL + "/")

    expect(page.get_by_role("heading", name="Course catalogue", level=1)).to_be_visible()
    group = page.get_by_role("group", name="Filter by level")
    expect(group).to_be_visible()
    for label in ["All levels", "Foundation", "Intermediate", "Advanced"]:
        expect(group.get_by_role("button", name=label, exact=True)).to_be_visible()


def test_stubbed_courses_are_listed(page: Page) -> None:
    stub_courses(page)
    page.goto(BASE_URL + "/")

    expect(page.get_by_role("heading", name="Synthetic Foundation Course", level=2)).to_be_visible()
    expect(page.get_by_role("heading", name="Synthetic Advanced Course", level=2)).to_be_visible()


def test_api_failure_shows_error_state(page: Page) -> None:
    stub_courses(page, status=500)
    page.goto(BASE_URL + "/")

    expect(page.get_by_text("Catalogue request failed with status 500")).to_be_visible()


def test_level_filter_requests_matching_level(page: Page) -> None:
    stub_courses(page)
    page.goto(BASE_URL + "/")
    # Wait for the initial, unfiltered load to finish before changing the filter.
    expect(page.get_by_role("heading", name="Synthetic Advanced Course", level=2)).to_be_visible()

    group = page.get_by_role("group", name="Filter by level")
    with page.expect_request(lambda request: "level=ADVANCED" in request.url) as captured:
        group.get_by_role("button", name="Advanced", exact=True).click()

    assert "level=ADVANCED" in captured.value.url
