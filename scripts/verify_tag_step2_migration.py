#!/usr/bin/env python3
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DB = os.environ.get("TAG_STEP2_SOURCE_DB", "private_domain_assistant_smoke")
TEST_DB = os.environ.get("TAG_STEP2_TEST_DB", "private_domain_assistant_tag_step2_verify")
KEEP_DB = os.environ.get("KEEP_TAG_STEP2_DB", "").lower() in {"1", "true", "yes"}
MIGRATION = ROOT / "src" / "main" / "resources" / "db" / "migration" / "V68__unified_customer_tag_foundation.sql"


def validate_database_name(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_]+", value):
        raise RuntimeError(f"invalid database name: {value}")
    return value


def run(args: list[str], *, stdin: str | None = None, expect_success: bool = True) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        args,
        cwd=ROOT,
        input=stdin,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )
    if expect_success and completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip())
    return completed


def mysql(sql: str, database: str | None = None, *, expect_success: bool = True) -> subprocess.CompletedProcess[str]:
    args = ["wsl", "-e", "sudo", "-n", "mysql", "--default-character-set=utf8mb4", "-N", "-B"]
    if database:
        args.append(database)
    return run(args, stdin=sql, expect_success=expect_success)


def scalar(sql: str) -> str:
    return mysql(sql, TEST_DB).stdout.strip()


def expect_failure(sql: str, expected_fragment: str) -> None:
    result = mysql(sql, TEST_DB, expect_success=False)
    if result.returncode == 0:
        raise AssertionError("statement unexpectedly succeeded")
    detail = result.stderr + result.stdout
    if expected_fragment not in detail:
        raise AssertionError(f"failure did not contain {expected_fragment!r}: {detail.strip()}")


