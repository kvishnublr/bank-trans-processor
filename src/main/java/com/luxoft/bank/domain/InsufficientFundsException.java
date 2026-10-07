package com.luxoft.bank.domain;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId, Amount requested) {
        super("Account " + accountId + " has insufficient funds for " + requested);
    }
}
