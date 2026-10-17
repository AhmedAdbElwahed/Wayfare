package com.wayfare.service;

import com.wayfare.domain.Account;
import com.wayfare.domain.AccountRole;
import com.wayfare.domain.AccountStatus;
import com.wayfare.dto.DeleteAccountRequest;
import com.wayfare.dto.LoginRequest;
import com.wayfare.dto.RegisterRequest;
import com.wayfare.exception.EmailAlreadyRegisteredException;
import com.wayfare.exception.InvalidCredentialsException;
import com.wayfare.exception.RoleNotSelfServiceException;
import com.wayfare.event.AccountDeletedEvent;
import com.wayfare.event.AccountRegisteredEvent;
import com.wayfare.event.ProtoTimes;
import com.wayfare.repository.AccountRepository;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and credential verification. Kept separate from the web layer
 * so {@link com.wayfare.controller.AuthController} stays a thin adapter and
 * the transactional boundary lives here, around the business operation.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Account register(RegisterRequest request) {
        if (!request.role().isSelfService()) {
            throw new RoleNotSelfServiceException(request.role());
        }
        if (accountRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }
        Account account = new Account(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role());
        Account saved = accountRepository.save(account);
        // Handed to AccountEventPublisher, which holds it until this
        // transaction commits before putting it on the broker.
        eventPublisher.publishEvent(AccountRegisteredEvent.newBuilder()
                .setAccountId(saved.getId().toString())
                .setEmail(saved.getEmail())
                .setRole(saved.getRole().name())
                .setRegisteredAt(ProtoTimes.toTimestamp(saved.getCreatedAt()))
                .build());
        return saved;
    }

    /**
     * Verifies credentials and issues a signed JWT. Fails with the same generic
     * {@link InvalidCredentialsException} whether the email is unknown, the
     * password is wrong, or the account is not active, so callers can't probe
     * which accounts exist.
     */
    @Transactional(readOnly = true)
    public String login(LoginRequest request) {
        Account account = accountRepository.findByEmail(request.email())
                .filter(a -> passwordEncoder.matches(request.password(), a.getPasswordHash()))
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(InvalidCredentialsException::new);
        return jwtService.issueToken(account);
    }

    /**
     * Deletes the caller's own account after re-checking their credentials
     * (same generic failure as login, so nothing about other accounts leaks),
     * then announces it so rider/driver-service erase their data. The event
     * is published after commit, like AccountRegistered — see
     * AccountDeletedEventPublisher for the lost-event caveat.
     */
    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        Account account = accountRepository.findByEmail(request.email())
                .filter(a -> passwordEncoder.matches(request.password(), a.getPasswordHash()))
                .filter(a -> a.getStatus() != AccountStatus.DELETED)
                .orElseThrow(InvalidCredentialsException::new);
        if (!account.getRole().isSelfService()) {
            throw new RoleNotSelfServiceException(account.getRole());
        }
        AccountRole role = account.getRole();
        // A random value nobody knows: the row can never authenticate again.
        account.markDeleted(passwordEncoder.encode(UUID.randomUUID().toString()));
        accountRepository.save(account);
        eventPublisher.publishEvent(AccountDeletedEvent.newBuilder()
                .setAccountId(account.getId().toString())
                .setRole(role.name())
                .setDeletedAt(ProtoTimes.toTimestamp(Instant.now()))
                .build());
    }
}
