#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULES_SRC = ROOT / "desktop" / "src" / "renderer" / "modules"
REPORT_DIR = ROOT / ".tools" / "coverage"


def component_files() -> list[Path]:
    return sorted(MODULES_SRC.rglob("*.vue"))


def main() -> int:
    components = component_files()
    missing: list[dict[str, str]] = []
    covered: list[dict[str, str]] = []

    for component in components:
      expected = component.with_name(f"{component.stem}.test.ts")
      item = {
          "component": str(component.relative_to(ROOT)),
          "expectedTest": str(expected.relative_to(ROOT)),
      }
      if expected.exists():
          covered.append(item)
      else:
          missing.append(item)

    report = {
        "componentCount": len(components),
        "coveredComponentCount": len(covered),
        "missingComponentCount": len(missing),
        "components": covered + missing,
        "missing": missing,
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report_path = REPORT_DIR / "desktop_component_test_coverage.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"desktop_component_test_coverage_report={report_path}")
    print(
        f"components={report['componentCount']} covered={report['coveredComponentCount']} "
        f"missing={report['missingComponentCount']}"
    )
    if missing:
        for item in missing:
            print(f"missing desktop component test coverage: {item}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
