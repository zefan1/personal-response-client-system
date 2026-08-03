# WeCom Fixed-Egress Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let local Smart Table traffic use the Alibaba Cloud ECS fixed egress through a signed, allowlisted relay while preserving direct server deployment and phone-less write blocking.

**Architecture:** `WecomSmartSheetApiClient` remains the Smart Table protocol entrypoint. It selects direct WeCom HTTP or a signed relay client from `WECOM_TRANSPORT_MODE`; the ECS relay owns the WeCom credentials and maps a finite operation allowlist to WeCom paths. Existing Nginx application routes remain unchanged.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, `HttpURLConnection`, Python 3 standard library, systemd, Nginx, HMAC-SHA256.

---

## File Structure

- Create: `src/main/java/com/privateflow/modules/tablewrite/config/WecomRelayConfig.java` - validates relay settings.
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomRelayClient.java` - serializes and signs relay calls.
- Modify: `src/main/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfig.java` - selects `DIRECT` or `RELAY`.
- Modify: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClient.java` - branches to relay before direct token lookup.
- Create: `deploy/wecom-relay/wecom_relay.py` - loopback-only ECS service.
- Create: `deploy/wecom-relay/test_wecom_relay.py` - relay security tests.
- Create: `deploy/wecom-relay/private-domain-assistant-wecom-relay.service`, `nginx-wecom-relay.conf`, and `README.md` - deployment assets.
- Modify: `scripts/start_backend_real_wsl.sh` - forwards relay environment values without printing them.

### Task 1: Configuration Boundary

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/config/WecomRelayConfig.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfig.java`
- Test: `src/test/java/com/privateflow/modules/tablewrite/config/WecomSmartSheetConfigTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void defaultsToDirectTransport() {
  assertThat(configured().transportMode()).isEqualTo(WecomTransportMode.DIRECT);
}

@Test
void relayModeRequiresBaseUrlKeyIdAndSecret() {
  assertThatThrownBy(() -> new WecomRelayConfig("", "local-test", "secret").requireConfigured())
      .hasMessageContaining("WECOM_RELAY_BASE_URL");
}
```

- [ ] **Step 2: Verify the test fails**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomSmartSheetConfigTest test'`

Expected: compilation fails because the relay configuration types do not exist.

- [ ] **Step 3: Implement the minimal types**

```java
public enum WecomTransportMode { DIRECT, RELAY }

public void requireConfigured() {
  if (baseUrl.isBlank() || keyId.isBlank() || secret.isBlank()) {
    throw new IllegalStateException("Missing required relay environment variables");
  }
}
```

`WECOM_TRANSPORT_MODE` accepts only `DIRECT` or `RELAY`; blank is `DIRECT`.

- [ ] **Step 4: Verify the test passes**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomSmartSheetConfigTest test'`

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/privateflow/modules/tablewrite/config src/test/java/com/privateflow/modules/tablewrite/config
git commit -m "feat(wecom): add relay transport configuration"
```

### Task 2: Signed Local Relay Client

**Files:**
- Create: `src/main/java/com/privateflow/modules/tablewrite/client/WecomRelayClient.java`
- Test: `src/test/java/com/privateflow/modules/tablewrite/client/WecomRelayClientTest.java`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void signsTheExactSerializedRequestBody() {
  JsonNode result = client.post("get_records", Map.of("document_id", "doc-1"), Duration.ofSeconds(5));
  assertThat(captured.headers().get("X-Relay-Signature")).isEqualTo(expectedSignature(captured.body()));
  assertThat(captured.uri().toString()).isEqualTo("https://relay.example/v1/wecom/api");
  assertThat(result.path("errcode").asInt()).isZero();
}

@Test
void rejectsMissingRelaySettingsBeforeSending() {
  assertThatThrownBy(() -> incompleteClient.post("get_records", Map.of(), Duration.ofSeconds(5)))
      .hasMessageContaining("relay");
  assertThat(transport.callCount()).isZero();
}
```

- [ ] **Step 2: Verify the test fails**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomRelayClientTest test'`

Expected: compilation fails because `WecomRelayClient` does not exist.

- [ ] **Step 3: Implement signing**

```java
String rawBody = objectMapper.writeValueAsString(Map.of(
    "operation", operation, "payload", payload, "requestId", requestId));
String signingText = timestamp + "." + nonce + "." + rawBody;
String signature = HexFormat.of().formatHex(
    Mac.getInstance("HmacSHA256").doFinal(signingText.getBytes(StandardCharsets.UTF_8)));
```

