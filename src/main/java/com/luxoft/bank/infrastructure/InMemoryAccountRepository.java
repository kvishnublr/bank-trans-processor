package com.luxoft.bank.infrastructure;

import com.luxoft.bank.domain.Account;
import com.luxoft.bank.domain.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void add(Account account) {
        Account existing = accounts.putIfAbsent(account.id(), account);
        if (existing != null) {
            throw new IllegalStateException("Account " + account.id() + " already exists");
        }
    }

    @Override
    public Optional<Account> findById(String id) {
        return Optional.ofNullable(accounts.get(id));
    }
}
