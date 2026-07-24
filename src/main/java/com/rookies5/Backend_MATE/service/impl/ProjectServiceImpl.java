package com.rookies5.Backend_MATE.service.impl;

import com.rookies5.Backend_MATE.dto.request.ProjectRequestDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.entity.BoardPost;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.ProjectMember;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.ApplicationStatus;
import com.rookies5.Backend_MATE.entity.enums.Category;
import com.rookies5.Backend_MATE.entity.enums.MemberRole;
import com.rookies5.Backend_MATE.entity.enums.OnOffline;
import com.rookies5.Backend_MATE.entity.enums.ProjectStatus;
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.EntityNotFoundException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.mapper.ProjectMapper;
import com.rookies5.Backend_MATE.repository.ApplicationRepository;
import com.rookies5.Backend_MATE.repository.BoardPostRepository;
import com.rookies5.Backend_MATE.repository.CommentRepository;
import com.rookies5.Backend_MATE.repository.ProjectMemberRepository;
import com.rookies5.Backend_MATE.repository.ProjectRepository;
import com.rookies5.Backend_MATE.repository.UserRepository;
import com.rookies5.Backend_MATE.security.SecurityUtils;
import com.rookies5.Backend_MATE.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final BoardPostRepository boardPostRepository;
    private final CommentRepository commentRepository;

    /**
     * 1. 프로젝트 생성 (로그인 유저 기반 자동 설정)
     */
    @Override
    @Transactional
    public ProjectResponseDto createProject(Long userId, ProjectRequestDto requestDto) {
        // 1. 방장(User) 조회
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Project 엔티티 생성 및 저장
        Project project = ProjectMapper.mapToEntity(requestDto, owner);
        Project savedProject = projectRepository.save(project);

        // 3. 방장을 ProjectMember 테이블에도 저장
        ProjectMember leader = ProjectMember.builder()
                .project(savedProject)
                .user(owner)
                .role(MemberRole.OWNER)
                .position(owner.getPosition())
                .build();

        projectMemberRepository.save(leader);

        // 4. 결과 반환
        return ProjectMapper.mapToResponse(savedProject, userId);
    }

    /**
     * 2. 단건 조회 (상세 보기)
     */
    @Transactional(readOnly = true)
    @Override
    public ProjectResponseDto getProjectById(Long projectId) {
        return projectRepository.findById(projectId)
                .map(ProjectMapper::mapToResponse)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));
    }

    /**
     * 3. 전체 목록 조회 (카테고리 & 키워드 필터링 및 페이징 지원)
     */
    @Transactional(readOnly = true)
    @Override
    public Page<ProjectResponseDto> getAllProjects(String categoryStr, String keyword, String onOfflineStr, String statusStr, String techStack, Pageable pageable) {
        Category category = parseEnumSafely(Category.class, categoryStr);
        OnOffline onOffline = parseEnumSafely(OnOffline.class, onOfflineStr);
        ProjectStatus status = parseEnumSafely(ProjectStatus.class, statusStr);

        // QueryDSL 기반 동적 필터링 쿼리 호출 (카테고리, 키워드, 온오프라인, 모집상태, 기술스택 조합 검색)
        Page<Project> projectPage = projectRepository.findAllWithFilters(category, keyword, onOffline, status, techStack, pageable);

        return projectPage.map(ProjectMapper::mapToResponse);
    }

    // 잘못된 enum 문자열이 들어오면 필터링하지 않도록 null로 무시
    private <E extends Enum<E>> E parseEnumSafely(Class<E> enumClass, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 4. 프로젝트 부분 수정 (PATCH)
     */
    @Override
    @Transactional
    public ProjectResponseDto patchProject(Long projectId, Long userId, ProjectRequestDto requestDto) {
        // 1. 수정할 프로젝트 조회
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        // 2. 방장 권한 검증 (컨트롤러에서 넘겨받은 userId 사용)
        if (!project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        // 3. 엔티티 내부의 '부분 수정' 로직 호출
        project.updateProject(requestDto);

        // 4. 변경된 엔티티를 다시 DTO로 변환 (방장 여부 계산을 위해 userId 전달)
        return ProjectMapper.mapToResponse(project, userId);
    }

    /**
     * 5. 삭제 로직 (Soft Delete 적용)
     * 처리 순서: 자식 데이터(댓글, 게시글, 지원서) 상태 변경 -> 프로젝트 본체 상태 변경
     */
    @Override
    @Transactional
    public void deleteProject(Long projectId, Long userId) { // userId 파라미터 추가
        // 1. 프로젝트 존재 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));

        // 2. 권한 검증 (넘겨받은 userId와 방장 ID 비교)
        if (!project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        // 3. 자식 리소스들 Soft Delete (벌크 업데이트)
        commentRepository.softDeleteAllByProjectId(projectId);
        boardPostRepository.softDeleteAllByProjectId(projectId);
        applicationRepository.softDeleteAllByProjectId(projectId);
        projectMemberRepository.softDeleteAllByProjectId(projectId);

        // 4. 프로젝트 본체 Soft Delete
        projectRepository.softDeleteById(projectId);
    }

    /**
     * 6. 프로젝트 모집 수동 마감
     */
    @Override
    public ProjectResponseDto closeProjectRecruitment(Long projectId, Long userId) { // ✅ userId 파라미터 추가
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));

        // ✅ SecurityUtils 완전 제거 — Controller에서 넘겨받은 userId로 권한 검증
        if (!project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new BusinessException(ErrorCode.PROJECT_CLOSED);
        }
        if (project.getStatus() == ProjectStatus.DELETED) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "삭제된 프로젝트는 마감할 수 없습니다.");
        }

        project.closeRecruitment();
        return ProjectMapper.mapToResponse(project);
    }

    /**
     * 7. 내가 작성한 모집글 목록 조회
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getMyOwnedPosts(Long userId) {
        return projectRepository.findAllByOwnerId(userId).stream()
                .map(project -> ProjectMapper.mapToResponse(project, userId))
                .collect(Collectors.toList());
    }

    /**
     * 8. 내가 참여 중인 프로젝트 목록 조회 (수정 버전)
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getMyJoinedProjects(Long userId) {
        return projectMemberRepository.findAllByUserId(userId).stream()
                // 1. 기존 유지: 강퇴당한(Soft Delete된) 멤버는 제외
                .filter(member -> member.getDeletedAt() == null)

                // 2. 수정: 매핑 시 포지션 정보 주입
                .map(member -> {
                    // 기존 매퍼를 사용하여 기본 DTO 생성
                    ProjectResponseDto dto = ProjectMapper.mapToResponse(member.getProject(), userId);

                    // 핵심: 현재 순회 중인 '나(member)'의 포지션을 DTO에 세팅!
                    if (member.getPosition() != null) {
                        dto.setApplicantPosition(member.getPosition().name());
                    } else {
                        dto.setApplicantPosition("선택없음");
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 9. 프로젝트 재모집 시작 (OWNER 전용)
     */
    @Transactional
    @Override
    public ProjectResponseDto reopenProject(Long projectId, Long userId) {
        // 1. 프로젝트 존재 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));

        // 2. 방장 권한 확인 (OWNER 전용)
        if (!project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "재모집 권한이 없습니다.");
        }

        // 3. 상태 확인 (이미 모집 중이면 굳이 로직 수행 안 함)
        if (project.getStatus() == ProjectStatus.RECRUITING) {
            return ProjectMapper.mapToResponse(project, userId);
        }

        // 4. 재모집 로직 실행 (엔티티 메서드 호출)
        // 인원이나 날짜 수정 없이 상태만 바꾼다면 기존 값을 그대로 넘깁니다.
        project.reopen(project.getRecruitCount(), project.getEndDate());

        return ProjectMapper.mapToResponse(project, userId);
    }
}