POST exactly `relayBaseUrl + "/v1/wecom/api"`; send the five `X-Relay-*` headers; reject non-object or missing-`errcode` responses.

- [ ] **Step 4: Verify the test passes**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomRelayClientTest test'`

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomRelayClient.java src/test/java/com/privateflow/modules/tablewrite/client/WecomRelayClientTest.java
git commit -m "feat(wecom): sign Smart Table relay requests"
```

### Task 3: Select Relay Before Direct Token Lookup

**Files:**
- Modify: `src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClient.java`
- Modify: `src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClientTest.java`

- [ ] **Step 1: Write the failing selection tests**

```java
@Test
void relayModePostsOperationWithoutFetchingDirectAccessToken() {
  JsonNode response = relayConfiguredApi.post("get_records", Map.of("document_id", "doc-1"), Duration.ofSeconds(5));
  assertThat(response.path("errcode").asInt()).isZero();
  verifyNoInteractions(tokenProvider);
}

@Test
void directModeStillUsesTheExistingWeComEndpoint() {
  directConfiguredApi.post("get_records", Map.of("document_id", "doc-1"), Duration.ofSeconds(5));
  verify(tokenProvider).get(any());
}
```

- [ ] **Step 2: Verify the test fails**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomSmartSheetApiClientTest test'`

Expected: relay-mode test fails because `post()` always obtains a direct token.

- [ ] **Step 3: Add one early branch**

```java
if (config.transportMode() == WecomTransportMode.RELAY) {
  return relayClient.post(operation, body, timeout);
}
```

Place it after existing operation/timeout validation and before `tokenProvider.get(...)`; retain all direct behavior.

- [ ] **Step 4: Verify the selected tests pass**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomSmartSheetApiClientTest,WecomRelayClientTest test'`

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClient.java src/test/java/com/privateflow/modules/tablewrite/client/WecomSmartSheetApiClientTest.java
git commit -m "feat(wecom): route Smart Table calls through relay mode"
```

### Task 4: Preserve Phone-Less Blocking

**Files:**
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreatorPhoneLessTest.java`
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/TableWritePhoneLessBlockTest.java`

- [ ] **Step 1: Write the failing no-outbound contract**

```java
@Test
void blankPhoneDoesNotInvokeTheConfiguredTableClient() {
  assertThatThrownBy(() -> creator.create(phoneLessEvent()))
      .extracting("errorCode")
      .isEqualTo(TableWriteErrorCodes.TABLE_WRITE_BLOCKED);
  verifyNoInteractions(tableClient);
}
```

- [ ] **Step 2: Verify the test fails until it proves no outbound call**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=NewCustomerRowCreatorPhoneLessTest,TableWritePhoneLessBlockTest test'`

Expected: failure if the existing test does not assert zero `WecomTableClient` calls.

- [ ] **Step 3: Keep the local guard first**

```java
if (event == null || event.phone() == null || event.phone().isBlank()) {
  throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_BLOCKED,
      "smart table create is blocked because the configured unique field has no phone value");
}
```

- [ ] **Step 4: Verify the phone-less tests pass**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=NewCustomerRowCreatorPhoneLessTest,TableWritePhoneLessBlockTest test'`

Expected: `BUILD SUCCESS` and zero table-client interactions.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/privateflow/modules/tablewrite/service
git commit -m "test(wecom): preserve phone-less local table block"
```

### Task 5: ECS Relay Process

**Files:**
- Create: `deploy/wecom-relay/wecom_relay.py`
- Create: `deploy/wecom-relay/test_wecom_relay.py`

- [ ] **Step 1: Write the failing Python tests**

```python
def test_unsigned_request_is_rejected():
    assert app.handle(headers={}, raw_body=b"{}").status == 401

def test_operation_outside_allowlist_is_rejected():
    assert signed_request({"operation": "delete_everything", "payload": {}}).status == 400
