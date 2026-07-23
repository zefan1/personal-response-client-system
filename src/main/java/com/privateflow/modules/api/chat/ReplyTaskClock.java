package com.privateflow.modules.api.chat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class ReplyTaskClock {

  public static final String BUSINESS_TIME_ZONE_ID = "Asia/Shanghai";
  public static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of(BUSINESS_TIME_ZONE_ID);

  private final Clock clock;

  public ReplyTaskClock() {
    this(Clock.system(BUSINESS_TIME_ZONE));
  }

  public ReplyTaskClock(Clock clock) {
    this.clock = clock;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
