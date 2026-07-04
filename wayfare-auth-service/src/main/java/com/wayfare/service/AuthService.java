package com.wayfare.service;

import com.wayfare.domain.Account;
import com.wayfare.domain.AccountStatus;
import com.wayfare.dto.LoginRequest;
import com.wayfare.dto.RegisterRequest;
import com.wayfare.exception.EmailAlreadyRegisteredException;
import com.wayfare.exception.InvalidCredentialsException;
import com.wayfare.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public Account register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }
        Account account = new Account(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role());
        return accountRepository.save(account);
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
}
