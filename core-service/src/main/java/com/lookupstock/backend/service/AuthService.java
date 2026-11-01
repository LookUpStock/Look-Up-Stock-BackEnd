package com.lookupstock.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.dto.LoginResponse;
import com.lookupstock.backend.exception.ConflictException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.AuthToken;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AuthTokenRepository;
import com.lookupstock.backend.repository.UserRepository;

@Service
public class AuthService {

    static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_BYTES = 72;                   // BCrypt-Grenze
    static final int MAX_EMAIL_LENGTH = 255;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final UsageEventRecorder usageEventRecorder;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder,
                       Clock clock,
                       UsageEventRecorder usageEventRecorder) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.usageEventRecorder = usageEventRecorder;
    }

    @Transactional
    public User register(String email, String password) {
        String normalized = normalize(email);
        List<ValidationException.FieldError> errors = new ArrayList<>();
        validateEmail(normalized, errors);
        validatePassword(password, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.get(0).message(), errors);
        }

        if (userRepository.existsByEmail(normalized)) {
            throw new ConflictException(Messages.EMAIL_TAKEN);
        }
        User user;
        try {
            // saveAndFlush, damit ein Verstoß gegen UNIQUE(email) hier auffällt
            user = userRepository.saveAndFlush(
                    new User(normalized, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // zwei gleichzeitige Registrierungen mit derselben E-Mail
            throw new ConflictException(Messages.EMAIL_TAKEN);
        }
        // Nur erfolgreiche Registrierungen zählen (E37)
        usageEventRecorder.record(UsageEventType.REGISTRATION);
        return user;
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        if (email == null || password == null) {
            throw new UnauthorizedException(Messages.INVALID_CREDENTIALS);
        }
        User user = userRepository.findByEmail(normalize(email))
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException(Messages.INVALID_CREDENTIALS));

        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.SECONDS).plus(TOKEN_VALIDITY);
        String token = generateToken();
        authTokenRepository.save(new AuthToken(token, user, expiresAt));
        // Nur erfolgreiche Anmeldungen zählen (E37)
        usageEventRecorder.record(UsageEventType.LOGIN);
        return new LoginResponse(token, expiresAt, user.getRole());
    }

    @Transactional
    public void logout(String token) {
        // idempotent: unbekanntes oder abgelaufenes Token ist kein Fehler
        if (token != null && !token.isBlank()) {
            authTokenRepository.deleteByToken(token);
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException(Messages.NOT_LOGGED_IN);
        }
        AuthToken authToken = authTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException(Messages.NOT_LOGGED_IN));
        if (authToken.isExpired(clock.instant())) {
            throw new UnauthorizedException(Messages.NOT_LOGGED_IN);
        }
        return authToken.getUser();
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateEmail(String email, List<ValidationException.FieldError> errors) {
        if (email == null || email.length() > MAX_EMAIL_LENGTH
                || !EMAIL_PATTERN.matcher(email).matches()) {
            errors.add(new ValidationException.FieldError("email", Messages.EMAIL_INVALID));
        }
    }

    private void validatePassword(String password, List<ValidationException.FieldError> errors) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            errors.add(new ValidationException.FieldError("password", Messages.PASSWORD_TOO_SHORT));
        } else if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            errors.add(new ValidationException.FieldError("password", Messages.PASSWORD_TOO_LONG));
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // 43 Zeichen
    }
}