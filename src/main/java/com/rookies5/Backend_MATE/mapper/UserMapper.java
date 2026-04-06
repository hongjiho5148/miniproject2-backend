package com.rookies5.Backend_MATE.mapper;

import com.rookies5.Backend_MATE.dto.request.UserRequestDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;
import com.rookies5.Backend_MATE.entity.User;
import org.springframework.util.StringUtils;

public class UserMapper {

    // 1. Entity -> Response DTO (프론트엔드 응답용)
    public static UserResponseDto mapToUserResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId()) // 엔티티의 PK 필드명에 맞춰 수정 (userId)
                .email(user.getEmail())
                .nickname(user.getNickname())
                .phoneNumber(user.getPhoneNumber())
                .position(user.getPosition())
                .techStacks(user.getTechStacks())
                .profileImg(user.getProfileImg())
                .createdAt(user.getCreatedAt())
                .deleted(user.getDeletedAt() != null)
                .build();
    }

    // 2. Request DTO -> Entity (회원가입/DB 저장용)
    public static User mapToUser(UserRequestDto requestDto) {

        // 닉네임 자동 추출
        String finalNickname = StringUtils.hasText(requestDto.getNickname())
                ? requestDto.getNickname()
                : requestDto.getEmail().split("@")[0];

        // 기본 프로필 이미지 설정
        String finalProfileImg = StringUtils.hasText(requestDto.getProfileImg())
                ? requestDto.getProfileImg()
                : "https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png";

        return User.builder()
                .email(requestDto.getEmail())
                .password(requestDto.getPassword())
                .nickname(finalNickname)
                .phoneNumber(requestDto.getPhoneNumber())
                .position(requestDto.getPosition())
                .techStacks(requestDto.getTechStacks())
                .profileImg(finalProfileImg)
                .build();
    }
}