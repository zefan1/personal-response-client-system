package com.privateflow.modules.arrival;

/** Result of matching an appointment against its one human-completion task. */
public record ArrivalHandoverTaskDecision(long taskId, boolean completedDuplicate) {}
