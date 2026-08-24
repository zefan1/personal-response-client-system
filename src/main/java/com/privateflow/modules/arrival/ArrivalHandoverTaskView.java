package com.privateflow.modules.arrival;
import java.time.LocalDate; import java.time.LocalDateTime;
public record ArrivalHandoverTaskView(long id,String phone,String nickname,String assignedKeeper,LocalDate appointmentDate,String appointmentTime,String appointmentStore,String appointmentItem,boolean canComplete,boolean canRemind,LocalDateTime remindedAt) {}
