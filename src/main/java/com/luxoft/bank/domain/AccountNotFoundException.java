package com.luxoft.bank.domain;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("Account " + accountId + " does not exist");
    }
}
