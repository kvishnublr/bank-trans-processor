package com.luxoft.bank.domain;

import java.util.Optional;

public interface AccountRepository {

    /** Stores a new account. Fails if an account with the same id already exists. */
    void add(Account account);

    Optional<Account> findById(String id);
}
