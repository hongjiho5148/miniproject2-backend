package com.rookies5.Backend_MATE.dto.response;

import com.rookies5.Backend_MATE.entity.enums.Category;
import com.rookies5.Backend_MATE.entity.enums.OnOffline;
import com.rookies5.Backend_MATE.entity.enums.ProjectStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ProjectResponseDto {
    private Long id;
    private Long ownerId;
    private String ownerNickname;
    // 💡 마이페이지에서 수정한 최신 이미지를 반영하기 위해 추가
    private String ownerProfileImg;

    private Category category;
    private String title;
    private String content;

    private Integer recruitCount;
    private Integer currentCount;
    private ProjectStatus status;
    private OnOffline onOffline;

    private LocalDate endDate;
    private Long remainingDays;
    private LocalDateTime createdAt;

    //삭제구현
    private boolean deleted;

    // --- 활동 이력 구분을 위해 추가된 필드 ---
    private boolean isOwner;      // 내가 방장인지 여부 (프론트 버튼 제어용)
    private String role;          // 나의 역할 ("OWNER" 또는 "MEMBER")
}