package com.rookies5.Backend_MATE.dto.response;

import com.rookies5.Backend_MATE.entity.enums.MemberRole;
import com.rookies5.Backend_MATE.entity.enums.Position;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectMemberResponseDto {
    private Long id;
    private Long projectId;
    private Long userId;

    private MemberRole role; // 방장(OWNER) vs 팀원(MEMBER)

    private String nickname;
    // 💡 멤버별 최신 프로필 이미지를 반영하기 위해 추가
    private String profileImg;
    private Position position;
}