package com.luxoft.bank.service;

import com.luxoft.bank.domain.Account;
import com.luxoft.bank.domain.AccountNotFoundException;
import com.luxoft.bank.domain.AccountRepository;
import com.luxoft.bank.domain.Amount;
import com.luxoft.bank.domain.InvalidTransferException;
import com.luxoft.bank.domain.LedgerEntry;
import com.luxoft.bank.domain.TransferReceipt;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BankingService {

    private final AccountRepository accounts;
    private final Clock clock;

    public BankingService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    public Account openAccount() {
        Account account = new Account(UUID.randomUUID().toString());
        accounts.add(account);
        return account;
    }

    public Account getAccount(String accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<LedgerEntry> history(String accountId) {
        return getAccount(accountId).history();
    }

    public LedgerEntry deposit(String accountId, Amount amount) {
        return getAccount(accountId).deposit(amount, clock.instant());
    }

    public LedgerEntry withdraw(String accountId, Amount amount) {
        return getAccount(accountId).withdraw(amount, clock.instant());
    }

    public TransferReceipt transfer(String fromAccountId, String toAccountId, Amount amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }
        Account from = getAccount(fromAccountId);
        Account to = getAccount(toAccountId);

        // Always lock in id order, so A->B and B->A running together can't each hold one lock and wait forever.
        Account firstLock = from.id().compareTo(to.id()) < 0 ? from : to;
        Account secondLock = firstLock == from ? to : from;

        synchronized (firstLock) {
            synchronized (secondLock) {
                String transactionId = UUID.randomUUID().toString();
                Instant now = clock.instant();

                // Debit first: if the source can't cover it, this throws and nothing has changed.
                from.transferOut(amount, toAccountId, transactionId, now);
                to.transferIn(amount, fromAccountId, transactionId, now);

                return new TransferReceipt(transactionId, fromAccountId, toAccountId, amount.value(), now);
            }
        }
    }
}
