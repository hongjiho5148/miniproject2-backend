package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Cloudinary 실제 호출 없이, 업로드 전 검증 로직(확장자/Content-Type/매직바이트)만 단독 테스트한다.
class CloudinaryImageValidationTest {

    private final CloudinaryService service = new CloudinaryService(null);

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

    @Test
    @DisplayName("확장자가 화이트리스트에 없으면 거부한다")
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "image/jpeg", JPEG_HEADER);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateImage", file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_UNSUPPORTED_EXTENSION));
    }

    @Test
    @DisplayName("확장자는 jpg지만 실제 파일 시그니처가 이미지가 아니면 거부한다 (확장자 위조 방어)")
    void rejectsSpoofedExtensionWithWrongMagicBytes() {
        byte[] fakeContent = "<?php system($_GET['cmd']); ?>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", fakeContent);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateImage", file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_UNSUPPORTED_EXTENSION));
    }

    @Test
    @DisplayName("5MB를 초과하면 거부한다")
    void rejectsOversizedFile() {
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy(JPEG_HEADER, 0, big, 0, JPEG_HEADER.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", big);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateImage", file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED));
    }

    @Test
    @DisplayName("정상 JPEG/PNG는 검증을 통과한다")
    void acceptsValidImages() {
        MockMultipartFile jpeg = new MockMultipartFile("file", "profile.jpg", "image/jpeg", JPEG_HEADER);
        MockMultipartFile png = new MockMultipartFile("file", "profile.png", "image/png", PNG_HEADER);

        ReflectionTestUtils.invokeMethod(service, "validateImage", jpeg);
        ReflectionTestUtils.invokeMethod(service, "validateImage", png);
        // 예외 없이 통과하면 성공
    }
}
