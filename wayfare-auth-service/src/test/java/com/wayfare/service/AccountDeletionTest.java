package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.wayfare.domain.Account;
import com.wayfare.domain.AccountRole;
import com.wayfare.domain.AccountStatus;
import com.wayfare.dto.DeleteAccountRequest;
import com.wayfare.event.AccountDeletedEvent;
import com.wayfare.exception.InvalidCredentialsException;
import com.wayfare.exception.RoleNotSelfServiceException;
import com.wayfare.repository.AccountRepository;

class AccountDeletionTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthService auth = new AuthService(accounts, encoder, mock(JwtService.class), events);

    private Account account(AccountRole role) {
        Account a = new Account("sam@wayfare.test", "hash", role);
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        when(accounts.findByEmail("sam@wayfare.test")).thenReturn(Optional.of(a));
        when(encoder.matches("password1", "hash")).thenReturn(true);
        when(encoder.encode(any())).thenReturn("unusable");
        return a;
    }

    @Test
    void deletesAccountAndPublishesEvent() {
        Account a = account(AccountRole.RIDER);

        auth.deleteAccount(new DeleteAccountRequest("sam@wayfare.test", "password1"));

        assertThat(a.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(a.getEmail()).isEqualTo("deleted-" + a.getId() + "@wayfare.invalid");
        assertThat(a.getPasswordHash()).isEqualTo("unusable");
        verify(accounts).save(a);
        ArgumentCaptor<AccountDeletedEvent> event = ArgumentCaptor.forClass(AccountDeletedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().getAccountId()).isEqualTo(a.getId().toString());
        assertThat(event.getValue().getRole()).isEqualTo("RIDER");
        assertThat(event.getValue().hasDeletedAt()).isTrue();
    }

    @Test
    void wrongPasswordDeletesNothing() {
        Account a = account(AccountRole.RIDER);

        assertThatThrownBy(() -> auth.deleteAccount(new DeleteAccountRequest("sam@wayfare.test", "nope")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(a.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownEmailLooksLikeBadCredentials() {
        when(accounts.findByEmail("x@y.z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.deleteAccount(new DeleteAccountRequest("x@y.z", "password1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void alreadyDeletedAccountCannotBeDeletedAgain() {
        Account a = account(AccountRole.DRIVER);
        a.markDeleted("x");
        when(accounts.findByEmail("sam@wayfare.test")).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> auth.deleteAccount(new DeleteAccountRequest("sam@wayfare.test", "password1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void adminCannotBeDeletedThisWay() {
        account(AccountRole.ADMIN);

        assertThatThrownBy(() -> auth.deleteAccount(new DeleteAccountRequest("sam@wayfare.test", "password1")))
                .isInstanceOf(RoleNotSelfServiceException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }
}
