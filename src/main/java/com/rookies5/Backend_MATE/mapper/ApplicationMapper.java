package com.rookies5.Backend_MATE.mapper;

import com.rookies5.Backend_MATE.dto.request.ApplicationRequestDto;
import com.rookies5.Backend_MATE.dto.response.ApplicationResponseDto;
import com.rookies5.Backend_MATE.entity.Application;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.ApplicationStatus;

public class ApplicationMapper {

    /**
     * Entity -> Response DTO 변환 (조회용)
     */
    public static ApplicationResponseDto mapToApplicationResponse(Application application) {
        return ApplicationResponseDto.builder()
                .id(application.getId())
                .projectId(application.getProject().getId())
                .projectTitle(application.getProject().getTitle())
                .category(application.getProject().getCategory() != null ?  // 추가
                        application.getProject().getCategory().name() : null) // 추가
                .applicantId(application.getApplicant().getId())
                .applicantNickname(application.getApplicant().getNickname())
                .applicantPosition(application.getPosition() != null ?
                        application.getPosition().name() : null)
                .techStacks(application.getApplicant().getTechStacks())
                .message(application.getMessage())
                .status(application.getStatus())
                .createdAt(application.getAppliedAt())
                .build();
    }

    /**
     * Request DTO -> Entity 변환 (저장용)
     */
    public static Application mapToEntity(ApplicationRequestDto requestDto, Project project, User applicant) {
        return Application.builder()
                .project(project)
                .applicant(applicant)
                .message(requestDto.getMessage())
                .position(requestDto.getPosition()) // ✅ 추가: DTO의 포지션을 Entity에 매핑
                .status(ApplicationStatus.PENDING)
                .build();
    }
}
