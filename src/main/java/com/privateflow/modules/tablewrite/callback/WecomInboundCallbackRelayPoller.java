package com.privateflow.modules.tablewrite.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pulls durable callback notifications while the business backend is still running locally. */
@Component
class WecomInboundCallbackRelayPoller {

  private static final Logger log = LoggerFactory.getLogger(WecomInboundCallbackRelayPoller.class);
  private final WecomInboundCallbackRelayConfig config;
  private final WecomInboundCallbackRelayClient relayClient;
  private final WecomSmartSheetCallbackService callbackService;

  WecomInboundCallbackRelayPoller(
      WecomInboundCallbackRelayConfig config,
      WecomInboundCallbackRelayClient relayClient,
      WecomSmartSheetCallbackService callbackService) {
    this.config = config;
    this.relayClient = relayClient;
    this.callbackService = callbackService;
  }

  @Scheduled(fixedDelayString = "${wecom.inbound-relay.poll-interval-ms:5000}")
  void pull() {
    if (!config.configured()) {
      return;
    }
    try {
      for (WecomInboundCallbackRelayClient.RelayEvent event : relayClient.claim(100)) {
        try {
          callbackService.receiveRelayed(event);
          relayClient.acknowledge(event.id(), event.lease_token());
        } catch (RuntimeException ex) {
          log.warn("WeCom inbound relay event will be retried: {}", ex.getMessage());
        }
      }
    } catch (RuntimeException ex) {
      log.warn("WeCom inbound relay is temporarily unavailable: {}", ex.getMessage());
    }
  }
}
