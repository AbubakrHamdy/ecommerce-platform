package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.PasswordResetProperties;
import com.abubakr.ecommerceplatform.entity.PasswordResetToken;
import com.abubakr.ecommerceplatform.entity.User;
import com.abubakr.ecommerceplatform.exception.InvalidTokenException;
import com.abubakr.ecommerceplatform.repository.PasswordResetTokenRepository;
import com.abubakr.ecommerceplatform.repository.RefreshTokenRepository;
import com.abubakr.ecommerceplatform.repository.UserRepository;
import com.abubakr.ecommerceplatform.security.TokenService;
import com.abubakr.ecommerceplatform.event.PasswordResetRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ApplicationEventPublisher events;
    private final PasswordResetProperties properties;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(AuthService.normalizeEmail(email)).ifPresent(this::issueResetToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new InvalidTokenException("Reset link is invalid or has expired"));

        token.setUsed(true);
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private void issueResetToken(User user) {
        resetTokenRepository.invalidateAllByUserId(user.getId());

        String rawToken = tokenService.generateOpaqueToken();
        resetTokenRepository.save(PasswordResetToken.builder()
                .tokenHash(tokenService.hash(rawToken))
                .user(user)
                .expiresAt(Instant.now().plus(properties.tokenTtl()))
                .used(false)
                .build());

        String resetLink = properties.resetUrl() + "?token=" + rawToken;
        // Emailed after commit, so the link is never sent for a token that failed to save.
        events.publishEvent(new PasswordResetRequested(user.getEmail(), user.getFirstName(), resetLink, properties.tokenTtl()));
    }
}
