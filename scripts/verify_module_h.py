from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "dev-progress/01H_progress.md",
    "src/main/resources/db/migration/V8__module_h_api_websocket.sql",
    "src/main/java/com/privateflow/modules/api/ApiErrorCodes.java",
    "src/main/java/com/privateflow/modules/api/ApiException.java",
    "src/main/java/com/privateflow/modules/api/Role.java",
    "src/main/java/com/privateflow/modules/api/config/SystemConfig.java",
    "src/main/java/com/privateflow/modules/api/config/SystemConfigProvider.java",
    "src/main/java/com/privateflow/modules/api/config/ApiModuleConfiguration.java",
    "src/main/java/com/privateflow/modules/api/auth/JwtService.java",
    "src/main/java/com/privateflow/modules/api/auth/JwtAuthenticationFilter.java",
    "src/main/java/com/privateflow/modules/api/auth/AuthService.java",
    "src/main/java/com/privateflow/modules/api/auth/AccountRepository.java",
    "src/main/java/com/privateflow/modules/api/ws/WebSocketConfiguration.java",
    "src/main/java/com/privateflow/modules/api/ws/DesktopHandshakeInterceptor.java",
    "src/main/java/com/privateflow/modules/api/ws/DesktopWebSocketHandler.java",
    "src/main/java/com/privateflow/modules/api/ws/WsPushService.java",
    "src/main/java/com/privateflow/modules/api/ws/WsOfflineMessageRepository.java",
    "src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java",
    "src/main/java/com/privateflow/modules/api/web/AuthController.java",
    "src/main/java/com/privateflow/modules/api/web/ChatController.java",
    "src/main/java/com/privateflow/modules/api/web/HelpController.java",
    "src/main/java/com/privateflow/modules/api/web/ConfigController.java",
    "src/main/java/com/privateflow/modules/api/web/HealthController.java",
    "src/main/java/com/privateflow/modules/api/web/GlobalApiExceptionHandler.java",
    "src/main/java/com/privateflow/modules/api/events/WsEventBridge.java",
    "src/main/java/com/privateflow/modules/api/health/HealthService.java",
    "src/main/java/com/privateflow/modules/api/alert/SystemAlertRepository.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditLogger.java",
]

system_keys = [
    "system.jwt_secret",
    "system.jwt_expire_hours",
    "system.jwt_refresh_days",
    "system.ws_heartbeat_s",
    "system.ws_timeout_s",
    "system.ws_replay_queue_size",
    "system.request_total_timeout_ms",
    "system.audit_log_retention_days",
    "system.login_fail_limit",
    "system.login_lock_minutes",
    "system.request_context_ttl_s",
    "system.ws_offline_retention_days",
    "system.alert_retention_days",
    "system.config_change_channel",
    "system.ws_push_channel",
]

errors = []

for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

