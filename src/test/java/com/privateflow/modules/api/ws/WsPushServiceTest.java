package com.privateflow.modules.api.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.config.SystemConfigProvider;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class WsPushServiceTest {

  @Test
  void invalidationNotifiesOnlyTheCurrentOnlineSessionAndDoesNotQueueForReplay() throws Exception {
    WsOfflineMessageRepository offlineRepository = mock(WsOfflineMessageRepository.class);
    SystemConfigProvider configProvider = mock(SystemConfigProvider.class);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("session-1");
    when(session.isOpen()).thenReturn(true);
    when(offlineRepository.replay("13900000001", 0L, 100)).thenReturn(List.of());
    when(configProvider.get()).thenReturn(mock(com.privateflow.modules.api.config.SystemConfig.class));
    when(configProvider.get().wsReplayQueueSize()).thenReturn(100);
    Executor directExecutor = Runnable::run;
    WsPushService service = new WsPushService(
        offlineRepository,
        configProvider,
        new ObjectMapper(),
        directExecutor);
    service.register(new WsSessionContext("13900000001", Role.KEEPER, 2L, session, 0L));

    service.invalidateActiveSession("13900000001", "密码已重置，请使用新密码重新登录");

    ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(message.capture());
    assertThat(message.getValue().getPayload()).contains("AUTH_INVALIDATED");
    assertThat(message.getValue().getPayload()).contains("密码已重置，请使用新密码重新登录");
    verify(session).close();
    verify(offlineRepository, never()).save(eq("13900000001"), any());
  }
}
