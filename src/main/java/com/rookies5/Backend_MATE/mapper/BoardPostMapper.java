package com.rookies5.Backend_MATE.mapper;

import com.rookies5.Backend_MATE.dto.request.BoardPostRequestDto;
import com.rookies5.Backend_MATE.dto.response.BoardPostResponseDto;
import com.rookies5.Backend_MATE.entity.BoardPost;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;

import java.util.Objects;

public class BoardPostMapper {

    /**
     * Entity -> Response DTO 변환
     * 게시글 상세 조회 및 목록 출력 시 사용하며, 작성자의 닉네임을 포함합니다.
     */
    public static BoardPostResponseDto mapToResponse(BoardPost post, Long currentUserId) {

        boolean isAuthor = Objects.equals(post.getAuthor().getId(), currentUserId);

        return BoardPostResponseDto.builder()
                .id(post.getId())
                .projectId(post.getProject().getId())
                .authorId(post.getAuthor().getId())
                .authorNickname(post.getAuthor().getNickname()) // 작성자 닉네임 매핑
                // ★ 작성자의 최신 프로필 이미지 매핑 추가
                .authorProfileImg(post.getAuthor() != null ? post.getAuthor().getProfileImg() : null)
                .title(post.getTitle())
                .content(post.getContent())
                .isAuthor(isAuthor)
                .viewCount(post.getViewCount())
                .createdAt(post.getCreatedAt()) // 생성 시간 추가
                .build();
    }

    /**
     * Request DTO -> Entity 변환
     * 새로운 게시글을 등록할 때 사용하며, 초기 조회수는 0으로 설정합니다.
     */
    public static BoardPost mapToEntity(BoardPostRequestDto requestDto, Project project, User author) {
        return BoardPost.builder()
                .project(project)
                .author(author)
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .viewCount(0) // 초기 조회수 0으로 고정
                .build();
    }
}