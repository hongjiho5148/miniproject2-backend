package com.rookies5.Backend_MATE.service.impl;

import com.rookies5.Backend_MATE.dto.response.ProjectMemberResponseDto;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.ProjectMember;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.ApplicationStatus;
import com.rookies5.Backend_MATE.entity.enums.MemberRole;
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.EntityNotFoundException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.mapper.ProjectMemberMapper;
import com.rookies5.Backend_MATE.repository.*;
import com.rookies5.Backend_MATE.service.ProjectMemberService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    // 멤버 목록 조회 시 프로젝트 존재 여부를 검증하기 위해 추가됨
    private final ProjectRepository projectRepository;
    private final ApplicationRepository applicationRepository;
    private final BoardPostRepository boardPostRepository;
    private final CommentRepository commentRepository;

    /**
     * 1. 특정 프로젝트의 참여 멤버 목록 조회
     */
    @Transactional(readOnly = true)
    @Override
    public List<ProjectMemberResponseDto> getMembersByProjectId(Long projectId) {
        // 프로젝트 존재 여부 사전 검증
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId);
        }

        return projectMemberRepository.findAllByProjectId(projectId).stream()
                .map(ProjectMemberMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 팀원 강제 퇴출 (OWNER 전용)
     */
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void removeMember(Long memberId, Long currentUserId) {
        // 1. 대상 멤버(ProjectMember) 존재 확인 및 변수 할당
        ProjectMember targetMember = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.MEMBER_NOT_FOUND, memberId));

        // 승인과 퇴출이 동시에 일어나도 팀원 수(currentCount)가 어긋나지 않도록 프로젝트 행을 잠근다
        Project project = projectRepository.findByIdForUpdate(targetMember.getProject().getId())
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, targetMember.getProject().getId()));
        User user = targetMember.getUser(); // 퇴출될 유저
        Long ownerId = project.getOwner().getId();

        // 2. 권한 체크: 방장 여부 확인
        if (!ownerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "팀원 퇴출은 방장만 가능합니다.");
        }

        // 3. 방장 본인 퇴출 방지
        if (user.getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "방장 본인을 퇴출할 수 없습니다.");
        }

        // 4. 지원서 상태 변경을 먼저 수행
        applicationRepository.findByProjectIdAndApplicantId(project.getId(), user.getId())
                .ifPresent(app -> {
                    app.setStatus(ApplicationStatus.REJECTED);
                    applicationRepository.saveAndFlush(app);
                });

        // 5. 강제 Flush 및 인원수 조절
        project.decreaseCurrentCount();
        entityManager.flush();
        entityManager.clear();

        // 6. 벌크 삭제 쿼리 실행 (댓글 -> 게시글)
        commentRepository.softDeleteAllByProjectIdAndAuthorId(project.getId(), user.getId());
        boardPostRepository.softDeleteAllByProjectIdAndAuthorId(project.getId(), user.getId());

        // 7. 멤버 Soft Delete
        // 위에서 entityManager.clear()를 했으므로, 다시 조회해서 지워주는 것이 가장 안전합니다.
        ProjectMember freshTargetMember = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.MEMBER_NOT_FOUND, memberId));
        freshTargetMember.softDelete();
    }
}