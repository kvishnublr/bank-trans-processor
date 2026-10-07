package com.luxoft.bank.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AmountRequest(@NotNull BigDecimal amount) {
}
