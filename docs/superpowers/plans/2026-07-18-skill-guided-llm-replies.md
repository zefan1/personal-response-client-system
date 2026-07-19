# Skill-Guided LLM Replies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the routed Skill a mandatory planning step before the configured LLM generates final reply suggestions, without hardcoding Qwen or any provider.

**Architecture:** `ChatOrchestrationService` calls the existing `SkillGatewayService` first. It passes the successful `SkillResponse` to `LlmReplyGenerationService`, which serializes the response into the LLM input as `skillGuidance`. Skill failure stops the LLM call; LLM failure returns the valid Skill result.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Mockito, Maven

---

### Task 1: Specify orchestration behavior

**Files:**
- Modify: `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`

- [ ] Add a test that returns an LLM reply only after `SkillGatewayService.generateReplies` returns valid guidance.
- [ ] Capture the `SkillResponse` passed to `LlmReplyGenerationService.tryGenerate` and assert it is the Skill result.
- [ ] Add a test that a `SYSTEM_FALLBACK` Skill response prevents any LLM call.
- [ ] Add a test that an empty LLM result returns the valid Skill response and reports `SKILL` as the source.
- [ ] Run `mvn -Dtest=ChatOrchestrationServiceTest test` and confirm compilation or assertions fail because the new API and ordering do not exist.

### Task 2: Specify Skill guidance in the LLM request

**Files:**
- Modify: `src/test/java/com/privateflow/modules/llm/LlmReplyGenerationServiceTest.java`

- [ ] Add a test that calls `tryGenerate(request, skillGuidance)`.
- [ ] Capture the `LlmRequest` and assert its user prompt contains `skillGuidance`, the Skill suggestion direction, customer analysis, and fixed-workflow instruction.
- [ ] Assert the prompt does not contain the full customer phone number.
- [ ] Run `mvn -Dtest=LlmReplyGenerationServiceTest test` and confirm the test fails because the overload and guidance serialization do not exist.

### Task 3: Implement Skill-first orchestration

**Files:**
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatReplySource.java`

- [ ] Call `skillGatewayService.generateReplies(skillRequest)` before the LLM.
- [ ] Detect the existing `SYSTEM_FALLBACK` response and return it without calling the LLM.
- [ ] Call `llmReplyGenerationService.tryGenerate(skillRequest, skillGuidance)` for valid guidance.
- [ ] Return the LLM response with detail indicating that Skill guidance was applied.
- [ ] Return the valid Skill response if the LLM is unavailable.
- [ ] Run `mvn -Dtest=ChatOrchestrationServiceTest test` and confirm all tests pass.

### Task 4: Inject structured Skill guidance into the LLM

**Files:**
- Modify: `src/main/java/com/privateflow/modules/llm/LlmReplyGenerationService.java`

- [ ] Add `tryGenerate(SkillRequest request, SkillResponse skillGuidance)`.
- [ ] Include `skillGuidance` in the JSON input sent to the LLM.
- [ ] Add a provider-neutral instruction that the final response must follow the Skill guidance and may improve wording only.
- [ ] Keep model, endpoint, protocol, timeout, temperature, and token limits resolved by `LlmService` and configuration.
- [ ] Run `mvn -Dtest=LlmReplyGenerationServiceTest test` and confirm all tests pass.

### Task 5: Configure and verify the live Skill

**Files:**
- Runtime configuration only; do not write credentials to tracked files.

- [ ] Save the Skill base URL and API Key through the encrypted admin configuration API.
- [ ] Create `GENERAL` bindings for all five Skill scenes using the provider's exact Skill ID.
- [ ] Test one binding through `/admin/api/v1/skills/{id}/test`.
- [ ] Exercise the screenshot-to-reply endpoint and verify logs show Skill before LLM.
- [ ] Run `mvn test`, `npm --prefix desktop test`, `npm --prefix desktop run typecheck`, and `npm --prefix desktop run build`.
