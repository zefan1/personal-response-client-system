# WeCom Safe Profile Projection Design

## Goal

When a phone-less customer receives a phone number, create or update the WeCom Smart Table display row with the full local customer profile without allowing one invalid select value to block the rest of the profile.

## Source Of Truth

MariaDB remains the customer source of truth. The WeCom Smart Table is an asynchronous display projection. A failed or skipped projection field must never roll back a local customer update.

## Outbound Fields

The phone-assignment projection includes the configured customer profile fields when they are nonblank:

- phone and nickname;
- lead type;
- customer stage;
- body concerns;
- follow-up notes;
- internal note;
- customer profile summary;
- next follow-up time and direction;
- first, second, and third tracking captures.

Only fields with enabled datasource mappings can be sent.

## Select Validation

Before writing, the projection obtains the current Smart Table field catalog. For mapped single-select or multi-select fields, values are sent only when they match a current WeCom option. Invalid selectable values are omitted from the remote request and are logged with the customer and field name. Text, phone, date, and other valid fields continue to be sent.

The implementation does not add or change WeCom select options. It therefore cannot alter the user-managed Smart Table schema as a side effect of customer synchronization.

## Failure Handling

Transport failures still use the existing pending table-write queue. A field omitted because its select value is invalid is not a transport failure and must not create an endlessly failing retry record. The existing row ID remains the idempotency anchor for updates.

## Acceptance

For customer 56 (少花), the projection must write phone, nickname, body concerns, follow-up notes, internal note, and customer profile summary. Customer stage is written only if `意向初筛` is a current Smart Table option; otherwise it is omitted while all other fields succeed. Read-back must confirm the row ID and each supported written field.
