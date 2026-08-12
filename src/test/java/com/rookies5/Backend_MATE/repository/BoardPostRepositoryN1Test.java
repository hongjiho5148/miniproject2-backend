package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.entity.BoardPost;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.User;
import com.rookies5.Backend_MATE.entity.enums.BoardPostType;
import com.rookies5.Backend_MATE.entity.enums.Category;
import com.rookies5.Backend_MATE.entity.enums.OnOffline;
import com.rookies5.Backend_MATE.entity.enums.Position;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// BoardPostRepository.findAllByProjectId 목록 조회에서 작성자(author) 지연 로딩으로 인한
// N+1 쿼리가 실제로 발생하지 않는지 Hibernate 통계로 검증한다.
@DataJpaTest
@Import(QuerydslConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.sql.init.mode=never"
})
class BoardPostRepositoryN1Test {

    @Autowired
    private BoardPostRepository boardPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EntityManager em;

    private User createUser(String email, String nickname) {
        return userRepository.save(User.builder()
                .email(email)
                .password("encoded")
                .nickname(nickname)
                .phoneNumber("010" + (int) (Math.random() * 100000000))
                .position(Position.BE)
                .build());
    }

    @Test
    @DisplayName("게시글이 여러 명의 작성자에게 걸쳐 있어도, 목록 조회 시 작성자 조회 쿼리는 추가로 늘어나지 않는다")
    void findAllByProjectId_doesNotTriggerNPlus1ForAuthor() {
        // given: 서로 다른 작성자가 쓴 게시글 3개
        User owner = createUser("owner@mate.com", "방장");
        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .category(Category.PROJECT)
                .title("QueryDSL 스터디원 모집")
                .content("N+1 테스트용 프로젝트")
                .recruitCount(5)
                .onOffline(OnOffline.ONLINE)
                .endDate(LocalDate.now().plusDays(10))
                .build());

        for (int i = 0; i < 3; i++) {
            User author = createUser("author" + i + "@mate.com", "작성자" + i);
            boardPostRepository.save(BoardPost.builder()
                    .project(project)
                    .author(author)
                    .title("게시글 " + i)
                    .content("내용 " + i)
                    .type(BoardPostType.GENERAL)
                    .viewCount(0)
                    .build());
        }

        em.flush();
        em.clear();

        SessionFactory sessionFactory = em.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        // when: 게시글 목록 조회 후, 응답 매핑처럼 각 게시글의 작성자 닉네임까지 실제로 읽는다
        Page<BoardPost> page = boardPostRepository.findAllByProjectId(project.getId(), PageRequest.of(0, 10));
        for (BoardPost post : page.getContent()) {
            post.getAuthor().getNickname();
        }

        // then: 게시글 목록 조회 쿼리 + (count 쿼리) 정도로 끝나야 하고,
        // 게시글 수(3개)만큼 작성자 조회 쿼리가 추가로 발생하면 안 된다.
        long queryCount = statistics.getPrepareStatementCount();
        assertThat(page.getContent()).hasSize(3);
        assertThat(queryCount)
                .as("작성자 지연 로딩으로 인한 N+1 없이, 게시글 수와 무관하게 쿼리 수가 고정되어야 한다")
                .isLessThanOrEqualTo(3);
    }
}
