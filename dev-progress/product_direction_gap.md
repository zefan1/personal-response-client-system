# Product Direction Gap - 2026-07-03

This note records why the current frontend drifted away from the product intent. Future agents must read this before touching the desktop shell or operations admin frontend.

## 1. API Console Was Mistaken For An Operations Admin Product

The current management UI is connected to real `/admin/api/v1/*` endpoints, but its interaction model is an API console: HTTP method/path, target id fields, raw request JSON, and generic execution panels.

Manuals 40-51 define business pages for operations users: tables, filters, drawers, forms, upload controls, previews, confirmations, dashboards, version history, and clear error states. A user should never need to know a REST path, copy an id from a response, or write JSON by hand.

## 2. P0 Acceptance Was Narrowed To API Reachability

The repair for the earlier "Admin Frontend Missing" gap focused on proving every admin API could be called from the renderer. That made acceptance broad at the API surface but too shallow at the product surface.

The correct acceptance target is: each operations module can be used by an ADMIN/LEADER according to its manual workflow, with non-technical controls and production failure states.

## 3. Electron Sidebar Was Confused With Browser Preview

The desktop sidecar is specified as an Electron application that stays beside WeChat/WeCom and provides OS-level clipboard, screenshot, window capture, global shortcut, offline cache, and WebSocket capabilities.

`http://127.0.0.1:5173` is only the Vite renderer preview used during development and automated smoke testing. It is not the final one-line colleague-facing work surface.

## 4. Admin And Frontline Workflows Were Mixed

The desktop sidebar is for keepers and leaders doing live private-domain chat work. The operations admin is for configuration, content management, analytics, releases, audit, and health monitoring.

They may share authentication and API infrastructure, but their layouts and usage contexts are different: narrow Electron sidebar versus full-screen browser admin.

## 5. Endpoint Technology Boundaries Were Not Hard Enough

The development outline and manuals mention technologies, but they did not make every endpoint's formal delivery surface and acceptance entry strict enough:

- desktop sidebar: Electron application, not a production browser page;
- operations admin: full-screen web admin, not an Electron narrow panel and not an API console;
- Vite renderer URL: development preview and smoke target only.

Future plans and progress cards must explicitly state which surface is being validated, so agents do not treat a preview page, a debug console, and a production product as interchangeable.

## Non-Negotiable Follow-Up Rule

Production operations admin pages must not expose debug-console language or controls:

- no visible HTTP method/path such as `GET /admin` or `PUT /admin`;
- no visible `请求体 JSON`;
- no visible `目标 ID`;
- no workflow that asks the user to copy an id from a response before operating.
