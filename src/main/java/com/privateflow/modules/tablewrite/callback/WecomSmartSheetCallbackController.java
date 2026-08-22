package com.privateflow.modules.tablewrite.callback;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/wecom/smartsheet/callback", produces = MediaType.TEXT_PLAIN_VALUE)
public final class WecomSmartSheetCallbackController {

  private final WecomSmartSheetCallbackService service;

  public WecomSmartSheetCallbackController(WecomSmartSheetCallbackService service) {
    this.service = service;
  }

  @GetMapping
  public String verify(
      @RequestParam("msg_signature") String signature,
      @RequestParam("timestamp") String timestamp,
      @RequestParam("nonce") String nonce,
      @RequestParam("echostr") String encryptedEcho) {
    try {
      return service.verifyChallenge(signature, timestamp, nonce, encryptedEcho);
    } catch (RuntimeException ex) {
      throw new CallbackForbiddenException();
    }
  }

  @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE})
  public String receive(
      @RequestParam("msg_signature") String signature,
      @RequestParam("timestamp") String timestamp,
      @RequestParam("nonce") String nonce,
      @RequestBody String encryptedXml) {
    try {
      service.receive(signature, timestamp, nonce, encryptedXml);
      return "success";
    } catch (RuntimeException ex) {
      throw new CallbackForbiddenException();
    }
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  private static final class CallbackForbiddenException extends RuntimeException {
  }
}