if not errors:
    pom = read("pom.xml")
    for token in ["spring-boot-starter-websocket", "spring-security-crypto"]:
        if token not in pom:
            errors.append(f"pom.xml missing {token}")

    sql = read("src/main/resources/db/migration/V8__module_h_api_websocket.sql")
    for table in ["accounts", "ws_offline_queue", "system_alerts"]:
        if f"CREATE TABLE IF NOT EXISTS {table}" not in sql:
            errors.append(f"V8 migration missing table {table}")
    for index in ["idx_username", "idx_user_delivered", "idx_type_status", "idx_status_occurred"]:
        if index not in sql:
            errors.append(f"V8 migration missing index {index}")
    for key in system_keys:
        if key not in sql:
            errors.append(f"V8 migration missing config key {key}")

    app = read("src/main/resources/application.yml")
    for token in ["system:", "jwt-secret:", "ws-heartbeat-s: 30", "ws-timeout-s: 60", "request-context-ttl-s: 300"]:
        if token not in app:
            errors.append(f"application.yml missing {token}")

    codes = read("src/main/java/com/privateflow/modules/api/ApiErrorCodes.java")
    for code in ["80-10001", "80-10002", "80-10003", "80-10004", "80-10005"]:
        if code not in codes:
            errors.append(f"ApiErrorCodes missing {code}")

    config = read("src/main/java/com/privateflow/modules/api/config/SystemConfigProvider.java")
    for key in system_keys + ["ConfigChangedEvent", "1, 168", "15, 60", "30, 120", "50, 500"]:
        if key not in config:
            errors.append(f"SystemConfigProvider missing {key}")

    module_config = read("src/main/java/com/privateflow/modules/api/config/ApiModuleConfiguration.java")
    for token in ["wsBroadcastExecutor", "auditLogExecutor", "apiOrchestrationExecutor", "CallerRunsPolicy", "500", "200"]:
        if token not in module_config:
            errors.append(f"ApiModuleConfiguration missing {token}")

    jwt = read("src/main/java/com/privateflow/modules/api/auth/JwtService.java")
    for token in ["HmacSHA256", "jwtSecret", "jwtExpireHours", "verify", "issue", "exp"]:
        if token not in jwt:
            errors.append(f"JwtService missing {token}")

    filter_src = read("src/main/java/com/privateflow/modules/api/auth/JwtAuthenticationFilter.java")
    for token in ["/api/v1/", "/admin/api/v1/", "Authorization", "Bearer ", "KEEPER", "FORBIDDEN"]:
        if token not in filter_src:
            errors.append(f"JwtAuthenticationFilter missing {token}")

    ws_config = read("src/main/java/com/privateflow/modules/api/ws/WebSocketConfiguration.java")
    for token in ["@EnableWebSocket", "/ws/v1/desktop", "DesktopHandshakeInterceptor"]:
        if token not in ws_config:
            errors.append(f"WebSocketConfiguration missing {token}")

    ws_push = read("src/main/java/com/privateflow/modules/api/ws/WsPushService.java")
    for token in ["ConcurrentHashMap", "pushWsMessage", "broadcastWs", "replay", "wsTimeoutS", "wsReplayQueueSize"]:
        if token not in ws_push:
            errors.append(f"WsPushService missing {token}")
    offline_repo = read("src/main/java/com/privateflow/modules/api/ws/WsOfflineMessageRepository.java")
    for token in ["ws_offline_queue", "INSERT IGNORE", "markDelivered", "cleanup", "message_id"]:
        if token not in offline_repo:
            errors.append(f"WsOfflineMessageRepository missing {token}")
    handler = read("src/main/java/com/privateflow/modules/api/ws/DesktopWebSocketHandler.java")
    for token in ["PING", "PONG", "afterConnectionClosed", "WsSessionContext"]:
        if token not in handler:
            errors.append(f"DesktopWebSocketHandler missing {token}")

    chat = read("src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java")
    for token in ["ImageRecognitionService", "CustomerMatchService", "SkillGatewayService", "CustomerQueryService", "CustomerMessageSentEvent", "eventPublisher.publishEvent", "Scene.REGENERATE"]:
        if token not in chat:
            errors.append(f"ChatOrchestrationService missing {token}")

    auth_controller = read("src/main/java/com/privateflow/modules/api/web/AuthController.java")
    for token in ["/api/v1/auth/login", "/admin/api/v1/auth/login", "/api/v1/auth/refresh", "X-Forwarded-For"]:
        if token not in auth_controller:
            errors.append(f"AuthController missing {token}")

    chat_controller = read("src/main/java/com/privateflow/modules/api/web/ChatController.java")
    for token in ["/api/v1/chat", "/recognize", "/generate", "/regenerate", "/send-confirm"]:
        if token not in chat_controller:
            errors.append(f"ChatController missing {token}")

    help_controller = read("src/main/java/com/privateflow/modules/api/web/HelpController.java")
    for token in ["/api/v1/help", "/request", "/resolve"]:
        if token not in help_controller:
            errors.append(f"HelpController missing {token}")

    config_controller = read("src/main/java/com/privateflow/modules/api/web/ConfigController.java")
    for token in ["/admin/api/v1/configs", "@GetMapping", "@PutMapping", "ConfigAdminService"]:
        if token not in config_controller:
            errors.append(f"ConfigController missing {token}")

    health_controller = read("src/main/java/com/privateflow/modules/api/web/HealthController.java")
    for token in ["/admin/api/v1", "/health", "HealthService"]:
        if token not in health_controller:
            errors.append(f"HealthController missing {token}")

    bridge = read("src/main/java/com/privateflow/modules/api/events/WsEventBridge.java")
    for token in ["FollowupWsMessageReadyEvent", "ProfileSuggestionsReadyEvent", "ImageServiceStatusEvent", "PROFILE_SUGGESTIONS", "IMAGE_SERVICE_STATUS", "IMAGE_SERVICE_DOWN"]:
        if token not in bridge:
            errors.append(f"WsEventBridge missing {token}")

    exception_handler = read("src/main/java/com/privateflow/modules/api/web/GlobalApiExceptionHandler.java")
    for token in ["RestControllerAdvice", "ApiException", "BAD_REQUEST", "AUTH_FAILED", "FORBIDDEN", "INTERNAL_ERROR"]:
        if token not in exception_handler:
            errors.append(f"GlobalApiExceptionHandler missing {token}")

    progress = read("dev-progress/01H_progress.md")
    for label in [f"SF-H{i:02d}" for i in range(1, 21)]:
        if label not in progress:
            errors.append(f"progress card missing {label}")

if errors:
    print("Module H verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module H static verification passed.")
print(f"Checked {len(required_files)} required files and {len(system_keys)} system config keys.")
