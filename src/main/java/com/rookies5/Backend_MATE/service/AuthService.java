package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.dto.request.UserRequestDto;
import com.rookies5.Backend_MATE.dto.response.AuthResponseDto;
import com.rookies5.Backend_MATE.dto.response.PasswordResetTokenResponseDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;

public interface AuthService {

    /**
     * 1. 회원가입
     */
    UserResponseDto register(UserRequestDto requestDto);

    /**
     * 2. 로그인
     */
    AuthResponseDto login(String email, String password);

    /**
     * 3. 이메일 중복 및 유효성 확인
     */
    boolean isEmailAvailable(String email);

    /**
     * 4. 전화번호 중복 및 유효성 확인 (가입/수정 공용)
     */
    boolean isPhoneAvailable(String phoneNumber, Long userId);

    /**
     * 5. 닉네임 중복 및 유효성 확인 (가입/수정 공용)
     */
    boolean isNicknameAvailable(String nickname, Long userId);

    /**
     * 6. 아이디(이메일) 찾기
     */
    String findEmailByPhoneNumber(String phoneNumber);

    /**
     * 7-1. 비밀번호 재설정 1단계: 본인 확인 후 1회용 재설정 토큰 발급
     */
    PasswordResetTokenResponseDto requestPasswordReset(String email, String phoneNumber);

    /**
     * 7-2. 비밀번호 재설정 2단계: 토큰을 검증하고 사용자가 정한 새 비밀번호로 변경
     */
    void confirmPasswordReset(String token, String newPassword);

    /**
     * 8. 로그아웃
     * ✅ userId → email 로 변경 (Controller에서 DB 조회 제거)
     */
    void logout(String email);

    /**
     * 9. 토큰 재발급
     */
    AuthResponseDto refresh(String refreshToken);
}
