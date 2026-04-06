package com.rookies5.Backend_MATE.controller;

import com.rookies5.Backend_MATE.common.SuccessResponse;
import com.rookies5.Backend_MATE.dto.request.ProjectRequestDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.security.CustomUserDetails;
import com.rookies5.Backend_MATE.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 새로운 프로젝트 모집글 생성
     */
    @PostMapping
    public SuccessResponse<ProjectResponseDto> createProject(
            @Valid @RequestBody ProjectRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 로그인된 유저의 ID를 추출합니다.
        Long currentUserId = userDetails.getId();

        log.info("프로젝트 생성 요청 - 유저ID: {}, 제목: {}", currentUserId, requestDto.getTitle());

        // 서비스에 유저 ID를 따로 전달합니다.
        ProjectResponseDto responseDto = projectService.createProject(currentUserId, requestDto);

        return new SuccessResponse<>("프로젝트가 성공적으로 생성되었습니다.", responseDto);
    }

    /**
     * 전체 프로젝트 목록 조회
     * 💡 필터링을 위해 @RequestParam category와 keyword를 추가했습니다.
     */
    @GetMapping
    public SuccessResponse<List<ProjectResponseDto>> getAllProjects(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        log.info("전체 프로젝트 목록 조회 요청 - category: {}, keyword: {}", category, keyword);

        // 서비스 메서드 호출 시 파라미터를 함께 전달합니다.
        List<ProjectResponseDto> responseDtoList = projectService.getAllProjects(category, keyword);

        return new SuccessResponse<>("프로젝트 목록 조회가 완료되었습니다.", responseDtoList);
    }

    /**
     * 특정 프로젝트 상세 조회
     */
    @GetMapping("/{projectId}")
    public SuccessResponse<ProjectResponseDto> getProjectById(@PathVariable Long projectId) {
        log.info("프로젝트 상세 조회 요청 - projectId: {}", projectId);
        ProjectResponseDto responseDto = projectService.getProjectById(projectId);
        return new SuccessResponse<>("프로젝트 상세 조회가 완료되었습니다.", responseDto);
    }

    /**
     * 프로젝트 정보 부분 수정 (PATCH)
     */
    @PatchMapping("/{projectId}")
    public SuccessResponse<ProjectResponseDto> patchProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ProjectRequestDto requestDto) {

        log.info("프로젝트 부분 수정 요청 - projectId: {}, userId: {}", projectId, userDetails.getId());

        ProjectResponseDto responseDto = projectService.patchProject(projectId, userDetails.getId(), requestDto);

        return new SuccessResponse<>("프로젝트 정보가 성공적으로 수정되었습니다.", responseDto);
    }

    /**
     * 프로젝트 삭제
     */
    @DeleteMapping("/{projectId}")
    public SuccessResponse<Void> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails) { // ✅ SecurityUtils 제거, @AuthenticationPrincipal로 통일
        log.info("프로젝트 삭제 요청 - projectId: {}, userId: {}", projectId, userDetails.getId());
        projectService.deleteProject(projectId, userDetails.getId());
        return new SuccessResponse<>("프로젝트가 성공적으로 삭제되었습니다.");
    }

    /**
     * 프로젝트 수동 마감
     */
    @PatchMapping("/{projectId}/close")
    public SuccessResponse<ProjectResponseDto> closeProjectRecruitment(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails) { // ✅ userId를 Controller에서 받아 Service로 전달
        log.info("프로젝트 수동 마감 요청 - projectId: {}, userId: {}", projectId, userDetails.getId());
        ProjectResponseDto responseDto = projectService.closeProjectRecruitment(projectId, userDetails.getId());
        return new SuccessResponse<>("프로젝트 모집이 마감되었습니다.", responseDto);
    }

    /**
     * 프로젝트 재모집 시작 (OWNER 전용)
     */
    @PatchMapping("/{projectId}/reopen")
    public SuccessResponse<ProjectResponseDto> reopenProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        ProjectResponseDto response = projectService.reopenProject(projectId, customUserDetails.getId());

        return new SuccessResponse<>("재모집이 시작되었습니다.", response);
    }
}
