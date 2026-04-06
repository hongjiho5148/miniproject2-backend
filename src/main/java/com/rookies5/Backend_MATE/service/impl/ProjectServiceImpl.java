package com.rookies5.Backend_MATE.service.impl;

import com.rookies5.Backend_MATE.dto.request.ProjectRequestDto;
import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.entity.BoardPost;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.ProjectMember;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.ApplicationStatus;
import com.rookies5.Backend_MATE.entity.enums.MemberRole;
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
import com.rookies5.Backend_MATE.security.SecurityUtils; // [추가]
import com.rookies5.Backend_MATE.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 3. 전체 목록 조회
     */
    @Transactional(readOnly = true)
    @Override
    public List<ProjectResponseDto> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectMapper::mapToResponse)
                .collect(Collectors.toList());
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

        // 3. 자식 리소스들 Soft Delete (벌크 업데이트 - 지호님이 짠 코드 그대로!)
        commentRepository.softDeleteAllByProjectId(projectId);
        boardPostRepository.softDeleteAllByProjectId(projectId);
        applicationRepository.softDeleteAllByProjectId(projectId);
        projectMemberRepository.softDeleteAllByProjectId(projectId);

        // 4. 프로젝트 본체 Soft Delete
        projectRepository.softDeleteById(projectId);
    }

    /**
     * 6. 프로젝트 모집 수동 마감 (파라미터 주입 방식)
     */
    @Override
    public ProjectResponseDto closeProjectRecruitment(Long projectId, Long userId) { // 👈 파라미터 추가
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));

        // 외부에서 넘겨받은 userId로 권한 검증
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

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getMyOwnedPosts(Long userId) {
        return projectRepository.findAllByOwnerId(userId).stream()
                .map(project -> ProjectMapper.mapToResponse(project, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDto> getMyJoinedProjects(Long userId) {
        return applicationRepository.findAllByApplicantIdAndStatus(userId, ApplicationStatus.ACCEPTED)
                .stream()
                .map(app -> ProjectMapper.mapToResponse(app.getProject(), userId))
                .collect(Collectors.toList());
    }

    //모집글 재오픈
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