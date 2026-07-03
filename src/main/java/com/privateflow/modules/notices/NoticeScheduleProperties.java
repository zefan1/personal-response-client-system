package com.privateflow.modules.notices;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import org.springframework.stereotype.Component;

@Component
public class NoticeScheduleProperties {

  private final SystemConfigRepository configRepository;

  public NoticeScheduleProperties(SystemConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  public long scanIntervalMs() {
    return configRepository.findValue("notice.scan_interval_s")
        .map(this::parseSeconds)
        .orElse(30) * 1000L;
  }

  private int parseSeconds(String value) {
    try {
      int seconds = Integer.parseInt(value);
      return seconds < 15 || seconds > 120 ? 30 : seconds;
    } catch (NumberFormatException ex) {
      return 30;
    }
  }
}
