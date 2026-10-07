package com.luxoft.bank.api;

import com.luxoft.bank.domain.Account;
import com.luxoft.bank.domain.Amount;
import com.luxoft.bank.domain.LedgerEntry;
import com.luxoft.bank.domain.TransferReceipt;
import com.luxoft.bank.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class AccountController {

    private final BankingService bank;

    public AccountController(BankingService bank) {
        this.bank = bank;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> openAccount() {
        Account account = bank.openAccount();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        return AccountResponse.from(bank.getAccount(accountId));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<LedgerEntry> history(@PathVariable String accountId) {
        return bank.history(accountId);
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public LedgerEntry deposit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        return bank.deposit(accountId, Amount.of(request.amount()));
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    public LedgerEntry withdraw(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        return bank.withdraw(accountId, Amount.of(request.amount()));
    }

    @PostMapping("/transfers")
    public TransferReceipt transfer(@Valid @RequestBody TransferRequest request) {
        return bank.transfer(request.fromAccountId(), request.toAccountId(), Amount.of(request.amount()));
    }
}
