package com.lookupstock.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.lookupstock.backend.model.UserRole;
import com.lookupstock.backend.dto.LoginResponse;
import com.lookupstock.backend.exception.ConflictException;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.AuthToken;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AuthTokenRepository;
import com.lookupstock.backend.repository.UserRepository;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private UserRepository userRepository;
    private AuthTokenRepository authTokenRepository;
    private PasswordEncoder passwordEncoder;
    private UsageEventRecorder usageEventRecorder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authTokenRepository = mock(AuthTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        usageEventRecorder = mock(UsageEventRecorder.class);
        service = new AuthService(userRepository, authTokenRepository, passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC), usageEventRecorder);

        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("geheim123")).thenReturn("HASH");
    }

    @Test
    void register_speichertNurDenHash_NF03() {
        service.register("anna@example.com", "geheim123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASH").isNotEqualTo("geheim123");
        verify(usageEventRecorder).record(UsageEventType.REGISTRATION);
    }

    @Test
    void register_normalisiertEmail() {
        User user = service.register("  Anna@Example.COM ", "geheim123");
        assertThat(user.getEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void register_vorhandeneEmail_wirftConflict() {
        when(userRepository.existsByEmail("anna@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register("anna@example.com", "geheim123"))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
        verify(usageEventRecorder, never()).record(any());
    }

    @Test
    void register_passwortMit7Zeichen_wirdAbgelehnt() {
        assertThatThrownBy(() -> service.register("anna@example.com", "1234567"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void register_passwortMit8Zeichen_wirdAkzeptiert() {
        when(passwordEncoder.encode("12345678")).thenReturn("HASH");
        assertThatCode(() -> service.register("anna@example.com", "12345678"))
                .doesNotThrowAnyException();
    }

    @Test
    void register_passwortUeber72Byte_wirdAbgelehnt() {
        assertThatThrownBy(() -> service.register("anna@example.com", "a".repeat(73)))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "anna", "anna@", "anna@example", "an na@example.com"})
    void register_ungueltigeEmail_wirdAbgelehnt(String email) {
        assertThatThrownBy(() -> service.register(email, "geheim123"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void login_erfolgreich_liefertTokenMit24StundenGueltigkeit() {
        User user = new User("anna@example.com", "HASH");
        when(userRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("geheim123", "HASH")).thenReturn(true);

        LoginResponse response = service.login("Anna@Example.com", "geheim123");

        assertThat(response.token()).hasSize(43);
        assertThat(AuthService.TOKEN_VALIDITY).isEqualTo(Duration.ofHours(24));
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(AuthService.TOKEN_VALIDITY));
        assertThat(response.role()).isEqualTo(UserRole.USER);
        verify(authTokenRepository).save(any(AuthToken.class));
        verify(usageEventRecorder).record(UsageEventType.LOGIN);
    }

    @Test
    void login_falschesPasswort_und_unbekannteEmail_gleicheMeldung() {
        User user = new User("anna@example.com", "HASH");
        when(userRepository.findByEmail("anna@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("falsch", "HASH")).thenReturn(false);
        when(userRepository.findByEmail("niemand@example.com")).thenReturn(Optional.empty());

        Throwable falschesPasswort = catchThrowable(() -> service.login("anna@example.com", "falsch"));
        Throwable unbekannt = catchThrowable(() -> service.login("niemand@example.com", "geheim123"));

        assertThat(falschesPasswort).isInstanceOf(UnauthorizedException.class);
        assertThat(unbekannt).isInstanceOf(UnauthorizedException.class);
        assertThat(falschesPasswort.getMessage()).isEqualTo(unbekannt.getMessage());
        verify(authTokenRepository, never()).save(any());
        verify(usageEventRecorder, never()).record(any());
    }

    @Test
    void authenticate_gueltigesToken_liefertNutzer() {
        User user = new User("anna@example.com", "HASH");
        when(authTokenRepository.findByToken("abc"))
                .thenReturn(Optional.of(new AuthToken("abc", user, NOW.plusSeconds(1))));

        assertThat(service.authenticate("abc")).isSameAs(user);
    }

    @Test
    void authenticate_genauAufAblaufzeit_istAbgelaufen() {
        User user = new User("anna@example.com", "HASH");
        when(authTokenRepository.findByToken("abc"))
                .thenReturn(Optional.of(new AuthToken("abc", user, NOW)));

        assertThatThrownBy(() -> service.authenticate("abc"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authenticate_unbekanntesToken_wirftUnauthorized() {
        when(authTokenRepository.findByToken("xyz")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("xyz"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authenticate_ohneToken_wirftUnauthorized() {
        assertThatThrownBy(() -> service.authenticate(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_loeschtToken() {
        service.logout("abc");
        verify(authTokenRepository).deleteByToken("abc");
    }

    @Test
    void logout_ohneToken_wirftKeinenFehler() {
        assertThatCode(() -> service.logout(null)).doesNotThrowAnyException();
        verify(authTokenRepository, never()).deleteByToken(any());
    }

    @Test
    void register_beideFelderUngueltig_nenntBeideFelder() {
        Throwable t = catchThrowable(() -> service.register("anna", "kurz"));

        assertThat(t).isInstanceOf(ValidationException.class);
        ValidationException e = (ValidationException) t;
        assertThat(e.getErrors())
                .extracting(ValidationException.FieldError::field)
                .containsExactly("email", "password");
        verify(userRepository, never()).saveAndFlush(any());
    }

}
