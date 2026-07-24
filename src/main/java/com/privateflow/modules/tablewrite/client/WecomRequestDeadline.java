package com.privateflow.modules.tablewrite.client;

import java.time.Duration;
import java.util.function.LongSupplier;

final class WecomRequestDeadline {

  private final String operation;
  private final LongSupplier ticker;
  private final long startedNanos;
  private final long timeoutNanos;

  private WecomRequestDeadline(
      String operation,
      LongSupplier ticker,
      long startedNanos,
      long timeoutNanos) {
    this.operation = operation;
    this.ticker = ticker;
    this.startedNanos = startedNanos;
    this.timeoutNanos = timeoutNanos;
  }

  static WecomRequestDeadline start(Duration timeout, String operation) {
    return start(timeout, operation, System::nanoTime);
  }

  static WecomRequestDeadline start(Duration timeout, String operation, LongSupplier ticker) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw failure(operation, "request timeout must be positive");
    }
    long timeoutNanos;
    try {
      timeoutNanos = timeout.toNanos();
    } catch (ArithmeticException ex) {
      timeoutNanos = Long.MAX_VALUE;
    }
    return new WecomRequestDeadline(operation, ticker, ticker.getAsLong(), timeoutNanos);
  }

  Duration remaining() {
    long elapsedNanos = ticker.getAsLong() - startedNanos;
    if (elapsedNanos < 0 || elapsedNanos >= timeoutNanos) {
      throw failure(operation, "request timeout expired");
    }
    return Duration.ofNanos(timeoutNanos - elapsedNanos);
  }

  private static WecomSmartSheetException failure(String operation, String message) {
    return new WecomSmartSheetException(operation, message, null);
  }
}
