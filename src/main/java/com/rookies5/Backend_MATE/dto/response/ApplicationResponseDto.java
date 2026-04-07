package com.rookies5.Backend_MATE.dto.response;

import com.rookies5.Backend_MATE.entity.enums.ApplicationStatus;
import com.rookies5.Backend_MATE.entity.Application;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class ApplicationResponseDto {
    private Long id;            // 지원서 ID (application_id)
    private Long projectId;     // 이동을 위한 원본 게시글 ID
    private String projectTitle; // 리스트에 보여줄 게시글 제목 (추가!)
    private String category;

    private Long applicantId;
    private String message;

    private String applicantNickname;
    private String applicantPosition;
    private String profileImg;

    private Set<String> techStacks;

    private String link;
    private String contact;

    private ApplicationStatus status;
    private LocalDateTime createdAt;

    public static ApplicationResponseDto from(Application application) {
        return ApplicationResponseDto.builder()
                .id(application.getId())
                .projectId(application.getProject().getId())
                .projectTitle(application.getProject().getTitle())
                .category(application.getProject().getCategory().name()) // Enum일 경우 .name()
                .applicantId(application.getApplicant().getId())
                .message(application.getMessage())
                .applicantNickname(application.getApplicant().getNickname())
                .profileImg(application.getApplicant().getProfileImg())
                .link(application.getLink())
                .contact(application.getContact())
                .techStacks(application.getApplicant().getTechStacks())
                // 이 부분을 추가해야 화면에 '지원 분야'가 정상적으로 나옵니다!
                .applicantPosition(application.getPosition() != null ? application.getPosition().name() : "선택없음")

                .status(application.getStatus())
                .createdAt(application.getCreatedAt())
                .build();
    }
}