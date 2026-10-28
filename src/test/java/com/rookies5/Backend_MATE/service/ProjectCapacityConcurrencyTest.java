package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.entity.Application;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.*;
import com.rookies5.Backend_MATE.repository.*;
import com.rookies5.Backend_MATE.security.CustomUserDetails;
import com.rookies5.Backend_MATE.service.impl.ApplicationServiceImpl;
import com.rookies5.Backend_MATE.service.impl.ProjectMemberServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 팀원 수(currentCount)를 바꾸는 요청이 동시에 들어와도 정원과 인원 수가 어긋나지 않는지 검증한다.
// 실제 요청처럼 서로 다른 트랜잭션이 겹쳐야 하므로, 테스트 트랜잭션 없이 커밋되는 DB와 여러 스레드를 사용한다.
@DataJpaTest
@Import({QuerydslConfig.class, ApplicationServiceImpl.class, ProjectMemberServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class ProjectCapacityConcurrencyTest {

    @Autowired private ApplicationServiceImpl applicationService;
    @Autowired private ProjectMemberServiceImpl projectMemberService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        // 소프트 삭제된 행까지 포함해 모든 테이블을 비운다 (반복 실행 간 데이터가 섞이지 않게)
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'", String.class)
                .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private User createUser(String name) {
        return userRepository.save(User.builder()
                .email(name + "@mate.com").password("x").nickname(name)
                .phoneNumber(String.format("010%08d", Math.abs(name.hashCode()) % 100000000))
                .position(Position.BE).build());
    }

    private Project createProject(User owner, int recruitCount) {
        return projectRepository.save(Project.builder()
                .owner(owner).category(Category.PROJECT).title("동시성 테스트 프로젝트")
                .content("내용").recruitCount(recruitCount).onOffline(OnOffline.ONLINE)
                .endDate(LocalDate.now().plusDays(10)).build());
    }

    private Long createApplication(Project project, String applicantName) {
        return applicationRepository.save(Application.builder()
                .project(project).applicant(createUser(applicantName)).position(Position.BE)
                .message("지원합니다. 열심히 하겠습니다.").build()).getId();
    }

    private void loginAs(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    // 모든 작업을 같은 순간에 시작시키고, 실패(정원 초과 등)는 정상 동작으로 보고 삼킨다.
    private int runConcurrently(User actor, List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier startTogether = new CyclicBarrier(tasks.size());
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(() -> {
                loginAs(actor);
                try {
                    startTogether.await(10, TimeUnit.SECONDS);
                    task.call();
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                    // 정원 초과 등으로 거절되는 요청은 정상 동작이다
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return succeeded.get();
    }

    @RepeatedTest(5)
    @DisplayName("정원이 2명인 프로젝트에 지원서 8개를 동시에 승인해도 팀원은 정확히 2명만 늘어난다")
    void concurrentAcceptsNeverExceedRecruitCount() throws Exception {
        int recruitCount = 2;
        User owner = createUser("owner");
        Project project = createProject(owner, recruitCount);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Long applicationId = createApplication(project, "applicant" + i);
            tasks.add(() -> {
                applicationService.acceptApplication(applicationId);
                return null;
            });
        }

        int accepted = runConcurrently(owner, tasks);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(projectMemberRepository.count())
                .as("정원(%d명)을 넘어 팀원이 추가되면 안 된다", recruitCount).isEqualTo(recruitCount);
        assertThat(accepted).isEqualTo(recruitCount);
        assertThat(reloaded.getCurrentCount()).isEqualTo(recruitCount);
        assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    }

    @RepeatedTest(20)
    @DisplayName("승인과 퇴출이 동시에 일어나도 프로젝트의 인원 수가 실제 팀원 수와 일치한다")
    void currentCountMatchesMembersWhenAcceptAndRemoveRace() throws Exception {
        User owner = createUser("owner");
        Project project = createProject(owner, 10);

        // 팀원 1명이 이미 있는 상태에서 시작한다 (순차 승인)
        loginAs(owner);
        applicationService.acceptApplication(createApplication(project, "existing"));
        SecurityContextHolder.clearContext();
        Long existingMemberId = projectMemberRepository.findAll().get(0).getId();

        List<Callable<Void>> tasks = new ArrayList<>();
        tasks.add(() -> {
            projectMemberService.removeMember(existingMemberId, owner.getId());
            return null;
        });
        for (int i = 0; i < 4; i++) {
            Long applicationId = createApplication(project, "newcomer" + i);
            tasks.add(() -> {
                applicationService.acceptApplication(applicationId);
                return null;
            });
        }

        runConcurrently(owner, tasks);

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount())
                .as("currentCount는 실제 활성 팀원 수와 같아야 한다")
                .isEqualTo((int) projectMemberRepository.count());
    }
}
