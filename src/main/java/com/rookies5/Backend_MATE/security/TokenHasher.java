package com.rookies5.Backend_MATE.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 리프레시 토큰을 DB에 저장하기 전 해싱하기 위한 유틸리티.
 * JWT 문자열 자체가 이미 충분히 높은 엔트로피를 가지므로(HMAC 서명 포함),
 * 비밀번호와 달리 별도 salt 없이 결정적(deterministic)인 SHA-256이면
 * DB 유출 시 토큰 재사용을 막으면서도 조회(lookup)를 그대로 지원할 수 있다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
