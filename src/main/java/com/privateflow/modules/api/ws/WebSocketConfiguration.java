package com.privateflow.modules.api.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

  private final DesktopWebSocketHandler handler;
  private final DesktopHandshakeInterceptor interceptor;

  public WebSocketConfiguration(DesktopWebSocketHandler handler, DesktopHandshakeInterceptor interceptor) {
    this.handler = handler;
    this.interceptor = interceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/v1/desktop")
        .addInterceptors(interceptor)
        .setAllowedOrigins("*");
  }
}
