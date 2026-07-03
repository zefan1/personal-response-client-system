#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN_SRC = ROOT / "src" / "main" / "java"
TEST_SRC = ROOT / "src" / "test" / "java"
REPORT_DIR = ROOT / ".tools" / "coverage"


AGGREGATE_TEST_COVERAGE = {
    "WebCoreControllerTest": [
        "AuthController",
        "ConfigController",
        "HealthController",
        "HelpController",
        "QuickSearchController",
    ],
}


def controller_classes() -> dict[str, str]:
    controllers: dict[str, str] = {}
    for path in MAIN_SRC.rglob("*Controller.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "@RestController" not in text and "@Controller" not in text:
            continue
        match = re.search(r"\bclass\s+([A-Za-z0-9_]+Controller)\b", text)
        if match:
            controllers[match.group(1)] = str(path.relative_to(ROOT))
    return controllers


def test_classes() -> dict[str, str]:
    tests: dict[str, str] = {}
    for path in TEST_SRC.rglob("*ControllerTest.java"):
        match = re.search(r"\bclass\s+([A-Za-z0-9_]+ControllerTest)\b", path.read_text(encoding="utf-8", errors="replace"))
        if match:
            tests[match.group(1)] = str(path.relative_to(ROOT))
    return tests


def main() -> int:
    controllers = controller_classes()
    tests = test_classes()
    covered: dict[str, str] = {}
    for controller in controllers:
        direct_test = f"{controller}Test"
        if direct_test in tests:
            covered[controller] = direct_test
    for test_name, controller_names in AGGREGATE_TEST_COVERAGE.items():
        if test_name not in tests:
            continue
        for controller in controller_names:
            if controller in controllers:
                covered[controller] = test_name

    missing = sorted(set(controllers) - set(covered))
    report = {
        "controllerCount": len(controllers),
        "testClassCount": len(tests),
        "coveredControllerCount": len(covered),
        "missingControllerCount": len(missing),
        "controllers": [
            {
                "controller": controller,
                "source": controllers[controller],
                "coveredBy": covered.get(controller),
            }
            for controller in sorted(controllers)
        ],
        "missing": [
            {
                "controller": controller,
                "source": controllers[controller],
            }
            for controller in missing
        ],
        "aggregateTestCoverage": AGGREGATE_TEST_COVERAGE,
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report_path = REPORT_DIR / "controller_test_coverage.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"controller_test_coverage_report={report_path}")
    print(
        f"controllers={report['controllerCount']} covered={report['coveredControllerCount']} "
        f"missing={report['missingControllerCount']} test_classes={report['testClassCount']}"
    )
    if missing:
        for item in missing:
            print(f"missing controller test coverage: {item}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
