package com.rookies5.Backend_MATE.service.impl;

import com.rookies5.Backend_MATE.dto.request.UserRequestDto;
import com.rookies5.Backend_MATE.dto.response.AuthResponseDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;
import com.rookies5.Backend_MATE.entity.RefreshToken;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.config.TechStack; // 👈 추가
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.EntityNotFoundException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.mapper.UserMapper;
import com.rookies5.Backend_MATE.repository.RefreshTokenRepository;
import com.rookies5.Backend_MATE.repository.UserRepository;
import com.rookies5.Backend_MATE.security.JwtTokenProvider;
import com.rookies5.Backend_MATE.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    // Security 관련 의존성 추가
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final String DEFAULT_PROFILE_IMG = "https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png";

    /**
     * 1. 신규 회원을 등록(회원가입) - 기본 이미지 자동 할당
     */
    @Override
    public UserResponseDto register(UserRequestDto requestDto) {
        // 중복 체크
        isEmailAvailable(requestDto.getEmail());
        isPhoneAvailable(requestDto.getPhoneNumber(), null);

        // ✅ 기술 스택 유효성 검증 로직 추가 (프론트엔드 목록과 동기화)
        if (requestDto.getTechStacks() != null && !requestDto.getTechStacks().isEmpty()) {
            for (String tech : requestDto.getTechStacks()) {
                if (!TechStack.isValid(tech)) {
                    log.warn("회원가입 시 지원하지 않는 기술 스택 요청: {}", tech);
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 기술 스택이 포함되어 있습니다: " + tech); // 👈 INVALID_REQUEST → VALIDATION_ERROR
                }
            }
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        requestDto.setPassword(encodedPassword);

        // 1. 기존 파일 저장 로직 삭제 및 기본 이미지 할당
        requestDto.setProfileImg(DEFAULT_PROFILE_IMG);
        log.info("회원가입 기본 프로필 이미지 세팅 완료");

        // 2. 닉네임 미입력 시 이메일 기반 자동 할당 (기존 로직 유지)
        if (requestDto.getNickname() == null || requestDto.getNickname().trim().isEmpty()) {
            // 1. 이메일 앞자리 추출
            String defaultNickname = requestDto.getEmail().split("@")[0];

            // 2. 허용된 문자(영문, 숫자, 한글) 외의 특수문자 제거
            defaultNickname = defaultNickname.replaceAll("[^a-zA-Z0-9가-힣]", "");

            // 3. 길이가 10자를 초과하면 10자리까지만 자르기
            if (defaultNickname.length() > 10) {
                defaultNickname = defaultNickname.substring(0, 10);
            }

            // 4. 만약 지우고 났더니 2글자 미만이라면 기본 단어 추가 (예외 방지)
            if (defaultNickname.length() < 2) {
                defaultNickname = defaultNickname + "user";
            }

            requestDto.setNickname(defaultNickname);
            log.info("닉네임 미입력으로 이메일 기반 자동 할당: {}", defaultNickname);
        }

        // Entity 변환 (Mapper에서 profileImg를 꺼내 쓰도록 되어 있어야 함)
        User user = UserMapper.mapToUser(requestDto);

        // 닉네임 중복 체크
        isNicknameAvailable(user.getNickname(), null);

        User savedUser = userRepository.save(user);
        return UserMapper.mapToUserResponse(savedUser);
    }

    /**
     * 2. 로그인 및 토큰 발급 (Security 버전 유지)
     */
    @Override
    @Transactional
    public AuthResponseDto login(String email, String password) {
        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(email, password);
            Authentication authentication = authenticationManager.authenticate(authenticationToken);

            String accessToken = jwtTokenProvider.createAccessToken(authentication);
            String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            // Refresh Token DB 저장 로직
            // 이미 이 유저의 토큰이 있으면 덮어쓰고, 없으면 새로 생성해서 저장합니다.
            refreshTokenRepository.findByUserId(user.getId())
                    .ifPresentOrElse(
                            token -> token.updateToken(refreshToken), // 있으면 값 업데이트 (더티 체킹)
                            () -> refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken)) // 없으면 새로 저장
                    );

            return AuthResponseDto.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600)
                    .user(AuthResponseDto.UserInfo.builder()
                            .id(user.getId())
                            .nickname(user.getNickname())
                            .email(user.getEmail())
                            .position(user.getPosition() != null ? user.getPosition().name() : null)
                            .build())
                    .build();

        } catch (AuthenticationException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }

    /**
     * 3. 이메일 유효성 및 중복 확인 (삭제된 유저 포함)
     */
    @Transactional(readOnly = true)
    @Override
    public boolean isEmailAvailable(String email) {
        // 1. 형식 검사
        String regex = "^[A-Za-z0-9+_.-]+@(.+)$";
        if (email == null || !email.matches(regex)) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        // 2. 전체 DB 중복 검사 (Native Query로 Soft Delete 데이터까지 조회)
        // 💡 int 결과가 0보다 크면 이미 존재하는 이메일입니다.
        if (userRepository.countByEmailIncludingDeleted(email) > 0) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
        }
        return true;
    }

    /**
     * 4. 전화번호 가용성 체크 (삭제된 유저 포함)
     */
    @Transactional(readOnly = true)
    @Override
    public boolean isPhoneAvailable(String phoneNumber, Long userId) {
        if (phoneNumber == null) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        String targetPhone = phoneNumber.trim();
        if (!targetPhone.matches("^\\d{11}$")) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        // 💡 회원가입 시(userId == null)나 정보 수정 시 모두
        // 탈퇴한 유저가 쓰던 번호와 겹치면 DB 제약 조건 에러가 나므로 미리 차단합니다.
        if (userRepository.countByPhoneIncludingDeleted(targetPhone) > 0) {
            throw new BusinessException(ErrorCode.USER_PHONE_DUPLICATE);
        }

        return true;
    }

    /**
     * 5. 닉네임 가용성 체크 (삭제된 유저 포함)
     */
    @Transactional(readOnly = true)
    @Override
    public boolean isNicknameAvailable(String nickname, Long currentUserId) {
        if (nickname == null) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_FORMAT_INVALID);
        }

        String targetNickname = nickname.trim();
        // 2~10자 한글, 영문, 숫자
        if (!targetNickname.matches("^[a-zA-Z0-9가-힣]{2,10}$")) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_FORMAT_INVALID);
        }

        // 💡 전체 DB 중복 검사 (Soft Delete 데이터 포함)
        if (userRepository.countByNicknameIncludingDeleted(targetNickname) > 0) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
        }

        return true;
    }

    /**
     * 6. 아이디(이메일) 찾기 (공통)
     */
    @Transactional(readOnly = true)
    @Override
    public String findEmailByPhoneNumber(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, phoneNumber));
        return user.getEmail();
    }

    /**
     * 7. 비밀번호 찾기 (Controller의 반환값 + Security의 암호화 저장 통합)
     */
    @Override
    public String resetPassword(String email, String phoneNumber) {
        User user = userRepository.findByEmailAndPhoneNumber(email, phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_MATCHED));

        String tempPassword = generateTempPassword();

        // 👇 💡 첫 번째 CCTV: 백엔드가 만든 비밀번호 양옆에 대괄호 [ ] 를 씌워서 기록!
        log.info("🔑 [1. 백엔드 생성] 임시 비밀번호: [{}]", tempPassword);

        // 💡 Security 버전의 필수 로직: DB에 저장할 때는 반드시 암호화!
        user.updatePassword(passwordEncoder.encode(tempPassword));

        // Controller 버전의 필수 로직: 생성된 임시 비밀번호를 화면에 보여주기 위해 평문 반환
        return tempPassword;
    }

    /**
     * 8. 토큰 재발급 로직 (Access Token 만료 시)
     */
    @Override
    @Transactional
    public AuthResponseDto refresh(String refreshToken) {
        // 1. DB에 해당 토큰이 존재하는지 검증
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenValue(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID)); // "유효하지 않은 토큰입니다" 에러

        // 2. JWT 자체의 유효성 검증 (만료일이 지났는지 등)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            // 만료되었다면 DB에서도 지워버림 (다시 로그인하게 유도)
            refreshTokenRepository.delete(tokenEntity);
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED); // "만료된 토큰입니다" 에러
        }

        // 3. 토큰 주인의 정보 찾기
        User user = userRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 새로운 Access Token 발급 (기존 메서드 활용)
        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, null);
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);

        // 5. 결과 반환 (Refresh Token은 그대로 유지하거나, 새로 발급해서 DB 업데이트 할 수도 있음. 여기선 유지하는 방식)
        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600)
                .user(AuthResponseDto.UserInfo.builder()
                        .id(user.getId())
                        .nickname(user.getNickname())
                        .email(user.getEmail())
                        .position(user.getPosition() != null ? user.getPosition().name() : null)
                        .build())
                .build();
    }

    /**
     * 9. 로그아웃 (리프레시 토큰 삭제)
     * ✅ email을 받아서 Service 내부에서 유저를 직접 찾아 처리 (Controller에서 DB 조회 제거)
     */
    @Override
    @Transactional
    public void logout(String email) {
        // ✅ DB 조회 책임을 Service로 이동
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // DB에서 해당 유저의 리프레시 토큰을 삭제하여 더 이상 토큰 갱신을 못하게 막음
        refreshTokenRepository.deleteByUserId(user.getId());
        log.info("유저 ID: {} 로그아웃 및 리프레시 토큰 삭제 완료", user.getId());
    }

    private String generateTempPassword() {
        char[] charSet = new char[]{
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
        };
        StringBuilder tempPw = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int idx = (int) (charSet.length * Math.random());
            tempPw.append(charSet[idx]);
        }
        return tempPw.toString();
    }
}
