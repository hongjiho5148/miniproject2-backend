package com.rookies5.Backend_MATE.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png");
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png");

    public String uploadImage(MultipartFile file) {
        validateImage(file);
        try {
            // 파일을 Cloudinary에 업로드하고 결과 수신
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

            // 결과 맵에서 이미지의 보안 URL(https)을 가져와 문자열로 반환
            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, e.getMessage());
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "업로드할 이미지가 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 1. 확장자 검증 (파일명 기준)
        String filename = file.getOriginalFilename();
        String extension = (filename != null && filename.contains("."))
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_UNSUPPORTED_EXTENSION);
        }

        // 2. Content-Type 검증 (클라이언트가 보낸 MIME 타입 기준)
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.FILE_UNSUPPORTED_EXTENSION);
        }

        // 3. 매직 바이트 검증 (확장자/Content-Type을 위조해도 실제 파일 시그니처는 못 속임)
        try {
            byte[] header = file.getBytes();
            if (!isJpeg(header) && !isPng(header)) {
                throw new BusinessException(ErrorCode.FILE_UNSUPPORTED_EXTENSION);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, e.getMessage());
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return bytes.length >= pngSignature.length
                && Arrays.equals(Arrays.copyOf(bytes, pngSignature.length), pngSignature);
    }
}