def assert_equal(actual: str, expected: str, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, actual {actual!r}")


def clone_source() -> None:
    mysql(
        f"""
        DROP DATABASE IF EXISTS {TEST_DB};
        CREATE DATABASE {TEST_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
        CREATE USER IF NOT EXISTS 'pda_smoke'@'localhost' IDENTIFIED BY 'pda_smoke_pwd';
        GRANT ALL PRIVILEGES ON {TEST_DB}.* TO 'pda_smoke'@'localhost';
        FLUSH PRIVILEGES;
        """
    )
    command = (
        f"sudo -n mysqldump --single-transaction --routines=false --triggers=false {SOURCE_DB} "
        f"| sudo -n mysql {TEST_DB}"
    )
    run(["wsl", "-e", "bash", "-lc", command])


def seed_fixtures() -> None:
    mysql(
        """
        INSERT INTO customers (phone, personality_type, body_concerns, worries, intent_level)
        VALUES
          ('19900006801', 'LOYALIST', '腹直肌分离', 'FEAR_PAIN', '高意向'),
          ('19900006802', '温和型', '腹直肌分离,漏尿', '担心价格高', 'MEDIUM');
        UPDATE tag_values SET is_enabled = 0 WHERE tag_value = 'FEAR_PAIN';
        """,
        TEST_DB,
    )


def apply_migration() -> None:
    mysql(MIGRATION.read_text(encoding="utf-8"), TEST_DB)


def verify_data() -> None:
    assert_equal(scalar("SELECT COUNT(*) FROM customer_tag_assignments;"), "9", "assignment count")
    assert_equal(scalar("SELECT COUNT(*) FROM customer_tag_assignments WHERE is_active = 1;"), "8", "active assignment count")
    assert_equal(scalar("SELECT COUNT(*) FROM customer_tag_assignments WHERE is_active = 0 AND invalidated_reason = 'LEGACY_TAG_DISABLED';"), "1", "disabled legacy assignment")
    assert_equal(scalar("SELECT COUNT(*) FROM unmatched_legacy_tag_values;"), "18", "unmatched count")
    assert_equal(scalar("SELECT COUNT(*) FROM system_tag_suggestions WHERE customer_id IS NOT NULL AND validation_status='UNMATCHED_LEGACY' AND unmatched_legacy_value_id IS NOT NULL;"), "6", "legacy rule suggestions")
    assert_equal(scalar("SELECT COUNT(*) FROM personality_tags WHERE enabled=0 AND migration_status='MAPPED' AND canonical_tag_value_id IS NOT NULL;"), "3", "retired personality dictionary")
    assert_equal(scalar("SELECT COUNT(*) FROM tag_legacy_value_mappings WHERE source_type='PERSONALITY_TAGS' AND mapping_status='MAPPED';"), "3", "personality mappings")
    assert_equal(
        scalar("SELECT CONCAT_WS('|', personality_type, body_concerns, worries, intent_level) FROM customers WHERE phone='19900006801';"),
        "LOYALIST|DIASTASIS_RECTI|FEAR_PAIN|HIGH",
        "fixture one normalized fields",
    )
    assert_equal(
        scalar("SELECT CONCAT_WS('|', personality_type, body_concerns, worries, intent_level) FROM customers WHERE phone='19900006802';"),
        "PEACEMAKER|DIASTASIS_RECTI,URINE_LEAKAGE|FEAR_EXPENSIVE|MEDIUM",
        "fixture two normalized fields",
    )
    assert_equal(
        scalar("SELECT COUNT(*) FROM tag_values WHERE meaning='' OR applicable_when='' OR not_applicable_when='' OR positive_examples='' OR negative_examples='' OR synonyms_json='[]';"),
        "0",
        "builtin tag semantics",
    )


def verify_constraints() -> None:
    ids = scalar(
        """
        SELECT CONCAT_WS('|',
          (SELECT id FROM customers WHERE phone='19900006801'),
          (SELECT id FROM customers WHERE phone='19900006802'),
          (SELECT c.id FROM customers c LEFT JOIN customer_tag_assignments a ON a.customer_id=c.id WHERE a.id IS NULL ORDER BY c.id LIMIT 1),
          (SELECT id FROM tag_categories WHERE category_key='personality_type'),
          (SELECT id FROM tag_categories WHERE category_key='body_concerns'),
          (SELECT id FROM tag_values WHERE tag_value='PEACEMAKER' AND category_id=(SELECT id FROM tag_categories WHERE category_key='personality_type')),
          (SELECT id FROM tag_values WHERE tag_value='DIASTASIS_RECTI' AND category_id=(SELECT id FROM tag_categories WHERE category_key='body_concerns')),
          (SELECT id FROM tag_values WHERE tag_value='URINE_LEAKAGE' AND category_id=(SELECT id FROM tag_categories WHERE category_key='body_concerns'))
        );
        """
    ).split("|")
    customer_one, customer_two, customer_without_assignments, personality, body, peacemaker, diastasis, leakage = ids

    expect_failure(
        f"INSERT INTO customer_tag_assignments (customer_id,category_id,tag_value_id,selection_mode,source_type) VALUES ({customer_one},{personality},{peacemaker},'SINGLE','TEST');",
        "uk_customer_active_single_category",
    )
    expect_failure(
        f"INSERT INTO customer_tag_assignments (customer_id,category_id,tag_value_id,selection_mode,source_type) VALUES ({customer_one},{body},{diastasis},'MULTI','TEST');",
        "uk_customer_active_tag",
    )
    mysql(
        f"INSERT INTO customer_tag_assignments (customer_id,category_id,tag_value_id,selection_mode,source_type) VALUES ({customer_one},{body},{leakage},'MULTI','TEST');",
        TEST_DB,
    )
    expect_failure(
        f"INSERT INTO customer_tag_assignments (customer_id,category_id,tag_value_id,selection_mode,source_type) VALUES ({customer_without_assignments},{personality},{leakage},'SINGLE','TEST');",
        "fk_customer_tags_value_category",
    )
    expect_failure(
        f"INSERT INTO customer_tag_assignments (customer_id,category_id,tag_value_id,selection_mode,source_type) VALUES ({customer_without_assignments},{personality},{peacemaker},'MULTI','TEST');",
        "fk_customer_tags_category_mode",
    )
    expect_failure(
        f"INSERT INTO customer_tag_assignments (customer_id,category_id,tag_value_id,selection_mode,source_type,confidence) VALUES ({customer_two},{body},{leakage},'MULTI','TEST',1.5000);",
        "chk_customer_tags_confidence",
    )
    expect_failure(
        f"UPDATE tag_values SET synonyms_json='not-json' WHERE id={leakage};",
        "chk_tag_values_synonyms_json",
    )


def main() -> int:
    validate_database_name(SOURCE_DB)
    validate_database_name(TEST_DB)
    if SOURCE_DB == TEST_DB:
        raise RuntimeError("source and test databases must be different")
    if not MIGRATION.exists():
        raise RuntimeError(f"migration not found: {MIGRATION}")
    try:
        clone_source()
        seed_fixtures()
        apply_migration()
        verify_data()
        verify_constraints()
        print("tag_step2_migration=passed assignments=9 active=8 unmatched=18 constraints=7")
        return 0
    finally:
        if not KEEP_DB:
            mysql(f"DROP DATABASE IF EXISTS {TEST_DB};", expect_success=False)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as ex:
        print(f"tag_step2_migration=failed error={ex}", file=sys.stderr)
        raise SystemExit(1)
