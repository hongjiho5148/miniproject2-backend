package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.Category;
import com.rookies5.Backend_MATE.entity.enums.OnOffline;
import com.rookies5.Backend_MATE.entity.enums.Position;
import com.rookies5.Backend_MATE.entity.enums.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// JWT/Cloudinary/Flyway 등 전체 스프링 컨텍스트를 띄우지 않고 JPA 계층 + QueryDSL 설정만 로드
@DataJpaTest
@Import(QuerydslConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class ProjectRepositoryQuerydslTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;

    private Project createProject(Category category, OnOffline onOffline, ProjectStatus status,
                                   String title, String content, Set<String> techStacks) {
        Project project = Project.builder()
                .owner(owner)
                .category(category)
                .title(title)
                .content(content)
                .recruitCount(4)
                .onOffline(onOffline)
                .status(status)
                .endDate(LocalDate.now().plusDays(10))
                .techStacks(techStacks)
                .build();
        return projectRepository.save(project);
    }

    private void givenProjects() {
        owner = userRepository.save(User.builder()
                .email("owner@mate.com")
                .password("encoded")
                .nickname("방장")
                .phoneNumber("01000000000")
                .position(Position.BE)
                .build());

        createProject(Category.PROJECT, OnOffline.ONLINE, ProjectStatus.RECRUITING,
                "Spring 백엔드 스터디원 모집", "JPA와 QueryDSL을 함께 공부합니다", Set.of("Spring", "JPA"));
        createProject(Category.STUDY, OnOffline.OFFLINE, ProjectStatus.RECRUITING,
                "React 프론트 스터디원 모집", "리액트 기초를 함께 공부합니다", Set.of("React"));
        createProject(Category.PROJECT, OnOffline.ONLINE, ProjectStatus.CLOSED,
                "마감된 프로젝트", "이미 모집이 끝났습니다", Set.of("Spring"));
    }

    @Test
    @DisplayName("카테고리로만 필터링하면 해당 카테고리의 프로젝트만 조회된다")
    void filterByCategoryOnly() {
        givenProjects();

        Page<Project> result = projectRepository.findAllWithFilters(
                Category.PROJECT, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(p -> p.getCategory() == Category.PROJECT);
    }

    @Test
    @DisplayName("키워드는 제목·내용·기술스택 중 어디에 있든 대소문자 구분 없이 매칭된다")
    void filterByKeywordAcrossFieldsIgnoreCase() {
        givenProjects();

        Page<Project> result = projectRepository.findAllWithFilters(
                null, "spring", null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Project::getTitle)
                .containsExactlyInAnyOrder("Spring 백엔드 스터디원 모집", "마감된 프로젝트");
    }

    @Test
    @DisplayName("기술스택 필터는 정확히 일치하는 스택을 가진 프로젝트만 조회한다")
    void filterByTechStack() {
        givenProjects();

        Page<Project> result = projectRepository.findAllWithFilters(
                null, null, null, null, "React", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("React 프론트 스터디원 모집");
    }

    @Test
    @DisplayName("여러 조건을 동시에 조합하면 모든 조건을 만족하는 프로젝트만 조회된다")
    void filterByMultipleConditionsCombined() {
        givenProjects();

        Page<Project> result = projectRepository.findAllWithFilters(
                Category.PROJECT, null, OnOffline.ONLINE, ProjectStatus.CLOSED, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("마감된 프로젝트");
    }

    @Test
    @DisplayName("조건이 전부 null이면 소프트 삭제되지 않은 전체 프로젝트가 조회된다")
    void noFilterReturnsAllActiveProjects() {
        givenProjects();

        Page<Project> result = projectRepository.findAllWithFilters(
                null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
    }
}
