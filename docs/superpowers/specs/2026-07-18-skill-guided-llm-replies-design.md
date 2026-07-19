# Skill-Guided LLM Replies Design

## Goal

Use the configured Skill as the mandatory, stable reasoning workflow before the currently active LLM generates final reply suggestions. Qwen is only the currently selected provider and must remain replaceable through configuration.

## Required Flow

1. The active image environment recognizes the screenshot.
2. The application builds the normal `SkillRequest` from the recognized conversation and customer context.
3. The routed Skill runs first for the current scene and lead type.
4. A valid Skill response is serialized as structured guidance for the active LLM environment.
5. The LLM generates the final three suggestions while being instructed to follow the Skill guidance.

The Skill is not an LLM failure fallback in this flow. It is a mandatory planning step.

## Failure Rules

- If the Skill returns its system fallback response, do not call the LLM. Return the existing system fallback response.
- If the Skill succeeds but the LLM fails or returns invalid JSON, return the valid Skill suggestions as the controlled fallback.
- Never call the LLM without Skill guidance for reply-generation scenes.

## Provider Configuration

- Image and LLM models continue to come from active environment and system configuration records.
- No Qwen model name or Aliyun endpoint is added to orchestration code.
- The selected provider can be replaced without changing the reply workflow.

## Scenes

The same `销冠训练` Skill will be routed for:

- `CHAT_RECOGNIZE`
- `ACTIVE_REPLY`
- `REGENERATE`
- `PROFILE_EXTRACT`
- `OPENING`

Use `GENERAL` bindings so the routing applies to all lead types unless a more specific route is added later.

## Verification

- Unit tests prove Skill is called before LLM and its result is present in the LLM prompt.
- Unit tests prove a Skill fallback prevents the LLM call.
- Unit tests prove an LLM failure returns the stable Skill result.
- Existing backend and desktop test suites remain green.
- Live verification calls the configured Skill endpoint and then exercises the screenshot-to-reply flow.

## SkillMall Runtime Configuration

The currently selected SkillMall skill is identified by the stable slug `sales-champion-coach` (display name: `销冠训练`).

The public page currently shows an outdated `mcp.skillmall.com/v1/sse` example. The reachable service endpoint verified during integration is:

```text
https://mcp.xn--15tq51d.top/mcp
```

This endpoint uses MCP Streamable HTTP. The client performs `initialize`, opens a session, sends the `initialized` notification, then calls the derived tool name `sales_champion_coach__query` with the configured Skill ID in `X-Skill-Id` and the API key in the bearer header.

The current key authenticates successfully but the provider reports `Quota exhausted: 0/0`. The application configuration is therefore complete, while live reply generation remains blocked until the Skill subscription/key has available quota.
