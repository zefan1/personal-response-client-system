package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class WecomRequestDeadlineTest {

  @Test
  void requiresAPositiveTimeout() {
    MutableTicker ticker = new MutableTicker(10L);

    assertThatThrownBy(() -> WecomRequestDeadline.start(null, "get_fields", ticker))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("timeout must be positive");
    assertThatThrownBy(() -> WecomRequestDeadline.start(Duration.ZERO, "get_fields", ticker))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("timeout must be positive");
    assertThatThrownBy(() -> WecomRequestDeadline.start(Duration.ofNanos(-1), "get_fields", ticker))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("timeout must be positive");
  }

  @Test
  void saturatesTimeoutNanosAndDecreasesFromOneMonotonicStart() {
    MutableTicker ticker = new MutableTicker(42L);
    WecomRequestDeadline deadline = WecomRequestDeadline.start(
        Duration.ofSeconds(Long.MAX_VALUE), "get_fields", ticker);

    assertThat(deadline.remaining()).isEqualTo(Duration.ofNanos(Long.MAX_VALUE));
    ticker.advance(7L);
    assertThat(deadline.remaining()).isEqualTo(Duration.ofNanos(Long.MAX_VALUE - 7L));
  }

  @Test
  void handlesNanoTimeWrapAndNeverReturnsAnExpiredDuration() {
    MutableTicker ticker = new MutableTicker(Long.MAX_VALUE - 3L);
    WecomRequestDeadline deadline = WecomRequestDeadline.start(Duration.ofNanos(10L), "gettoken", ticker);

    ticker.advance(7L);
    assertThat(deadline.remaining()).isEqualTo(Duration.ofNanos(3L));
    ticker.advance(3L);
    assertThatThrownBy(deadline::remaining)
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("gettoken")
        .hasMessageContaining("timeout expired");
  }

  private static final class MutableTicker implements LongSupplier {
    private long value;

    private MutableTicker(long value) {
      this.value = value;
    }

    void advance(long nanos) {
      value += nanos;
    }

    @Override
    public long getAsLong() {
      return value;
    }
  }
}
