package com.rookies5.Backend_MATE.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 유저의 토큰인지 식별 (1인 1기기 로그인을 가정하여 unique = true 설정)
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // DB 유출 시 토큰이 그대로 재사용되는 것을 막기 위해 원문이 아닌 SHA-256 해시값을 저장 (SHA-256 hex = 64자)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Builder
    public RefreshToken(Long userId, String tokenHash) {
        this.userId = userId;
        this.tokenHash = tokenHash;
    }

    // 기존 토큰을 새로운 토큰으로 교체할 때 사용하는 메서드 (더티 체킹용)
    public void updateTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}
