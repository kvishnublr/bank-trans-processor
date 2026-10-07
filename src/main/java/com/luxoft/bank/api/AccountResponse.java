package com.luxoft.bank.api;

import com.luxoft.bank.domain.Account;

import java.math.BigDecimal;

public record AccountResponse(String id, BigDecimal balance) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.balance());
    }
}
