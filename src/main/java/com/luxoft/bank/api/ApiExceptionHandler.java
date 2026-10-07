package com.luxoft.bank.api;

import com.luxoft.bank.domain.AccountNotFoundException;
import com.luxoft.bank.domain.InsufficientFundsException;
import com.luxoft.bank.domain.InvalidAmountException;
import com.luxoft.bank.domain.InvalidTransferException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Maps domain failures to RFC 7807 problem responses.
 * 400: the request itself is malformed. 404: an account doesn't exist.
 * 422: the request is well-formed but breaks a business rule.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidAmountException.class)
    ProblemDetail invalidAmount(InvalidAmountException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid amount", e);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", e);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", e);
    }

    @ExceptionHandler(InvalidTransferException.class)
    ProblemDetail invalidTransfer(InvalidTransferException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid transfer", e);
    }

    /** Bean validation failures: tell the caller which fields are wrong, not just that something is. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
