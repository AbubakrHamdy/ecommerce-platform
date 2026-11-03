package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.PasswordResetProperties;
import com.abubakr.ecommerceplatform.entity.PasswordResetToken;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.event.PasswordResetRequested;
import com.abubakr.ecommerceplatform.exception.InvalidTokenException;
import com.abubakr.ecommerceplatform.repository.PasswordResetTokenRepository;
import com.abubakr.ecommerceplatform.repository.RefreshTokenRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import com.abubakr.ecommerceplatform.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private ApplicationEventPublisher events;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties =
                new PasswordResetProperties(Duration.ofMinutes(30), "http://localhost:4200/reset-password");
        service = new PasswordResetService(userRepository, resetTokenRepository, refreshTokenRepository,
                passwordEncoder, tokenService, events, properties);
    }

    @Test
    void requestReset_unknownEmail_sendsNothingAndRevealsNothing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("Ghost@Example.com");

        verifyNoInteractions(events, resetTokenRepository);
    }

    @Test
    void requestReset_knownEmail_invalidatesOldLinksAndEmailsANewOne() {
        User user = user();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(tokenService.generateOpaqueToken()).thenReturn("raw-reset");
        when(tokenService.hash("raw-reset")).thenReturn("reset-hash");

        service.requestReset("jane@example.com");

        verify(resetTokenRepository).invalidateAllByUserId(7L);
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(events).publishEvent(new PasswordResetRequested("jane@example.com", "Jane",
                "http://localhost:4200/reset-password?token=raw-reset", Duration.ofMinutes(30)));
    }

    @Test
    void resetPassword_validToken_updatesPasswordBurnsTokenAndSignsOutEverywhere() {
        User user = user();
        PasswordResetToken token = resetToken(user, false, Instant.now().plusSeconds(600));
        when(tokenService.hash("raw-reset")).thenReturn("reset-hash");
        when(resetTokenRepository.findByTokenHash("reset-hash")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("new-bcrypt-hash");

        service.resetPassword("raw-reset", "NewSecret123!");

        assertThat(user.getPassword()).isEqualTo("new-bcrypt-hash");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllByUserId(7L);
    }

    @Test
    void resetPassword_alreadyUsedToken_isRejected() {
        PasswordResetToken token = resetToken(user(), true, Instant.now().plusSeconds(600));
        when(tokenService.hash("raw-reset")).thenReturn("reset-hash");
        when(resetTokenRepository.findByTokenHash("reset-hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw-reset", "NewSecret123!"))
                .isInstanceOf(InvalidTokenException.class);

        verify(passwordEncoder, never()).encode(anyString());
    }

    private static User user() {
        User user = User.builder().email("jane@example.com").firstName("Jane").password("old-hash").build();
        user.setId(7L);
        return user;
    }

    private static PasswordResetToken resetToken(User user, boolean used, Instant expiresAt) {
        return PasswordResetToken.builder().user(user).tokenHash("reset-hash").used(used).expiresAt(expiresAt).build();
    }
}
