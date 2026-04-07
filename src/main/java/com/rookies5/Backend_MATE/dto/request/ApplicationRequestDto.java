package com.rookies5.Backend_MATE.dto.request;

import com.rookies5.Backend_MATE.entity.enums.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationRequestDto {
    private Long projectId;

    private Long applicantId;

    @NotBlank(message = "지원 동기는 필수 입력 항목입니다.")
    private String message;

    // ✅ 추가: 사용자가 지원할 때 선택하는 포지션
    @NotNull(message = "지원 포지션은 필수 입력 항목입니다.")
    private Position position;

    private String link;    // 포트폴리오/깃허브 링크
    private String contact; // 오픈채팅/연락처
}
