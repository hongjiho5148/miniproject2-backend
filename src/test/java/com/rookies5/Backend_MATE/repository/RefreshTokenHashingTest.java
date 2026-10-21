package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.entity.RefreshToken;
import com.rookies5.Backend_MATE.security.TokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// 리프레시 토큰이 원문이 아닌 SHA-256 해시로 저장/조회되는지 검증한다.
@DataJpaTest
@Import(QuerydslConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class RefreshTokenHashingTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("같은 토큰을 해싱하면 항상 같은 값이 나온다 (조회 가능성 보장)")
    void sha256IsDeterministic() {
        String rawToken = "sample.jwt.refresh-token";

        assertThat(TokenHasher.sha256(rawToken)).isEqualTo(TokenHasher.sha256(rawToken));
    }

    @Test
    @DisplayName("해시값은 원문 토큰과 다르며, 원문으로는 되돌릴 수 없다")
    void sha256DoesNotExposeRawToken() {
        String rawToken = "sample.jwt.refresh-token";

        String hash = TokenHasher.sha256(rawToken);

        assertThat(hash).isNotEqualTo(rawToken);
        assertThat(hash).hasSize(64); // SHA-256 hex 문자열 길이
    }

    @Test
    @DisplayName("DB에는 해시값만 저장되어, 해시로는 조회되지만 원문 토큰으로는 조회되지 않는다")
    void onlyHashIsQueryableFromDb() {
        String rawToken = "sample.jwt.refresh-token";
        String hash = TokenHasher.sha256(rawToken);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(1L)
                .tokenHash(hash)
                .build());

        assertThat(refreshTokenRepository.findByTokenHash(hash)).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(rawToken)).isEmpty();
    }
}
