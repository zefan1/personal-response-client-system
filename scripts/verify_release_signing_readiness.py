#!/usr/bin/env python3
import argparse
import json
import os
import platform
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "desktop"
OUTPUT = REPORT_DIR / "release_signing_readiness.json"


def present(name: str) -> bool:
    return bool(os.environ.get(name, "").strip())


def windows_readiness() -> dict[str, object]:
    has_pfx = present("WINDOWS_CERTIFICATE_FILE") and present("WINDOWS_CERTIFICATE_PASSWORD")
    has_custom_params = present("WINDOWS_SIGN_WITH_PARAMS")
    has_hook = present("WINDOWS_SIGN_HOOK_MODULE_PATH")
    return {
        "target": "win32",
        "hostSupported": platform.system().lower() == "windows",
        "ready": has_pfx or has_custom_params or has_hook,
        "acceptedStrategies": [
            "WINDOWS_CERTIFICATE_FILE + WINDOWS_CERTIFICATE_PASSWORD",
            "WINDOWS_SIGN_WITH_PARAMS",
            "WINDOWS_SIGN_HOOK_MODULE_PATH",
        ],
        "presentKeys": [
            key
            for key in [
                "WINDOWS_CERTIFICATE_FILE",
                "WINDOWS_CERTIFICATE_PASSWORD",
                "WINDOWS_SIGNTOOL_PATH",
                "WINDOWS_SIGN_WITH_PARAMS",
                "WINDOWS_SIGN_HOOK_MODULE_PATH",
                "WINDOWS_TIMESTAMP_SERVER",
                "WINDOWS_SIGN_DESCRIPTION",
                "WINDOWS_SIGN_WEBSITE",
            ]
            if present(key)
        ],
    }


def mac_readiness() -> dict[str, object]:
    has_identity = present("PDA_MAC_CODESIGN_IDENTITY") or present("MAC_CODESIGN_IDENTITY")
    has_password_notary = present("APPLE_ID") and present("APPLE_APP_SPECIFIC_PASSWORD") and (
        present("APPLE_TEAM_ID") or present("TEAM_ID")
    )
    has_api_key_notary = present("APPLE_API_KEY") and present("APPLE_API_KEY_ID") and present("APPLE_API_ISSUER")
    has_keychain_notary = present("APPLE_KEYCHAIN_PROFILE")
    return {
        "target": "darwin",
        "hostSupported": platform.system().lower() == "darwin",
        "ready": has_identity,
        "notarizationReady": has_password_notary or has_api_key_notary or has_keychain_notary,
        "acceptedStrategies": [
            "PDA_MAC_CODESIGN_IDENTITY or MAC_CODESIGN_IDENTITY",
            "APPLE_ID + APPLE_APP_SPECIFIC_PASSWORD + APPLE_TEAM_ID",
            "APPLE_API_KEY + APPLE_API_KEY_ID + APPLE_API_ISSUER",
            "APPLE_KEYCHAIN_PROFILE",
        ],
        "presentKeys": [
            key
            for key in [
                "PDA_MAC_CODESIGN_IDENTITY",
                "MAC_CODESIGN_IDENTITY",
                "PDA_MAC_ENTITLEMENTS",
                "PDA_MAC_ENTITLEMENTS_INHERIT",
                "APPLE_ID",
                "APPLE_APP_SPECIFIC_PASSWORD",
                "APPLE_TEAM_ID",
                "TEAM_ID",
                "APPLE_API_KEY",
                "APPLE_API_KEY_ID",
                "APPLE_API_ISSUER",
                "APPLE_KEYCHAIN_PROFILE",
            ]
            if present(key)
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-ready", action="store_true", help="return non-zero when the selected target is not signing-ready")
    parser.add_argument("--target", default=os.environ.get("PDA_PACKAGE_PLATFORM", "win32"), choices=["win32", "darwin"])
    args = parser.parse_args()

    target_report = windows_readiness() if args.target == "win32" else mac_readiness()
    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "target": args.target,
        "ready": bool(target_report["ready"] and target_report["hostSupported"]),
        "targetReport": target_report,
        "packageCommand": "cd desktop && npm run package:verify:signed",
        "notes": [
            "This script checks release signing inputs without exposing secret values.",
            "The package command still verifies the produced executable signature before production release.",
        ],
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"release_signing_readiness_report={OUTPUT}")
    print(
        "releaseSigningReady="
        + str(report["ready"]).lower()
        + f" target={args.target} hostSupported={str(target_report['hostSupported']).lower()}"
    )
    if args.require_ready and not report["ready"]:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
