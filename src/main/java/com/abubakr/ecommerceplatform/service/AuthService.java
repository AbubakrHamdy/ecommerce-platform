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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .role(Role.CUSTOMER)
                .authProvider(LOCAL_PROVIDER)
                .build());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
        // Always run a BCrypt check so unknown emails take as long as wrong passwords (no timing leak).
        String hash = user.map(User::getPassword).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueTokens(user.get());
    }

    // Reuse detection revokes every session; that write must survive the exception thrown right after it.
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            throw new InvalidTokenException("Refresh token was already used; all sessions have been signed out");
        }
        if (stored.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        stored.setRevoked(true);
        return issueTokens(stored.getUser());
    }

    // Deleted rather than revoked: a revoked token coming back means theft, a logged-out one doesn't.
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse issueTokens(User user) {
        String rawRefreshToken = tokenService.generateOpaqueToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(tokenService.hash(rawRefreshToken))
                .user(user)
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .revoked(false)
                .build());
        return new AuthResponse(
                tokenService.createAccessToken(user),
                rawRefreshToken,
                TOKEN_TYPE,
                jwtProperties.accessTokenTtl().toSeconds());
    }
}
