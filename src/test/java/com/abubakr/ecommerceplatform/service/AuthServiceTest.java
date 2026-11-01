package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.JwtProperties;
import com.abubakr.ecommerceplatform.dto.auth.AuthResponse;
import com.abubakr.ecommerceplatform.dto.auth.LoginRequest;
import com.abubakr.ecommerceplatform.dto.auth.RegisterRequest;
import com.abubakr.ecommerceplatform.entity.RefreshToken;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.enums.Role;
import com.abubakr.ecommerceplatform.exception.DuplicateResourceException;
import com.abubakr.ecommerceplatform.exception.InvalidTokenException;
import com.abubakr.ecommerceplatform.repository.RefreshTokenRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import com.abubakr.ecommerceplatform.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;

    private final JwtProperties jwtProperties =
            new JwtProperties("unused", "test", Duration.ofMinutes(15), Duration.ofDays(7));

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, tokenService, jwtProperties);
    }

    @Test
    void register_newEmail_savesNormalizedCustomerWithHashedPasswordAndReturnsTokens() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        stubTokenIssuing();

        AuthResponse response = authService.register(
                new RegisterRequest("  Jane@Example.COM ", "Secret123!", " Jane ", "Doe"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPassword()).isEqualTo("bcrypt-hash");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void register_storesOnlyTheHashOfTheRefreshToken() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));
        stubTokenIssuing();

        authService.register(new RegisterRequest("jane@example.com", "Secret123!", "Jane", "Doe"));

        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(stored.capture());
        assertThat(stored.getValue().getTokenHash()).isEqualTo("hashed-refresh");
        assertThat(stored.getValue().isRevoked()).isFalse();
    }

    @Test
    void register_existingEmail_throwsConflictAndSavesNothing() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "Secret123!", "Jane", "Doe")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        User user = withId(User.builder().email("jane@example.com").password("bcrypt-hash").build(), 1L);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_unknownEmail_stillRunsPasswordCheckAndGivesSameError() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches(eq("whatever"), eq("dummy-hash"));
    }

    @Test
    void refresh_validToken_revokesOldTokenAndIssuesNewPair() {
        RefreshToken stored = refreshToken(false, Instant.now().plusSeconds(3600));
        when(tokenService.hash("old-raw")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(stored));
        stubTokenIssuing();

        AuthResponse response = authService.refresh("old-raw");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
    }

    @Test
    void refresh_alreadyUsedToken_signsOutAllSessionsOfThatUser() {
        RefreshToken stored = refreshToken(true, Instant.now().plusSeconds(3600));
        when(tokenService.hash("stolen-raw")).thenReturn("stolen-hash");
        when(refreshTokenRepository.findByTokenHash("stolen-hash")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("stolen-raw"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository).revokeAllByUserId(42L);
        verify(tokenService, never()).createAccessToken(any());
    }

    @Test
    void refresh_expiredToken_throws() {
        RefreshToken stored = refreshToken(false, Instant.now().minusSeconds(1));
        when(tokenService.hash("expired-raw")).thenReturn("expired-hash");
        when(refreshTokenRepository.findByTokenHash("expired-hash")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("expired-raw"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Refresh token has expired");
    }

    @Test
    void logout_deletesTokenInsteadOfRevoking_soLaterUseIsNotTreatedAsTheft() {
        RefreshToken stored = refreshToken(false, Instant.now().plusSeconds(3600));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        authService.logout("raw");

        verify(refreshTokenRepository).delete(stored);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    private void stubTokenIssuing() {
        when(tokenService.generateOpaqueToken()).thenReturn("raw-refresh");
        when(tokenService.hash("raw-refresh")).thenReturn("hashed-refresh");
        when(tokenService.createAccessToken(any(User.class))).thenReturn("access-token");
    }

    private RefreshToken refreshToken(boolean revoked, Instant expiresAt) {
        User owner = withId(User.builder().email("jane@example.com").role(Role.CUSTOMER).build(), 42L);
        return RefreshToken.builder().user(owner).tokenHash("any").revoked(revoked).expiresAt(expiresAt).build();
    }

    private static User withId(User user, Long id) {
        user.setId(id);
        return user;
    }
}
