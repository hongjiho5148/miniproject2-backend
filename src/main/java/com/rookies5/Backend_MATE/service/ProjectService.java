package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.dto.request.ProjectRequestDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;

import java.util.List;

public interface ProjectService {

    /**
     * 새로운 프로젝트 팀 생성
     * @param requestDto 카테고리, 모집 인원, 마감일 등 입력 정보
     * @return 생성된 프로젝트 상세 정보 (ID, 방장 닉네임, D-Day 포함)
     */
    ProjectResponseDto createProject(Long userId, ProjectRequestDto requestDto);

    /**
     * 특정 프로젝트 상세 정보 조회
     * @param projectId 조회할 프로젝트 ID
     * @return 프로젝트 상세 데이터
     */
    ProjectResponseDto getProjectById(Long projectId);

    /**
     * 전체 프로젝트 목록 조회
     * @return 메인 페이지나 목록에 뿌려줄 프로젝트 리스트
     */
    List<ProjectResponseDto> getAllProjects();

    /**
     * 프로젝트 정보 수정
     * @param projectId 수정할 프로젝트 ID
     * @param requestDto 수정할 내용 (제목, 내용, 모집 상태 등)
     * @return 수정 완료된 프로젝트 정보
     */
    ProjectResponseDto patchProject(Long projectId, Long userId, ProjectRequestDto requestDto);

    /**
     * 프로젝트 삭제
     * @param projectId 삭제할 프로젝트 ID
     */
    void deleteProject(Long projectId, Long userId);

    /**
     * 프로젝트 모집 수동 마감
     * @param projectId 마감할 프로젝트 ID
     * @return 마감된 프로젝트 정보
     */
    ProjectResponseDto closeProjectRecruitment(Long projectId, Long userId);

    List<ProjectResponseDto> getMyOwnedPosts(Long userId);      // (1) 내 모집글
    List<ProjectResponseDto> getMyJoinedProjects(Long userId);   // (2) 참여 중인 프로젝트
    ProjectResponseDto reopenProject(Long projectId, Long userId);
}