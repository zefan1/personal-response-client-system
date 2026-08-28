package com.privateflow.modules.arrival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ArrivalHandoverTaskRepositoryTest {

  @Test
  void createsManualTaskWhenHiddenBookingTimeAndItemAreAbsent() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(17L);
    ArrivalHandoverTask task = new ArrivalHandoverTask();
    task.setCustomerId(8L);
    task.setPhone("18800001111");
    task.setAppointmentTime(null);
    task.setAppointmentItem(null);

    long taskId = new ArrivalHandoverTaskRepository(jdbc).createManual(task, "keeper");

    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), values.capture());
    assertThat(taskId).isEqualTo(17L);
    assertThat(values.getValue()[4]).isEqualTo("");
    assertThat(values.getValue()[6]).isEqualTo("");
  }
}
