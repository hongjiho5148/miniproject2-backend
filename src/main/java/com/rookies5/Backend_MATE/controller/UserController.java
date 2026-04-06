package com.rookies5.Backend_MATE.controller;

import com.rookies5.Backend_MATE.common.SuccessResponse;
import com.rookies5.Backend_MATE.dto.request.UserRequestDto;
import com.rookies5.Backend_MATE.dto.response.ApplicationResponseDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;
import com.rookies5.Backend_MATE.security.CustomUserDetails;
import com.rookies5.Backend_MATE.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * 1. 내 프로필 상세 조회
     * GET /api/users/me
     */
    @GetMapping("/me")
    public SuccessResponse<UserResponseDto> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("내 프로필 조회 요청 - ID: {}", userDetails.getId());
        UserResponseDto user = userService.getUserById(userDetails.getId());
        return new SuccessResponse<>("내 정보 조회가 완료되었습니다.", user);
    }

    /**
     * 2. 내 정보 부분 수정
     * PATCH /api/users/me
     */
    @PatchMapping("/me")
    public SuccessResponse<UserResponseDto> updateMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UserRequestDto requestDto) {

        log.info("내 정보 수정 요청 - ID: {}", userDetails.getId());
        UserResponseDto updatedUser = userService.updateUser(userDetails.getId(), requestDto);
        return new SuccessResponse<>("유저 정보가 성공적으로 수정되었습니다.", updatedUser);
    }

    /**
     * 3. 프로필 이미지 수정 (파일 업로드)
     * PATCH /api/users/profile-image
     */
    @PatchMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SuccessResponse<String> updateProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("profileImage") MultipartFile profileImage) {

        log.info("프로필 이미지 수정 요청 - ID: {}", userDetails.getId());
        // 서비스에서 추가한 currentUser 파라미터를 넘겨줍니다.
        String newImgUrl = userService.updateProfileImage(userDetails.getId(), userDetails.getUser(), profileImage); // 👈 수정

        return new SuccessResponse<>("프로필 이미지가 성공적으로 수정되었습니다.", newImgUrl);
    }

    /**
     * 4. 프로필 이미지 삭제 (기본 이미지로 복구)
     * DELETE /api/users/profile-image
     */
    @DeleteMapping("/profile-image")
    public SuccessResponse<Void> deleteProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("프로필 이미지 삭제 요청 - ID: {}", userDetails.getId());
        // 서비스에서 추가한 currentUser 파라미터를 넘겨줍니다.
        userService.deleteProfileImage(userDetails.getId(), userDetails.getUser()); // 👈 수정
        return new SuccessResponse<>("프로필 이미지가 기본 이미지로 초기화되었습니다.");
    }

// --- 마이페이지 활동 이력 (나만 접근 가능) ---

    /**
     * 5. 내 모집글 조회 (내가 작성자/방장인 글)
     */
    @GetMapping("/me/posts/owned")
    public SuccessResponse<List<ProjectResponseDto>> getMyOwnedPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 서비스 호출 시 currentUser 추가
        List<ProjectResponseDto> projects = userService.getMyOwnedPosts(userDetails.getId(), userDetails.getUser()); // 👈 수정
        return new SuccessResponse<>("내 모집글 조회가 완료되었습니다.", projects);
    }

    /**
     * 6. 참여 중인 프로젝트 조회 (승인 완료된 상태)
     */
    @GetMapping("/me/posts/joined")
    public SuccessResponse<List<ProjectResponseDto>> getMyJoinedProjects(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 서비스 호출 시 currentUser 추가
        List<ProjectResponseDto> projects = userService.getMyJoinedProjects(userDetails.getId(), userDetails.getUser()); // 👈 수정
        return new SuccessResponse<>("참여 중인 프로젝트 조회가 완료되었습니다.", projects);
    }

    /**
     * 7. 내 신청 현황 조회 (대기/거절 상태 - PENDING, REJECTED)
     */
    @GetMapping("/me/applications")
    public SuccessResponse<List<ApplicationResponseDto>> getMyApplications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 서비스 호출 시 currentUser 추가
        List<ApplicationResponseDto> applications = userService.getMyPendingApplications(userDetails.getId(), userDetails.getUser()); // 👈 수정
        return new SuccessResponse<>("내 신청 및 거절 현황 조회가 완료되었습니다.", applications);
    }

    /**
     * 8. 회원 탈퇴 (나 본인만 가능)
     * DELETE /api/users/me
     */
    @DeleteMapping("/me")
    public SuccessResponse<Void> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        // 1. 로그에 탈퇴를 요청한 유저 ID 기록
        log.info("회원 탈퇴 요청 - ID: {}", userDetails.getId());

        // 2. 서비스 호출 시 '탈퇴 대상 ID'와 '현재 로그인한 유저 객체'를 모두 넘깁니다.
        userService.deleteUser(userDetails.getId(), userDetails.getUser());

        return new SuccessResponse<>("회원 탈퇴가 완료되었습니다.");
    }
}