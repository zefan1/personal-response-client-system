#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
QUALITY_REPORT = REPORT_DIR / "backend_api_acceptance_quality.json"

MIN_TOTAL = 167
MIN_COVERAGE_COUNTS = {
    "representative": 90,
    "read": 34,
    "update": 2,
    "download": 1,
    "permission": 6,
    "invalid": 22,
    "conflict": 8,
    "create": 4,
}


def latest_backend_report() -> Path | None:
    reports = sorted(
        (
            path for path in REPORT_DIR.glob("backend_api_acceptance_*.json")
            if path.name != QUALITY_REPORT.name
        ),
        key=lambda path: path.stat().st_mtime,
    )
    return reports[-1] if reports else None


def main() -> int:
    report_path = latest_backend_report()
    failures: list[str] = []
    source_report: dict | None = None

    if report_path is None:
        failures.append("no backend_api_acceptance_*.json report found")
    else:
        source_report = json.loads(report_path.read_text(encoding="utf-8"))
        total = len(source_report.get("results", []))
        failed = int(source_report.get("failed", 0))
        fatal = source_report.get("fatal")
        coverage = source_report.get("coverageCounts", {})
        if fatal:
            failures.append(f"backend acceptance has fatal error: {fatal}")
        if failed != 0:
            failures.append(f"backend acceptance has failed calls: {failed}")
        if total < MIN_TOTAL:
            failures.append(f"backend acceptance total too low: {total} < {MIN_TOTAL}")
        for key, minimum in MIN_COVERAGE_COUNTS.items():
            actual = int(coverage.get(key, 0))
            if actual < minimum:
                failures.append(f"coverage {key} too low: {actual} < {minimum}")

    quality = {
        "sourceReport": str(report_path) if report_path else None,
        "passed": not failures,
        "minTotal": MIN_TOTAL,
        "minCoverageCounts": MIN_COVERAGE_COUNTS,
        "actualTotal": len(source_report.get("results", [])) if source_report else 0,
        "actualFailed": int(source_report.get("failed", 0)) if source_report else None,
        "actualCoverageCounts": source_report.get("coverageCounts", {}) if source_report else {},
        "failures": failures,
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    QUALITY_REPORT.write_text(json.dumps(quality, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"backend_api_acceptance_quality_report={QUALITY_REPORT}")
    print(
        f"passed={str(quality['passed']).lower()} total={quality['actualTotal']} "
        f"failed={quality['actualFailed']} coverage={quality['actualCoverageCounts']}"
    )
    if failures:
        for failure in failures:
            print(f"backend API acceptance quality failure: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
