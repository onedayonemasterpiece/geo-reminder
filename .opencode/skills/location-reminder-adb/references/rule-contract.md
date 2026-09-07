# Rule contract

Canonical local input is `config/rules.local.json`; schema version is 1.
Android request IDs are deterministically derived as `<rule-id>::<zone-id>`.
The app rejects unknown fields, wrong JSON types, duplicate IDs, invalid ranges,
non-UTC-compatible timestamps and more than 100 total zones.

## Root

- `schema_version`: exactly `1`;
- `generated_at`: optional ISO-8601 timestamp with timezone;
- `rules`: array, maximum 100 rules.

## Rule

Required:

- `id`: lowercase ASCII `^[a-z0-9][a-z0-9._-]{0,63}$`;
- `title`: 1–120 characters;
- `message`: 1–1000 characters;
- `zones`: non-empty array.

Optional:

- `enabled`: boolean, default `true`;
- `transition`: `enter`, `exit`, `dwell`, default `enter`;
- `repeat`: `once`, `always`, default `once`;
- `cooldown_minutes`: 0–10080, default 0;
- `responsiveness_ms`: 60000–900000, default 120000;
- `dwell_ms`: 60000–3600000, default 120000;
- `valid_from`, `valid_until`: ISO-8601 timestamps with timezone;
- `source_text`: up to 4000 characters for auditability.

A stable rule ID preserves completion/cooldown state across imports. Change it
only when the user intends a distinct reminder.

## Zone

Required:

- `id`: same identifier grammar as a rule;
- `latitude`: -90..90;
- `longitude`: -180..180;
- `radius_m`: 75..2000.

Optional but strongly recommended:

- `label`: 1–160 characters.

Polygons and dynamic POI/category queries are not part of schema v1. The agent
must resolve a finite reviewed list before import. Leave examples and uncertain
locations `enabled: false`.
