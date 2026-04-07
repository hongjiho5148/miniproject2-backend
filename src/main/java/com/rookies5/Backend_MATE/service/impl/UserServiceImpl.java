package com.rookies5.Backend_MATE.service.impl;

import com.rookies5.Backend_MATE.dto.request.UserRequestDto;
import com.rookies5.Backend_MATE.dto.response.ApplicationResponseDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.config.TechStack; // 👈 추가
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.EntityNotFoundException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.mapper.UserMapper;
import com.rookies5.Backend_MATE.repository.*;
// import com.rookies5.Backend_MATE.repository.ProjectRepository;     // 나중에 추가 시 주석 해제
// import com.rookies5.Backend_MATE.repository.ApplicationRepository; // 나중에 추가 시 주석 해제
import com.rookies5.Backend_MATE.service.ApplicationService;
import com.rookies5.Backend_MATE.service.ProjectService;
import com.rookies5.Backend_MATE.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.rookies5.Backend_MATE.service.CloudinaryService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ApplicationService applicationService;
    private final ProjectRepository projectRepository;
    private final BoardPostRepository boardPostRepository;
    private final CommentRepository commentRepository;
    private final ApplicationRepository applicationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private static final String DEFAULT_PROFILE_IMG = "https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png";

    /**
     * 1. 내 정보 상세 조회
     */
    @Transactional(readOnly = true )
    @Override
    public UserResponseDto getUserById(Long userId) {
        return userRepository.findById(userId)
                .map(UserMapper::mapToUserResponse)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    /**
     * 2. 전체 회원 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    @Override
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserMapper::mapToUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * 3. 회원 정보 수정
     */
    @Override
    public UserResponseDto updateUser(Long userId, UserRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        // 1. 닉네임 변경 시 중복 체크
        if (requestDto.getNickname() != null && !user.getNickname().equals(requestDto.getNickname())) {
            isNicknameAvailable(requestDto.getNickname(), userId);
        }

        // 2. 전화번호 변경 시 중복 체크
        if (requestDto.getPhoneNumber() != null && !user.getPhoneNumber().equals(requestDto.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(requestDto.getPhoneNumber())) {
                throw new BusinessException(ErrorCode.USER_PHONE_DUPLICATE);
            }
        }

        // ✅ 3. 기술 스택 유효성 검증 로직 추가 (프론트엔드 목록과 동기화)
        if (requestDto.getTechStacks() != null && !requestDto.getTechStacks().isEmpty()) {
            for (String tech : requestDto.getTechStacks()) {
                if (!TechStack.isValid(tech)) {
                    log.warn("프로필 수정 시 지원하지 않는 기술 스택 요청: {}", tech);
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 기술 스택이 포함되어 있습니다: " + tech); // 👈 INVALID_REQUEST → VALIDATION_ERROR
                }
            }
        }

        // 4. 비밀번호 암호화 준비
        String encodedPassword = null;
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        }

        // 5. 엔티티 메서드 딱 하나만 호출해서 전부 수정!
        user.updateProfile(
                requestDto.getNickname(),
                requestDto.getPosition(),
                requestDto.getTechStacks(),
                requestDto.getPhoneNumber(),
                encodedPassword
        );

        return UserMapper.mapToUserResponse(user);
    }

    /**
     * 4. 회원 탈퇴
     */
    @Override
    public void deleteUser(Long userId, User currentUser) {
        // 본인 확인 로직 추가
        // 탈퇴하려는 대상(userId)이 현재 로그인한 사람(currentUser)과 다르면 예외 발생
        if (!userId.equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        // 유저 존재 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        // 이 유저가 방장인 프로젝트들 폭파
        List<Project> ownedProjects = projectRepository.findAllByOwnerId(userId);
        for (Project project : ownedProjects) {
            projectService.deleteProject(project.getId(), userId);
        }

        // 이 유저가 작성한 게시글, 댓글 등 흔적 지우기
        boardPostRepository.softDeleteAllByAuthorId(userId);
        commentRepository.softDeleteAllByAuthorId(userId);
        applicationRepository.softDeleteAllByApplicantId(userId);
        projectMemberRepository.softDeleteAllByUserId(userId);

        //refresh 토큰은 DB에서 완전 삭제 (Hard-delete)
        refreshTokenRepository.deleteByUserId(userId);

        // 유저 테이블의 deleted_at 업데이트 쿼리 실행!
        userRepository.softDeleteById(userId);

        log.info("회원 탈퇴 완료 - 유저 ID: {}", userId);
    }

    /**
     * 5. 닉네임 중복 및 유효성 확인
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname, Long currentUserId) {
        // 1. 유효성 검사 (형식 에러 - USER_007)
        String regex = "^[a-zA-Z0-9가-힣]{2,10}$";
        if (nickname == null || !nickname.matches(regex)) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_FORMAT_INVALID);
        }

        // 2. 중복 체크 (진짜 중복 에러 - USER_003)
        boolean isDuplicate;
        if (currentUserId == null) {
            // 회원가입 시: 전체 중복 체크
            isDuplicate = userRepository.existsByNicknameIgnoreCase(nickname);
        } else {
            // 마이페이지 수정 시: '나'를 제외하고 중복 체크 (UserIdNot -> IdNot으로 수정)
            isDuplicate = userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, currentUserId);
        }

        if (isDuplicate) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
        }

        return true;
    }

    /**
     * 6. 전화번호 중복 확인
     */
    @Transactional(readOnly = true)
    @Override
    public boolean checkPhoneDuplicate(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    /**
     * 7. 프로필 이미지 수정 (본인 확인 + Cloudinary 적용)
     */
    @Override
    public String updateProfileImage(Long userId, User currentUser, MultipartFile profileImage) {
        // 1. 본인 확인 검증 (수정하려는 대상 ID가 로그인한 유저 ID와 같은지)
        if (!userId.equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED); // 권한 없음 에러
        }

        // 2. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        // 3. 파일 유효성 검사
        if (profileImage == null || profileImage.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "업로드할 이미지가 없습니다.");
        }

        // 4. Cloudinary 업로드
        String newImgUrl = cloudinaryService.uploadImage(profileImage);

        // 5. DB 업데이트
        user.updateProfileImg(newImgUrl);

        log.info("유저(ID: {}) 본인이 프로필 이미지를 업데이트했습니다.", userId);
        return newImgUrl;
    }

    /**
     * 8. 프로필 이미지 삭제 (본인 확인 + 기본 이미지로 복구)
     */
    @Override
    public void deleteProfileImage(Long userId, User currentUser) { // 👈 1. currentUser 추가
        // 2. 본인 확인 검증
        if (!userId.equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        // 3. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        // Cloudinary 사용 시에는 로컬 파일 삭제(deleteActualFile)가 필요 없습니다.
        // 보통은 DB 주소만 기본값으로 바꿔주는 것만으로도 충분합니다.

        // 5. DB 정보를 기본 이미지 URL로 변경
        user.updateProfileImg(DEFAULT_PROFILE_IMG);

        log.info("유저(ID: {})의 프로필 이미지가 기본 이미지로 초기화되었습니다.", userId);
    }

    /**
     * 9. 내가 작성한 모집글 조회 (본인 확인 추가)
     */
    @Transactional(readOnly = true)
    @Override
    public List<ProjectResponseDto> getMyOwnedPosts(Long userId, User currentUser) {
        // 본인 확인: 조회하려는 대상 ID가 내 ID와 다르면 예외 발생
        if (!userId.equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        return projectService.getMyOwnedPosts(userId);
    }

    /**
     * 10. 참여 중인 프로젝트/스터디 조회 (본인 확인 추가)
     */
    @Transactional(readOnly = true)
    @Override
    public List<ProjectResponseDto> getMyJoinedProjects(Long userId, User currentUser) {
        if (!userId.equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        return projectService.getMyJoinedProjects(userId);
    }

    /**
     * 11. 내 지원 현황 조회 (본인 확인 추가)
     */
    @Transactional(readOnly = true)
    @Override
    public List<ApplicationResponseDto> getMyPendingApplications(Long userId, User currentUser) {
        if (!userId.equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
        return applicationService.getMyPendingApplications(userId);
    }
}
