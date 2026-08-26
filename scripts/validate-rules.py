#!/usr/bin/env python3
"""Validate the deterministic JSON contract used by the Android MVP."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

SAFE_ID = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
ROOT_KEYS = {"schema_version", "generated_at", "rules"}
RULE_KEYS = {
    "id",
    "title",
    "message",
    "enabled",
    "transition",
    "repeat",
    "cooldown_minutes",
    "responsiveness_ms",
    "dwell_ms",
    "valid_from",
    "valid_until",
    "source_text",
    "zones",
}
ZONE_KEYS = {"id", "label", "latitude", "longitude", "radius_m"}


def fail(message: str) -> None:
    raise ValueError(message)


def reject_unknown(obj: dict[str, Any], allowed: set[str], prefix: str) -> None:
    unknown = sorted(set(obj) - allowed)
    if unknown:
        fail(f"{prefix} contains unsupported fields: {', '.join(unknown)}")


def require_string(
    obj: dict[str, Any],
    name: str,
    *,
    prefix: str,
    max_length: int,
) -> str:
    value = obj.get(name)
    if not isinstance(value, str) or not value.strip():
        fail(f"{prefix}.{name} must be a non-empty string")
    value = value.strip()
    if len(value) > max_length:
        fail(f"{prefix}.{name} exceeds {max_length} characters")
    return value


def parse_timestamp(value: Any, field: str) -> datetime | None:
    if value is None:
        return None
    if not isinstance(value, str):
        fail(f"{field} must be an ISO-8601 string or null")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        fail(f"{field} is not a valid ISO-8601 timestamp: {value!r}: {exc}")
    if parsed.tzinfo is None:
        fail(f"{field} must include a timezone offset or Z")
    return parsed


def require_int(value: Any, field: str, minimum: int, maximum: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        fail(f"{field} must be an integer")
    if not minimum <= value <= maximum:
        fail(f"{field} must be {minimum}..{maximum}")
    return value


def require_number(value: Any, field: str, minimum: float, maximum: float) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        fail(f"{field} must be a number")
    number = float(value)
    if not math.isfinite(number) or not minimum <= number <= maximum:
        fail(f"{field} must be a finite number in {minimum}..{maximum}")
    return number


def validate(path: Path) -> tuple[int, int]:
    root = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(root, dict):
        fail("root must be an object")
    reject_unknown(root, ROOT_KEYS, "root")
    if root.get("schema_version") != 1:
        fail("schema_version must equal 1")
    parse_timestamp(root.get("generated_at"), "generated_at")

    rules = root.get("rules")
    if not isinstance(rules, list):
        fail("rules must be an array")
    if len(rules) > 100:
        fail("rules contains more than 100 items")

    seen_rules: set[str] = set()
    request_ids: set[str] = set()
    zone_count = 0

    for rule_index, rule in enumerate(rules):
        prefix = f"rules[{rule_index}]"
        if not isinstance(rule, dict):
            fail(f"{prefix} must be an object")
        reject_unknown(rule, RULE_KEYS, prefix)

        rule_id = require_string(rule, "id", prefix=prefix, max_length=64)
        if not SAFE_ID.fullmatch(rule_id):
            fail(f"{prefix}.id has invalid format: {rule_id!r}")
        if rule_id in seen_rules:
            fail(f"duplicate rule id: {rule_id}")
        seen_rules.add(rule_id)
        require_string(rule, "title", prefix=prefix, max_length=120)
        require_string(rule, "message", prefix=prefix, max_length=1000)

        enabled = rule.get("enabled", True)
        if not isinstance(enabled, bool):
            fail(f"{prefix}.enabled must be boolean")
        if rule.get("transition", "enter") not in {"enter", "exit", "dwell"}:
            fail(f"{prefix}.transition must be enter, exit, or dwell")
        if rule.get("repeat", "once") not in {"once", "always"}:
            fail(f"{prefix}.repeat must be once or always")

        require_int(rule.get("cooldown_minutes", 0), f"{prefix}.cooldown_minutes", 0, 10080)
        require_int(rule.get("responsiveness_ms", 120000), f"{prefix}.responsiveness_ms", 60000, 900000)
        require_int(rule.get("dwell_ms", 120000), f"{prefix}.dwell_ms", 60000, 3600000)
        valid_from = parse_timestamp(rule.get("valid_from"), f"{prefix}.valid_from")
        valid_until = parse_timestamp(rule.get("valid_until"), f"{prefix}.valid_until")
        if valid_from is not None and valid_until is not None and valid_until < valid_from:
            fail(f"{prefix}.valid_until is before valid_from")

        source_text = rule.get("source_text")
        if source_text is not None:
            if not isinstance(source_text, str):
                fail(f"{prefix}.source_text must be a string or null")
            if len(source_text) > 4000:
                fail(f"{prefix}.source_text exceeds 4000 characters")

        zones = rule.get("zones")
        if not isinstance(zones, list) or not zones:
            fail(f"{prefix}.zones must be a non-empty array")
        if len(zones) > 100:
            fail(f"{prefix}.zones contains more than 100 items")
        seen_zones: set[str] = set()
        for zone_index, zone in enumerate(zones):
            zone_prefix = f"{prefix}.zones[{zone_index}]"
            if not isinstance(zone, dict):
                fail(f"{zone_prefix} must be an object")
            reject_unknown(zone, ZONE_KEYS, zone_prefix)
            zone_id = require_string(zone, "id", prefix=zone_prefix, max_length=64)
            if not SAFE_ID.fullmatch(zone_id):
                fail(f"{zone_prefix}.id has invalid format: {zone_id!r}")
            if zone_id in seen_zones:
                fail(f"duplicate zone id {zone_id!r} in rule {rule_id!r}")
            seen_zones.add(zone_id)
            request_id = f"{rule_id}::{zone_id}"
            if request_id in request_ids:
                fail(f"duplicate geofence request id: {request_id}")
            request_ids.add(request_id)

            label = zone.get("label", zone_id)
            if not isinstance(label, str) or not label.strip() or len(label.strip()) > 160:
                fail(f"{zone_prefix}.label must be a non-empty string of at most 160 characters")
            require_number(zone.get("latitude"), f"{zone_prefix}.latitude", -90, 90)
            require_number(zone.get("longitude"), f"{zone_prefix}.longitude", -180, 180)
            require_number(zone.get("radius_m"), f"{zone_prefix}.radius_m", 75, 2000)
            zone_count += 1

    if zone_count > 100:
        fail(f"Android geofence limit exceeded: {zone_count} > 100")
    return len(rules), zone_count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    try:
        rules, zones = validate(args.path)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"INVALID: {exc}", file=sys.stderr)
        return 1
    print(f"VALID: rules={rules} zones={zones}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
