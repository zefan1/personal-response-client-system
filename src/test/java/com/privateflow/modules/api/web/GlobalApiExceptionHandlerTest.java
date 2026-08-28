package com.privateflow.modules.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalApiExceptionHandlerTest {

  @Test
  void profileVersionConflictIsReturnedAsAChineseConflictResponse() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConflictController())
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();

    mockMvc.perform(get("/profile-conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value(ProfileErrorCodes.VERSION_CONFLICT))
        .andExpect(jsonPath("$.message").value("档案已被更新，请刷新后重试"));
  }

  @RestController
  static class ConflictController {
    @GetMapping("/profile-conflict")
    void conflict() {
      throw new ProfileUpdateException(ProfileErrorCodes.VERSION_CONFLICT, "档案已被更新，请刷新后重试");
    }
  }
}
