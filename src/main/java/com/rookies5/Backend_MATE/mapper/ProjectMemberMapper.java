package com.rookies5.Backend_MATE.mapper;

import com.rookies5.Backend_MATE.dto.response.ProjectMemberResponseDto;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.ProjectMember;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.MemberRole;

public class ProjectMemberMapper {

    // 1. Entity -> Response DTO (팀원 목록 출력용)
    public static ProjectMemberResponseDto mapToResponse(ProjectMember member) {
        return ProjectMemberResponseDto.builder()
                .id(member.getId())
                .projectId(member.getProject().getId())
                // 💡 엔티티 필드명에 맞춰 userId로 수정
                .userId(member.getUser().getId())
                .role(member.getRole())
                .nickname(member.getUser().getNickname())
                // 💡 추가: 유저 엔티티의 최신 프로필 이미지를 DTO에 담아줍니다.
                .profileImg(member.getUser().getProfileImg())
                .position(member.getUser().getPosition())
                .build();
    }

    // 2. Entity 조립용 (새로운 팀원 합류 시)
    public static ProjectMember mapToEntity(Project project, User user, MemberRole role) {
        return ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .build();
    }
}