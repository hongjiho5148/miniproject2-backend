package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.dto.request.UserRequestDto;
import com.rookies5.Backend_MATE.dto.response.ApplicationResponseDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;
import com.rookies5.Backend_MATE.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    /**
     * 1. 특정 사용자 상세 정보 조회 (내 정보 조회 /api/users/me)
     */
    UserResponseDto getUserById(Long userId);

    /**
     * 2. 전체 사용자 목록 조회 (관리자용)
     */
    List<UserResponseDto> getAllUsers();

    /**
     * 3. 사용자 프로필 정보 수정 (닉네임, 포지션, 기술 스택 등)
     */
    UserResponseDto updateUser(Long userId, UserRequestDto requestDto);

    /**
     * 4. 회원 탈퇴 (soft-delete)
     */
    void deleteUser(Long userId, User currentUser);


    /**
     * 5. 닉네임 중복 체크 (수정 시 실시간 검증용)
     */
    boolean isNicknameAvailable(String nickname, Long currentUserId);

    /**
     * 6. 전화번호 중복 체크 (검증 필요 시 사용)
     */
    boolean checkPhoneDuplicate(String phoneNumber);

    /**
     * 7. 프로필 이미지 단독 수정
     */
    // 7. 프로필 이미지 수정 (파라미터 추가)
    String updateProfileImage(Long userId, User currentUser, MultipartFile profileImage);

    /**
     * 8. 프로필 이미지 삭제 (기본 이미지로 초기화)
     */
    void deleteProfileImage(Long userId, User currentUser);

    // 9. 내가 작성한 모집글 조회 (파라미터 추가)
    List<ProjectResponseDto> getMyOwnedPosts(Long userId, User currentUser);

    // 10. 참여 중인 프로젝트/스터디 조회 (파라미터 추가)
    List<ProjectResponseDto> getMyJoinedProjects(Long userId, User currentUser);

    // 11. 내 지원 현황 조회 (파라미터 추가)
    List<ApplicationResponseDto> getMyPendingApplications(Long userId, User currentUser);
}