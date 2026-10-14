package com.wayfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.wayfare.domain.Account;
import com.wayfare.domain.AccountRole;
import com.wayfare.dto.RegisterRequest;
import com.wayfare.exception.RoleNotSelfServiceException;
import com.wayfare.repository.AccountRepository;

class AdminProvisioningTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void publicRegistrationRefusesAdminRole() {
        var auth = new AuthService(accounts, encoder, mock(JwtService.class), mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> auth.register(new RegisterRequest("a@b.c", "password1", AccountRole.ADMIN)))
                .isInstanceOf(RoleNotSelfServiceException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    void bootstrapCreatesAdminOnce() {
        when(encoder.encode("password1")).thenReturn("hash");
        when(accounts.existsByEmail("root@wayfare.test")).thenReturn(false);
        bootstrap("root@wayfare.test", "password1").run(new DefaultApplicationArguments());

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void bootstrapNeverTouchesAnExistingAccount() {
        when(accounts.existsByEmail("rider@wayfare.test")).thenReturn(true);
        bootstrap("rider@wayfare.test", "password1").run(new DefaultApplicationArguments());

        verify(accounts, never()).save(any());
    }

    @Test
    void bootstrapIsSkippedWithoutCredentialsOrWithAShortPassword() {
        bootstrap("", "").run(new DefaultApplicationArguments());
        bootstrap("root@wayfare.test", "short").run(new DefaultApplicationArguments());

        verify(accounts, never()).save(any());
    }

    private AdminBootstrap bootstrap(String email, String password) {
        var b = new AdminBootstrap(accounts, encoder);
        ReflectionTestUtils.setField(b, "email", email);
        ReflectionTestUtils.setField(b, "password", password);
        return b;
    }
}
