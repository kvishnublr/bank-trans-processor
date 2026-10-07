package com.luxoft.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One immutable line in an account's ledger.
 *
 * @param transactionId         shared by both sides of a transfer, so the two entries can be tied together
 * @param balanceAfter          the account balance once this entry was applied
 * @param counterpartyAccountId the other account in a transfer, null for deposits and withdrawals
 */
public record LedgerEntry(
        String transactionId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant timestamp,
        String counterpartyAccountId) {
}
