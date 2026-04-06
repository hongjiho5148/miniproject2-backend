package com.rookies5.Backend_MATE.controller;

import com.rookies5.Backend_MATE.common.SuccessResponse;
import com.rookies5.Backend_MATE.dto.request.ApplicationRequestDto;
import com.rookies5.Backend_MATE.dto.response.ApplicationResponseDto;
import com.rookies5.Backend_MATE.security.CustomUserDetails;
import com.rookies5.Backend_MATE.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * 1. 프로젝트 지원하기
     * POST /api/applications/{projectId}
     */
    @PostMapping("/{projectId}")
    public SuccessResponse<ApplicationResponseDto> applyToProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplicationRequestDto requestDto) {

        Long applicantId = userDetails.getId();

        log.info("프로젝트 지원 요청 - projectId: {}, applicantId(from Token): {}", projectId, applicantId);

        // 서비스에는 여전히 ID 값들을 던져줌
        ApplicationResponseDto responseDto = applicationService.applyToProject(projectId, applicantId, requestDto);

        return new SuccessResponse<>("지원이 완료되었습니다.", responseDto);
    }
    /**
     * 2. 특정 프로젝트의 지원자 목록 조회 (방장용)
     * GET /api/applications/projects/{projectId}
     */
    @GetMapping("/projects/{projectId}")
    public SuccessResponse<List<ApplicationResponseDto>> getApplicationsByProjectId(
            @PathVariable Long projectId) {
        log.info("프로젝트 지원자 목록 조회 요청 - projectId: {}", projectId);
        List<ApplicationResponseDto> responseDtoList = applicationService.getApplicationsByProjectId(projectId);
        return new SuccessResponse<>("지원자 목록 조회가 완료되었습니다.", responseDtoList);
    }

    /**
     * 3. 지원 취소 (PENDING 상태일 때만 가능)
     * DELETE /api/applications/{applicationId}
     */
    @DeleteMapping("/{applicationId}")
    public SuccessResponse<Void> deleteApplication(@PathVariable Long applicationId) {
        log.info("지원 취소 요청 - applicationId: {}", applicationId);
        applicationService.deleteApplication(applicationId);
        return new SuccessResponse<>("지원이 취소되었습니다.");
    }

    /**
     * 4. 지원서 상태 변경 (승인/거절 통합)
     * PATCH /api/applications/{applicationId}/status
     */
    @PatchMapping("/{applicationId}/status")
    public SuccessResponse<ApplicationResponseDto> updateApplicationStatus(
            @PathVariable Long applicationId,
            @RequestBody Map<String, String> statusMap) { // 요구사항대로 Map 사용

        String status = statusMap.get("status");
        log.info("지원서 상태 변경 요청 - ID: {}, Status: {}", applicationId, status);

        ApplicationResponseDto responseDto;

        // 대소문자 구분 없이 'accept'인 경우 승인 로직 실행
        if ("accept".equalsIgnoreCase(status)) {
            responseDto = applicationService.acceptApplication(applicationId);
            return new SuccessResponse<>("지원서가 승인되었습니다.", responseDto);
        }
        // 'reject'인 경우 거절 로직 실행
        else if ("reject".equalsIgnoreCase(status)) {
            responseDto = applicationService.rejectApplication(applicationId);
            return new SuccessResponse<>("지원서가 거절되었습니다.", responseDto);
        }
        // 그 외 잘못된 값이 들어온 경우 에러 처리
        else {
            throw new RuntimeException("잘못된 상태 값입니다. (accept 또는 reject여야 함)");
            // 지호님 프로젝트에 BusinessException이 있다면 그걸 사용하세요!
        }
    }
}