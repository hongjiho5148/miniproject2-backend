package com.rookies5.Backend_MATE.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public String uploadImage(MultipartFile file) {
        try {
            // 파일을 Cloudinary에 업로드하고 결과 수신
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
            
            // 결과 맵에서 이미지의 보안 URL(https)을 가져와 문자열로 반환
            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            throw new RuntimeException("Cloudinary 이미지 업로드 실패: " + e.getMessage());
        }
    }
}