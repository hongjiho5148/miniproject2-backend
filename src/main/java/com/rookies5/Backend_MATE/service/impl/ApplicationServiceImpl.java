package com.rookies5.Backend_MATE.service.impl;

import com.rookies5.Backend_MATE.dto.request.ApplicationRequestDto;
import com.rookies5.Backend_MATE.dto.response.ApplicationResponseDto;
import com.rookies5.Backend_MATE.entity.Application;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.ProjectMember;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.ApplicationStatus;
import com.rookies5.Backend_MATE.entity.enums.MemberRole;
import com.rookies5.Backend_MATE.entity.enums.ProjectStatus;
import com.rookies5.Backend_MATE.exception.BusinessException;
import com.rookies5.Backend_MATE.exception.EntityNotFoundException;
import com.rookies5.Backend_MATE.exception.ErrorCode;
import com.rookies5.Backend_MATE.mapper.ApplicationMapper;
import com.rookies5.Backend_MATE.repository.ApplicationRepository;
import com.rookies5.Backend_MATE.repository.ProjectMemberRepository;
import com.rookies5.Backend_MATE.repository.ProjectRepository;
import com.rookies5.Backend_MATE.repository.UserRepository;
import com.rookies5.Backend_MATE.security.SecurityUtils;
import com.rookies5.Backend_MATE.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    /**
     * 1. 프로젝트 지원하기
     */
    @Override
    @Transactional
    public ApplicationResponseDto applyToProject(Long projectId, Long applicantId, ApplicationRequestDto requestDto) {
        // 1. 프로젝트 존재 여부 및 상태 확인
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId));

        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new BusinessException(ErrorCode.PROJECT_CLOSED);
        }

        // 2. [추가] 방장 본인인지 확인
        if (project.getOwner().getId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_APPLY, "방장은 본인의 프로젝트에 지원할 수 없습니다.");
        }

        // 2. 지원자 존재 여부 확인
        User applicant = userRepository.findById(applicantId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND, applicantId));

        // 3. [보안/비즈니스] 이미 해당 프로젝트의 멤버인지 확인
        boolean isMember = projectMemberRepository.existsByProjectIdAndUserId(projectId, applicantId);
        if (isMember) {
            throw new BusinessException(ErrorCode.APPLY_DUPLICATE, "이미 프로젝트에 참여 중인 멤버입니다.");
        }

        // 4. 중복 지원 여부 확인
        if (applicationRepository.existsByProjectIdAndApplicantId(projectId, applicantId)) {
            throw new BusinessException(ErrorCode.APPLY_DUPLICATE);
        }

        // 5. 엔티티 변환 및 저장 (매퍼 메서드에 ID가 아닌 객체를 직접 전달)
        Application application = ApplicationMapper.mapToEntity(requestDto, project, applicant);
        Application savedApplication = applicationRepository.save(application);

        return ApplicationMapper.mapToApplicationResponse(savedApplication);
    }

    /**
     * 2. 특정 프로젝트의 지원자 목록 보기 (방장용)
     */
    @Transactional(readOnly = true)
    @Override
    public List<ApplicationResponseDto> getApplicationsByProjectId(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectId);
        }

        return applicationRepository.findAllByProjectIdWithTechStacks(projectId).stream()
                .map(ApplicationMapper::mapToApplicationResponse)
                .collect(Collectors.toList());
    }

    /**
     * 3. 지원 취소 (PENDING 상태일 때만 가능)
     */
    @Override
    public void deleteApplication(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.APPLY_NOT_FOUND, applicationId));

        // 지원자 본인만 취소 가능하도록 권한 검증
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!application.getApplicant().getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLY_CANNOT_CANCEL);
        }

        applicationRepository.delete(application);
    }

    /**
     * 4. 내 신청 현황 조회 (PENDING, REJECTED 상태)
     */
    // ApplicationServiceImpl.java (예시)
    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponseDto> getMyPendingApplications(Long userId) {

        return applicationRepository.findAllPendingByApplicantIdExcludingDeleted(userId)
                .stream()
                .map(ApplicationResponseDto::from)
                .collect(Collectors.toList());
        // 💡 수락(ACCEPTED) 상태가 아닌 모든 지원서(PENDING, REJECTED)를 가져옵니다.
        // 그래야 강퇴당해서 REJECTED로 바뀐 내역이 이 목록에 나타납니다!
    }

    /**
     * 5. 지원서 승인 (방장용)
     */
    @Override
    public ApplicationResponseDto acceptApplication(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.APPLY_NOT_FOUND, applicationId));

        // 해당 프로젝트의 방장만 승인 가능
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!application.getProject().getOwner().getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLY_CANNOT_CANCEL, "이미 처리된 지원서입니다.");
        }

        Project project = application.getProject();

        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new BusinessException(ErrorCode.PROJECT_RECRUITMENT_FULL);
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(project.getId(), application.getApplicant().getId())) {
            throw new BusinessException(ErrorCode.APPLY_DUPLICATE, "이미 팀원으로 등록된 사용자입니다.");
        }

        // 1. 지원서 상태 ACCEPTED로 변경
        application.accept();

        // 2. project_members 테이블에 팀원으로 추가
        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(application.getApplicant())
                .role(MemberRole.MEMBER)
                .position(application.getPosition()) // ✅ 핵심: 지원서의 포지션을 새 멤버 정보로 복사
                .build();
        projectMemberRepository.save(newMember);

        // 3. 프로젝트 현재 인원 증가 (정원 충족 시 자동 CLOSED 처리)
        project.addMember();

        return ApplicationMapper.mapToApplicationResponse(application);
    }


    /**
     * 6. 지원서 거절 (방장용)
     */
    @Override
    public ApplicationResponseDto rejectApplication(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.APPLY_NOT_FOUND, applicationId));

        // 해당 프로젝트의 방장만 거절 가능
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!application.getProject().getOwner().getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLY_CANNOT_CANCEL, "이미 처리된 지원서입니다.");
        }

        application.reject();

        return ApplicationMapper.mapToApplicationResponse(application);
    }
}