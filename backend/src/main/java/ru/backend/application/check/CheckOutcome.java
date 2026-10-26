package ru.backend.application.check;

import ru.backend.application.entity.enums.CheckStatus;

import java.util.Map;

public record CheckOutcome(CheckStatus status, Map<String, Object> payload) {

}
