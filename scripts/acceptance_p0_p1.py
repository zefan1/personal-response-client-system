#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
DEFAULT_BACKEND_URL = os.environ.get("PDA_BASE_URL", "http://172.19.250.154:8080")


def ps_command(command: str, cwd: Path = ROOT, env: dict[str, str] | None = None, timeout: int = 300):
    started = time.time()
    completed = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        cwd=cwd,
        env={**os.environ, **(env or {})},
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout,
    )
    return {
        "command": command,
        "cwd": str(cwd),
        "exitCode": completed.returncode,
        "durationMs": int((time.time() - started) * 1000),
        "outputTail": completed.stdout[-4000:],
    }


def wsl_command(command: str, timeout: int = 600):
    return ps_command(f"wsl bash -lc {json.dumps(command)}", timeout=timeout)


def wsl_path(path: Path) -> str:
    text = str(path).replace("\\", "/")
    if len(text) >= 2 and text[1] == ":":
        return f"/mnt/{text[0].lower()}{text[2:]}"
    return text


def run_step(name: str, command: str, cwd: Path = ROOT, env: dict[str, str] | None = None, timeout: int = 300, expect_failure: bool = False):
    result = ps_command(command, cwd=cwd, env=env, timeout=timeout)
    result["name"] = name
    result["expectFailure"] = expect_failure
    result["ok"] = result["exitCode"] != 0 if expect_failure else result["exitCode"] == 0
    return result


def run_wsl_step(name: str, command: str, timeout: int = 600):
    result = wsl_command(command, timeout=timeout)
    result["name"] = name
    result["expectFailure"] = False
    result["ok"] = result["exitCode"] == 0
    return result


def backend_available(url: str) -> bool:
    result = ps_command(
        f"try {{ (Invoke-WebRequest -UseBasicParsing -Uri '{url.rstrip('/')}/api/v1/auth/config' -TimeoutSec 5).StatusCode }} catch {{ exit 1 }}",
        timeout=15,
    )
    return result["exitCode"] == 0 and "200" in result["outputTail"]


def write_report(results: list[dict[str, object]], skipped: list[dict[str, str]], fatal: str | None = None):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "passed": fatal is None and all(item["ok"] for item in results),
        "fatal": fatal,
        "results": results,
        "skipped": skipped,
    }
    path = REPORT_DIR / "p0_p1_acceptance.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"p0_p1_acceptance_report={path}")
    print(f"passed={str(report['passed']).lower()} checks={sum(1 for item in results if item['ok'])}/{len(results)} skipped={len(skipped)}")
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL)
    parser.add_argument("--include-slow", action="store_true", help="run Java tests, full desktop tests, renderer smoke, and package verification")
    parser.add_argument("--include-local-external", action="store_true", help="run controlled MOCK_EXTERNALS=false local-provider acceptance")
    parser.add_argument("--include-live-external", action="store_true", help="run live third-party provider acceptance using PDA_LIVE_* env vars")
    parser.add_argument("--require-signed-package", action="store_true", help="require package signing gate to pass")
    args = parser.parse_args()

    results: list[dict[str, object]] = []
    skipped: list[dict[str, str]] = []
    fatal = None

    if not backend_available(args.backend_url):
        fatal = f"backend is not reachable at {args.backend_url}"
        write_report(results, skipped, fatal)
        return 1

    env = {"PDA_BASE_URL": args.backend_url}
    results.extend([
        run_step("api acceptance", "python scripts\\acceptance_backend_api.py --no-start", env=env, timeout=240),
        run_step("api mapping coverage", "python scripts\\verify_api_mapping_coverage.py", timeout=120),
        run_step("controller coverage audit", "python scripts\\verify_controller_test_coverage.py", timeout=120),
        run_step("database alignment", "python scripts\\verify_database_alignment.py", timeout=180),
        run_step("enum contract alignment", "python scripts\\verify_enum_contract_alignment.py", timeout=120),
        run_step("real external source readiness", "python scripts\\verify_real_external_readiness.py", timeout=120),
        run_step("desktop typecheck", "npm run typecheck", cwd=ROOT / "desktop", timeout=180),
    ])

    if args.include_slow:
        results.extend([
            run_wsl_step("java test suite", f"cd {json.dumps(wsl_path(ROOT))} && mvn -Dstyle.color=never test", timeout=900),
            run_step("desktop unit tests", "npm run test", cwd=ROOT / "desktop", timeout=180),
            run_step("renderer smoke", "npm run renderer:smoke", cwd=ROOT / "desktop", env={"PDA_SMOKE_API_BASE_URL": args.backend_url}, timeout=180),
            run_step("desktop package verify", "npm run package:verify", cwd=ROOT / "desktop", timeout=240),
        ])
    else:
        skipped.append({"name": "slow gates", "reason": "pass --include-slow to run Java, desktop test, renderer smoke, and package verification"})

    if args.include_local_external:
        results.append(run_step("controlled non-mock external acceptance", "python scripts\\acceptance_real_external_local.py", timeout=360))
    else:
        skipped.append({"name": "controlled non-mock external acceptance", "reason": "pass --include-local-external to run isolated backend and fake provider"})

    if args.include_live_external:
        results.append(run_step("live external provider acceptance", "python scripts\\acceptance_real_external_live.py", timeout=420))
    else:
        skipped.append({"name": "live external provider acceptance", "reason": "pass --include-live-external after setting PDA_LIVE_* credentials"})

    if args.require_signed_package:
        results.append(run_step("signed package gate", "npm run package:verify:signed", cwd=ROOT / "desktop", timeout=240))
    else:
        results.append(run_step("unsigned package gate fails closed", "npm run package:verify:signed", cwd=ROOT / "desktop", timeout=240, expect_failure=True))

    report = write_report(results, skipped, fatal)
    if not report["passed"]:
        failed = [item["name"] for item in results if not item["ok"]]
        if failed:
            print("failed=" + ",".join(failed), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
