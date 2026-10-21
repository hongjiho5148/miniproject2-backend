package com.rookies5.Backend_MATE.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordResetTokenResponseDto {
    private final String resetToken;
    private final long expiresInSeconds;
}
