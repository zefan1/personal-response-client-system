package com.privateflow.modules.api.help;

import java.util.Map;

public record HelpRequestPayload(String phone, String question, Map<String, Object> context) {
}
