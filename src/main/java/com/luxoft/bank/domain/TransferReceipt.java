package com.luxoft.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferReceipt(
        String transactionId,
        String fromAccountId,
        String toAccountId,
        BigDecimal amount,
        Instant timestamp) {
}