```

- [ ] **Step 2: Verify the tests fail**

Run: `python -m unittest deploy/wecom-relay/test_wecom_relay.py -v`

Expected: import failure because `wecom_relay.py` does not exist.

- [ ] **Step 3: Implement a loopback-only service**

```python
ALLOWED_OPERATIONS = {
    "get_fields": "/cgi-bin/wedoc/smartsheet/get_fields",
    "get_records": "/cgi-bin/wedoc/smartsheet/get_records",
    "add_records": "/cgi-bin/wedoc/smartsheet/add_records",
    "update_records": "/cgi-bin/wedoc/smartsheet/update_records",
    "get_sheet": "/cgi-bin/wedoc/smartsheet/get_sheet",
    "get_views": "/cgi-bin/wedoc/smartsheet/get_views",
    "add_fields": "/cgi-bin/wedoc/smartsheet/add_fields",
    "update_fields": "/cgi-bin/wedoc/smartsheet/update_fields",
}
```

Require 10-digit timestamp within 300 seconds, UUID-like nonce, and `hmac.compare_digest`. Cache the ECS-owned token, never log body/secrets, and bind `ThreadingHTTPServer(("127.0.0.1", port), Handler)`.

- [ ] **Step 4: Verify Python tests pass**

Run: `python -m unittest deploy/wecom-relay/test_wecom_relay.py -v`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add deploy/wecom-relay/wecom_relay.py deploy/wecom-relay/test_wecom_relay.py
git commit -m "feat(wecom): add allowlisted ECS relay service"
```

### Task 6: ECS Deployment Assets

**Files:**
- Create: `deploy/wecom-relay/private-domain-assistant-wecom-relay.service`
- Create: `deploy/wecom-relay/nginx-wecom-relay.conf`
- Create: `deploy/wecom-relay/README.md`
- Modify: `scripts/start_backend_real_wsl.sh`

- [ ] **Step 1: Write the failing start-script contract**

Run: `rg -n "WECOM_TRANSPORT_MODE" scripts/start_backend_real_wsl.sh`

Expected: no output before the relay variables are passed through.

- [ ] **Step 2: Add the loopback Nginx route and systemd unit**

```nginx
location = /v1/wecom/api {
    client_max_body_size 2m;
    proxy_pass http://127.0.0.1:18081;
    proxy_read_timeout 35s;
}
```

```ini
[Service]
EnvironmentFile=/etc/private-domain-assistant/wecom-relay.env
ExecStart=/usr/bin/python3 /opt/private-domain-assistant/wecom-relay/wecom_relay.py
Restart=on-failure
```

Document installation, `nginx -t`, service status, secret-free environment-file creation, and rollback that removes only the relay route/service.

- [ ] **Step 3: Verify static checks pass**

Run: `python -m py_compile deploy/wecom-relay/wecom_relay.py && python -m unittest deploy/wecom-relay/test_wecom_relay.py -v`

Expected: exit code 0.

- [ ] **Step 4: Commit**

```bash
git add deploy/wecom-relay scripts/start_backend_real_wsl.sh
git commit -m "ops(wecom): document relay service deployment"
```

### Task 7: Full Verification And Controlled External Acceptance

- [ ] **Step 1: Run focused tests**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dtest=WecomSmartSheetConfigTest,WecomRelayClientTest,WecomSmartSheetApiClientTest,NewCustomerRowCreatorPhoneLessTest,TableWritePhoneLessBlockTest test'`

Run: `python -m unittest deploy/wecom-relay/test_wecom_relay.py -v`

Expected: all pass.

- [ ] **Step 2: Run full regressions**

Run: `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统/.worktrees/wecom-server-relay && mvn -Dstyle.color=never test'`

Run: `npm test -- --run` from `desktop`.

Expected: zero failures, with skips reported separately.

- [ ] **Step 3: Deploy the relay only after external-change confirmation**

Create the ECS directory, copy only tracked relay files, add the non-versioned relay environment file, run `sudo nginx -t`, and restart only `private-domain-assistant-wecom-relay`. Do not overwrite existing ECS environment values or Nginx application routes.

- [ ] **Step 4: Run low-risk remote acceptance**

Send a signed `get_fields` call through `/v1/wecom/api`.

Expected: `errcode=0`; the ECS egress remains the IP retained in WeCom trusted IPs.

- [ ] **Step 5: Ask for confirmation immediately before a real Smart Table write**

State the exact target and test value, then wait for user approval before creating or modifying a remote record.

- [ ] **Step 6: Commit and push verified implementation**

```bash
git add -A
git commit -m "feat(wecom): support fixed-egress relay mode"
git push -u origin feat/wecom-server-relay
```

