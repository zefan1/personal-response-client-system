#!/usr/bin/env python3
import json
import os
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
DESKTOP_REPORT = ROOT / ".tools" / "desktop" / "package_verify_report.json"
LIVE_REPORT = REPORT_DIR / "real_external_live.json"
OUTPUT = REPORT_DIR / "production_blockers.json"

LIVE_ENV_KEYS = [
    "PDA_LIVE_SKILL_BASE_URL",
    "PDA_LIVE_SKILL_API_KEY",
    "PDA_LIVE_IMAGE_BASE_URL",
    "PDA_LIVE_IMAGE_API_KEY",
    "PDA_LIVE_TABLE_BASE_URL",
    "PDA_LIVE_TABLE_API_KEY",
]


def read_json(path: Path) -> dict | None:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    blockers: list[dict[str, object]] = []
    live_env_present = {key: bool(os.environ.get(key, "").strip()) for key in LIVE_ENV_KEYS}
    missing_live_env = [key for key, present in live_env_present.items() if not present]
    live_report = read_json(LIVE_REPORT)
    package_report = read_json(DESKTOP_REPORT)

    live_passed = bool(live_report and live_report.get("passed") is True)
    if not live_passed:
        blockers.append({
            "id": "LIVE_EXTERNAL_PROVIDER_ACCEPTANCE",
            "severity": "P0",
            "status": "blocked",
            "reason": "Live Skill/image/WeCom provider acceptance has not passed.",
            "missingEnv": missing_live_env,
            "latestReport": str(LIVE_REPORT) if LIVE_REPORT.exists() else None,
            "latestFatal": live_report.get("fatal") if live_report else "missing live acceptance report",
        })

    signed = bool(package_report and package_report.get("signed") is True)
    signing_configured = bool(
        package_report
        and isinstance(package_report.get("signingConfiguration"), dict)
        and package_report["signingConfiguration"].get("certificateConfigured")
    )
    if not signed:
        blockers.append({
            "id": "SIGNED_RELEASE_PACKAGE",
            "severity": "P1",
            "status": "blocked",
            "reason": "Production release package is not signed by a configured certificate.",
            "signed": signed,
            "signingConfigured": signing_configured,
            "latestReport": str(DESKTOP_REPORT) if DESKTOP_REPORT.exists() else None,
        })

    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "productionReady": len(blockers) == 0,
        "blockerCount": len(blockers),
        "blockers": blockers,
        "liveEnvPresent": live_env_present,
        "liveAcceptancePassed": live_passed,
        "signedPackagePassed": signed,
        "signingConfigured": signing_configured,
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"production_blockers_report={OUTPUT}")
    print(f"productionReady={str(report['productionReady']).lower()} blockers={len(blockers)}")
    for blocker in blockers:
        print(f"blocker={blocker['severity']}:{blocker['id']} reason={blocker['reason']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
