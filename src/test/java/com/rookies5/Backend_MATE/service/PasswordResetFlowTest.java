package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.dto.response.PasswordResetTokenResponseDto;
import com.rookies5.Backend_MATE.entity.PasswordResetToken;
import com.rookies5.Backend_MATE.entity.RefreshToken;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.Position;
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.repository.PasswordResetTokenRepository;
import com.rookies5.Backend_MATE.repository.RefreshTokenRepository;
import com.rookies5.Backend_MATE.repository.UserRepository;
import com.rookies5.Backend_MATE.security.TokenHasher;
import com.rookies5.Backend_MATE.service.impl.AuthServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 비밀번호 재설정(토큰 2단계)이 실제 DB 위에서 안전하게 동작하는지 검증한다.
@DataJpaTest
@Import(QuerydslConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class PasswordResetFlowTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private EntityManager em;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthServiceImpl authService;
    private User user;

    @BeforeEach
    void setUp() {
        // 재설정 흐름은 인증 매니저/JWT를 쓰지 않으므로 null로 두고 실제 저장소와 인코더만 연결한다.
        authService = new AuthServiceImpl(userRepository, passwordEncoder, null, null,
                refreshTokenRepository, passwordResetTokenRepository, null);
        user = userRepository.save(User.builder()
                .email("reset@mate.com").password(passwordEncoder.encode("OldPassword1!"))
                .nickname("재설정").phoneNumber("01012345678").position(Position.BE).build());
        em.flush();
    }

    @Test
    @DisplayName("본인 확인에 성공하면 토큰이 발급되고, DB에는 원문이 아닌 해시만 저장된다")
    void tokenIsIssuedAndOnlyHashIsStored() {
        PasswordResetTokenResponseDto response = authService.requestPasswordReset("reset@mate.com", "01012345678");

        assertThat(response.getResetToken()).isNotBlank();
        assertThat(response.getExpiresInSeconds()).isEqualTo(600);
        assertThat(passwordResetTokenRepository.findByTokenHash(response.getResetToken())).isEmpty();
        assertThat(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256(response.getResetToken()))).isPresent();
    }

    @Test
    @DisplayName("이메일과 전화번호가 일치하지 않으면 토큰을 발급하지 않는다")
    void rejectsWhenIdentityDoesNotMatch() {
        assertThatThrownBy(() -> authService.requestPasswordReset("reset@mate.com", "01099999999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_MATCHED));
        assertThat(passwordResetTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("토큰으로 비밀번호를 바꾸면 새 비밀번호가 적용되고, 기존 로그인 세션(리프레시 토큰)이 무효화된다")
    void confirmChangesPasswordAndRevokesSessions() {
        refreshTokenRepository.save(new RefreshToken(user.getId(), TokenHasher.sha256("old-refresh")));
        String token = authService.requestPasswordReset("reset@mate.com", "01012345678").getResetToken();
        em.flush();
        em.clear();

        authService.confirmPasswordReset(token, "NewPassword9!");
        em.flush();
        em.clear();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword9!", reloaded.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword1!", reloaded.getPassword())).isFalse();
        assertThat(refreshTokenRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("토큰은 1회만 사용할 수 있다 (재사용 시 거부)")
    void tokenIsSingleUse() {
        String token = authService.requestPasswordReset("reset@mate.com", "01012345678").getResetToken();
        authService.confirmPasswordReset(token, "NewPassword9!");
        em.flush();
        em.clear();

        assertThatThrownBy(() -> authService.confirmPasswordReset(token, "AnotherPass7!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_RESET_TOKEN_INVALID));
    }

    @Test
    @DisplayName("만료된 토큰은 거부되고, 비밀번호는 바뀌지 않는다")
    void expiredTokenIsRejected() {
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId()).tokenHash(TokenHasher.sha256("expired-token"))
                .expiresAt(LocalDateTime.now().minusSeconds(1)).build());
        em.flush();
        em.clear();

        assertThatThrownBy(() -> authService.confirmPasswordReset("expired-token", "NewPassword9!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_RESET_TOKEN_INVALID));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassword1!", reloaded.getPassword())).isTrue();
    }

    @Test
    @DisplayName("재발급하면 이전에 발급된 토큰은 더 이상 사용할 수 없다")
    void reissuingInvalidatesPreviousToken() {
        String first = authService.requestPasswordReset("reset@mate.com", "01012345678").getResetToken();
        String second = authService.requestPasswordReset("reset@mate.com", "01012345678").getResetToken();
        em.flush();
        em.clear();

        assertThat(first).isNotEqualTo(second);
        assertThatThrownBy(() -> authService.confirmPasswordReset(first, "NewPassword9!"))
                .isInstanceOf(BusinessException.class);
        authService.confirmPasswordReset(second, "NewPassword9!");
    }

    @Test
    @DisplayName("임의의 토큰으로는 비밀번호를 바꿀 수 없다")
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> authService.confirmPasswordReset("not-a-real-token", "NewPassword9!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_RESET_TOKEN_INVALID));
    }
}
