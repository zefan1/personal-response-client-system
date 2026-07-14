package com.privateflow.modules.tags;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TagToggleRequest(@JsonAlias("isEnabled") Boolean enabled, Integer version) {
}
