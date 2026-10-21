package com.rookies5.Backend_MATE.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordResetConfirmRequestDto {

    @NotBlank(message = "재설정 토큰은 필수입니다.")
    private String token;

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    private String newPassword;
}
