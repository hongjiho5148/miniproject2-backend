package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.Position;
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.repository.PasswordResetTokenRepository;
import com.rookies5.Backend_MATE.repository.RefreshTokenRepository;
import com.rookies5.Backend_MATE.repository.UserRepository;
import com.rookies5.Backend_MATE.security.JwtTokenProvider;
import com.rookies5.Backend_MATE.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 로그인 연속 실패 시 계정이 잠기는지 검증한다.
// 실패 기록은 로그인 트랜잭션이 롤백돼도 남아야 하므로(REQUIRES_NEW), 테스트 트랜잭션 없이 실제 커밋되는 DB로 확인한다.
@DataJpaTest
@Import({QuerydslConfig.class, LoginAttemptService.class, LoginLockoutTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class LoginLockoutTest {

    @TestConfiguration
    static class Config {
        @Bean AuthenticationManager authenticationManager() { return Mockito.mock(AuthenticationManager.class); }
        @Bean JwtTokenProvider jwtTokenProvider() { return Mockito.mock(JwtTokenProvider.class); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean AuthServiceImpl authService(UserRepository users, PasswordEncoder encoder, AuthenticationManager manager,
                                          JwtTokenProvider jwt, RefreshTokenRepository refresh,
                                          PasswordResetTokenRepository reset, LoginAttemptService attempts) {
            return new AuthServiceImpl(users, encoder, manager, jwt, refresh, reset, attempts);
        }
    }

    @Autowired private AuthServiceImpl authService;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(authenticationManager, jwtTokenProvider);
        userRepository.save(User.builder().email("lock@mate.com").password("encoded")
                .nickname("잠금").phoneNumber("01011112222").position(Position.BE).build());
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void failLogin(String email) {
        assertThatThrownBy(() -> authService.login(email, "wrong"))
                .isInstanceOf(BusinessException.class);
    }

    private ErrorCode errorOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("5회 연속 실패하면 계정이 잠기고, 이후에는 인증 시도 자체를 하지 않고 거부한다")
    void accountIsLockedAfterMaxFailures() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> authService.login("lock@mate.com", "wrong"))
                    .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }

        assertThatThrownBy(() -> authService.login("lock@mate.com", "wrong"))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));

        User locked = userRepository.findByEmail("lock@mate.com").orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(LoginAttemptService.MAX_ATTEMPTS);
        assertThat(locked.isLocked(LocalDateTime.now())).isTrue();
        verify(authenticationManager, times(LoginAttemptService.MAX_ATTEMPTS)).authenticate(any());
    }

    @Test
    @DisplayName("잠긴 계정은 올바른 비밀번호로도 로그인할 수 없다")
    void lockedAccountRejectsEvenCorrectPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            failLogin("lock@mate.com");
        }

        Mockito.reset(authenticationManager);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("lock@mate.com", null, List.of()));

        assertThatThrownBy(() -> authService.login("lock@mate.com", "correct-password"))
                .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("실패 후 한 번 성공하면 실패 횟수가 초기화된다")
    void successResetsFailureCount() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        failLogin("lock@mate.com");
        failLogin("lock@mate.com");
        assertThat(userRepository.findByEmail("lock@mate.com").orElseThrow().getFailedLoginCount()).isEqualTo(2);

        Authentication ok = new UsernamePasswordAuthenticationToken("lock@mate.com", null, List.of());
        Mockito.reset(authenticationManager);
        when(authenticationManager.authenticate(any())).thenReturn(ok);
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh");

        authService.login("lock@mate.com", "right");

        assertThat(userRepository.findByEmail("lock@mate.com").orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 이메일은 실패를 기록하지 않고 일반 인증 실패로만 응답한다 (계정 존재 여부 비노출)")
    void unknownEmailIsNotRecordedOrLocked() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS + 2; i++) {
            assertThatThrownBy(() -> authService.login("nobody@mate.com", "wrong"))
                    .satisfies(e -> assertThat(errorOf(e)).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
    }

    @Test
    @DisplayName("잠금 시간이 지난 뒤의 첫 실패는 이전 횟수를 이어가지 않고 새로 센다")
    void countRestartsAfterLockExpires() {
        User user = User.builder().email("expired@mate.com").password("x").nickname("만료")
                .phoneNumber("01033334444").position(Position.BE).build();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            user.recordLoginFailure(base, LoginAttemptService.MAX_ATTEMPTS, Duration.ofMinutes(15));
        }
        assertThat(user.isLocked(base.plusMinutes(1))).isTrue();

        LocalDateTime afterLock = base.plusMinutes(16);
        user.recordLoginFailure(afterLock, LoginAttemptService.MAX_ATTEMPTS, Duration.ofMinutes(15));

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.isLocked(afterLock)).isFalse();
    }
}
