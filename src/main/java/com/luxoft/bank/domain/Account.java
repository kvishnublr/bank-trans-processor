package com.luxoft.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An account guards its own balance and ledger: every public method is synchronized on the account.
 * Operations spanning two accounts (transfers) must hold both monitors; see BankingService.
 */
public class Account {

    private final String id;
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);
    private final List<LedgerEntry> ledger = new ArrayList<>();

    public Account(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id is required");
        }
        this.id = id;
    }

    public String id() {
        return id;
    }

    public synchronized BigDecimal balance() {
        return balance;
    }

    /** Oldest first. A copy, so callers cannot rewrite history. */
    public synchronized List<LedgerEntry> history() {
        return List.copyOf(ledger);
    }

    public synchronized LedgerEntry deposit(Amount amount, Instant at) {
        return credit(TransactionType.DEPOSIT, amount, newTransactionId(), at, null);
    }

    public synchronized LedgerEntry withdraw(Amount amount, Instant at) {
        return debit(TransactionType.WITHDRAWAL, amount, newTransactionId(), at, null);
    }

    public synchronized LedgerEntry transferOut(Amount amount, String toAccountId, String transactionId, Instant at) {
        return debit(TransactionType.TRANSFER_OUT, amount, transactionId, at, toAccountId);
    }

    public synchronized LedgerEntry transferIn(Amount amount, String fromAccountId, String transactionId, Instant at) {
        return credit(TransactionType.TRANSFER_IN, amount, transactionId, at, fromAccountId);
    }

    private LedgerEntry credit(TransactionType type, Amount amount, String transactionId, Instant at, String counterparty) {
        balance = balance.add(amount.value());
        return record(type, amount, transactionId, at, counterparty);
    }

    private LedgerEntry debit(TransactionType type, Amount amount, String transactionId, Instant at, String counterparty) {
        if (balance.compareTo(amount.value()) < 0) {
            throw new InsufficientFundsException(id, amount);
        }
        balance = balance.subtract(amount.value());
        return record(type, amount, transactionId, at, counterparty);
    }

    private LedgerEntry record(TransactionType type, Amount amount, String transactionId, Instant at, String counterparty) {
        LedgerEntry entry = new LedgerEntry(transactionId, type, amount.value(), balance, at, counterparty);
        ledger.add(entry);
        return entry;
    }

    private static String newTransactionId() {
        return UUID.randomUUID().toString();
    }
}
