package com.rookies5.Backend_MATE.controller;

import com.rookies5.Backend_MATE.dto.response.ProjectResponseDto;
import com.rookies5.Backend_MATE.dto.response.UserResponseDto;
import com.rookies5.Backend_MATE.entity.AdminLog;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.mapper.ProjectMapper;
import com.rookies5.Backend_MATE.mapper.UserMapper;
import com.rookies5.Backend_MATE.repository.AdminLogRepository;
import com.rookies5.Backend_MATE.repository.ProjectRepository;
import com.rookies5.Backend_MATE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AdminLogRepository adminLogRepository;

    private final int PAGE_SIZE = 5;

    // -------------------------
    // ✅ 로그 추가 유틸
    // -------------------------
    private void addLog(String message) {
        // 로그 100개 이상이면 가장 오래된 로그 삭제
        if (adminLogRepository.count() >= 100) {
            AdminLog oldest = adminLogRepository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) {
                adminLogRepository.delete(oldest);
            }
        }
        // 새 로그 저장
        AdminLog log = AdminLog.builder()
                .action(message)
                .createdAt(LocalDateTime.now())
                .build();
        adminLogRepository.save(log);
    }


    // ================== 대시보드 ==================
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0") int userPage,
                            @RequestParam(defaultValue = "0") int projectPage,
                            @RequestParam(defaultValue = "0") int logPage,
                            Model model) {

        // 회원 페이징
        Page<UserResponseDto> users = userRepository
                .findAllIncludingDeleted(PageRequest.of(userPage, PAGE_SIZE))
                .map(user -> {
                    UserResponseDto dto = UserMapper.mapToUserResponse(user);
                    dto.setDeleted(user.getDeletedAt() != null);
                    return dto;
                });

        // 프로젝트 페이징
        Page<ProjectResponseDto> projects = projectRepository
                .findAllIncludingDeleted(PageRequest.of(projectPage, PAGE_SIZE))
                .map(ProjectMapper::mapToResponse);

        // 로그 페이징 (최신순)
        Pageable logPageable = PageRequest.of(logPage, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminLog> logs = adminLogRepository.findAll(logPageable);

        model.addAttribute("userCount", userRepository.countIncludingDeleted());
        model.addAttribute("projectCount", projectRepository.countIncludingDeleted());
        model.addAttribute("users", users);
        model.addAttribute("projects", projects);
        model.addAttribute("logs", logs);

        return "admin/dashboard";
    }

    // ================== 회원 ==================
    @GetMapping("/users")
    public String userManagement(@RequestParam(defaultValue = "0") int userPage, Model model) {
        Page<UserResponseDto> users = userRepository
                .findAllIncludingDeleted(PageRequest.of(userPage, PAGE_SIZE))
                .map(user -> {
                    UserResponseDto dto = UserMapper.mapToUserResponse(user);
                    dto.setDeleted(user.getDeletedAt() != null);
                    return dto;
                });
        model.addAttribute("users", users);
        return "admin/users";
    }

    @GetMapping("/logs")
    public String logManagement(@RequestParam(defaultValue = "0") int logPage,
                                @RequestParam(required = false) String keyword,
                                Model model) {
        int LOG_PAGE_SIZE = 20; // 1페이지당 20개
        Pageable pageable = PageRequest.of(logPage, LOG_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminLog> logs = adminLogRepository.findAll(pageable);
        model.addAttribute("logs", logs);
        model.addAttribute("keyword", keyword);
        return "admin/logs"; // logs.html
    }

    // 회원 상세 (모달용)
    @GetMapping("/users/{id}")
    @ResponseBody
    public UserResponseDto getUserDetail(@PathVariable Long id) {
        return userRepository.findByIdIncludingDeleted(id)
                .map(UserMapper::mapToUserResponse)
                .orElseThrow(() -> new RuntimeException("해당 회원을 찾을 수 없습니다."));
    }

    @PostMapping("/users/delete/{id}")
    @Transactional
    public String deleteUser(@PathVariable Long id,
                             @RequestParam(defaultValue = "0") int userPage,
                             @RequestParam(required = false) String redirectPage) {

        User user = userRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new RuntimeException("회원 없음"));

        // 회원 소프트 삭제
        userRepository.softDeleteById(id);
        addLog(user.getNickname() + "님 계정을 삭제했습니다.");

        // 회원이 작성한 프로젝트도 소프트 삭제
        List<Project> projects = projectRepository.findAllByOwnerId(id);
        for (Project p : projects) {
            projectRepository.softDeleteById(p.getId());
            addLog(p.getTitle() + " 프로젝트를 삭제했습니다.");
        }

        if ("users".equals(redirectPage)) {
            return "redirect:/admin/users?userPage=" + userPage;
        }
        return "redirect:/admin/dashboard?userPage=" + userPage;
    }

    @PostMapping("/users/restore/{id}")
    public String restoreUser(@PathVariable Long id,
                              @RequestParam(defaultValue = "0") int userPage,
                              @RequestParam(required = false) String redirectPage) {

        User user = userRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new RuntimeException("회원 없음"));

        user.setDeletedAt(null);
        userRepository.save(user);
        addLog(user.getNickname() + "님 계정을 복구했습니다.");

        if ("users".equals(redirectPage)) {
            return "redirect:/admin/users?userPage=" + userPage;
        }
        return "redirect:/admin/dashboard?userPage=" + userPage;
    }

    // ================== 프로젝트 ==================
    @Transactional(readOnly = true)
    @GetMapping("/projects")
    public String projectManagement(@RequestParam(defaultValue = "0") int projectPage, Model model) {
        Page<ProjectResponseDto> projects = projectRepository
                .findAllIncludingDeleted(PageRequest.of(projectPage, PAGE_SIZE))
                .map(ProjectMapper::mapToResponse);
        model.addAttribute("projects", projects);
        return "admin/projects";
    }

    // 프로젝트 상세 (모달용)
    @GetMapping("/projects/{id}")
    @ResponseBody
    public ProjectResponseDto getProjectDetail(@PathVariable Long id) {
        Project project = projectRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new RuntimeException("해당 프로젝트를 찾을 수 없습니다."));
        return ProjectMapper.mapToResponse(project);
    }

    @PostMapping("/projects/delete/{id}")
    @Transactional
    public String deleteProject(@PathVariable Long id,
                                @RequestParam(defaultValue = "0") int projectPage,
                                @RequestParam(required = false) String redirectPage) {

        Project project = projectRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new RuntimeException("프로젝트 없음"));
        projectRepository.softDeleteById(id);
        addLog(project.getTitle() + " 프로젝트를 삭제했습니다.");

        if ("projects".equals(redirectPage)) {
            return "redirect:/admin/projects?projectPage=" + projectPage;
        }
        return "redirect:/admin/dashboard?projectPage=" + projectPage;
    }

    @PostMapping("/projects/restore/{id}")
    public String restoreProject(@PathVariable Long id,
                                 @RequestParam(defaultValue = "0") int projectPage,
                                 @RequestParam(required = false) String redirectPage) {

        Project project = projectRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new RuntimeException("프로젝트 없음"));

        project.setDeletedAt(null);
        projectRepository.save(project);
        addLog(project.getTitle() + " 프로젝트를 복구했습니다.");

        if ("projects".equals(redirectPage)) {
            return "redirect:/admin/projects?projectPage=" + projectPage;
        }
        return "redirect:/admin/dashboard?projectPage=" + projectPage;
    }

    // ================== 검색 ==================
    @GetMapping("/search")
    public String search(@RequestParam String keyword,
                         @RequestParam(defaultValue = "0") int userPage,
                         @RequestParam(defaultValue = "0") int projectPage,
                         @RequestParam(required = false, defaultValue = "dashboard") String redirectPage,
                         Model model) {

        // 회원 검색
        List<UserResponseDto> userList = userRepository.findAllIncludingDeletedList().stream()
                .filter(u -> u.getNickname().contains(keyword) || u.getEmail().contains(keyword))
                .map(user -> {
                    UserResponseDto dto = UserMapper.mapToUserResponse(user);
                    dto.setDeleted(user.getDeletedAt() != null);
                    return dto;
                })
                .toList();
        Page<UserResponseDto> users = new PageImpl<>(userList, PageRequest.of(userPage, PAGE_SIZE), userList.size());

        // 프로젝트 검색
        List<ProjectResponseDto> projectList = projectRepository.findAllIncludingDeletedList().stream()
                .filter(p -> p.getTitle().contains(keyword))
                .map(ProjectMapper::mapToResponse)
                .toList();
        Page<ProjectResponseDto> projects = new PageImpl<>(projectList, PageRequest.of(projectPage, PAGE_SIZE), projectList.size());

        if ("users".equals(redirectPage)) {
            model.addAttribute("users", users);
            model.addAttribute("keyword", keyword);
            return "admin/users";
        }

        if ("projects".equals(redirectPage)) {
            model.addAttribute("projects", projects);
            model.addAttribute("keyword", keyword);
            return "admin/projects";
        }

        // 대시보드 검색
        Pageable logPageable = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminLog> logs = adminLogRepository.findAll(logPageable);

        model.addAttribute("userCount", userRepository.countIncludingDeleted());
        model.addAttribute("projectCount", projectRepository.countIncludingDeleted());
        model.addAttribute("users", users);
        model.addAttribute("projects", projects);
        model.addAttribute("logs", logs);
        model.addAttribute("keyword", keyword);

        return "admin/dashboard";
    }

    // ================== 로그인 페이지 ==================
    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }
}