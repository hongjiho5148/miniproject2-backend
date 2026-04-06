package com.rookies5.Backend_MATE.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BoardPostResponseDto {
    private Long id;
    private Long projectId;
    private Long authorId;
    private String authorNickname; // 작성자 이름
    // 💡 작성자의 최신 프로필 이미지를 반영하기 위해 추가
    private String authorProfileImg;
    private String title;
    private String content;
    private Integer viewCount;
    private LocalDateTime createdAt; // BaseEntity의 필드
    private boolean isAuthor;
}