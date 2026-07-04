package com.wayfare.dto;

import com.wayfare.domain.Account;
import com.wayfare.domain.AccountRole;

import java.util.UUID;

public record AccountResponse(UUID id, String email, AccountRole role) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getRole());
    }
}
