package com.rookies5.Backend_MATE.service;

import com.rookies5.Backend_MATE.config.QuerydslConfig;
import com.rookies5.Backend_MATE.entity.*;
import com.rookies5.Backend_MATE.entity.enums.*;
import com.rookies5.Backend_MATE.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 회원탈퇴 시 "내가 쓴 게시글에 남이 단 댓글"이 함께 정리되는지 검증한다.
// 이 정리가 빠지면: 게시글은 숨겨지는데 댓글은 살아남아 PUT으로는 여전히 노출/수정되고,
// DELETE는 comment.getPost().getProject() 접근에서 EntityNotFoundException으로 죽어 영구히 못 지우는 고아 데이터가 된다.
@DataJpaTest
@Import(QuerydslConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class UserWithdrawalCommentCleanupTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private BoardPostRepository boardPostRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private EntityManager em;

    private User createUser(String email, String nickname) {
        return userRepository.save(User.builder()
                .email(email).password("encoded").nickname(nickname)
                .phoneNumber("010" + (int) (Math.random() * 100000000))
                .position(Position.BE).build());
    }

    @Test
    @DisplayName("게시글 작성자가 탈퇴하면, 그 글에 다른 사람이 단 댓글도 함께 소프트 삭제된다")
    void othersCommentOnMyPostIsCleanedUpWhenIWithdraw() {
        // given: postAuthor가 쓴 글에 commenter가 댓글을 닮
        User postAuthor = createUser("postauthor@mate.com", "글쓴이");
        User commenter = createUser("commenter@mate.com", "댓글러");

        Project project = projectRepository.save(Project.builder()
                .owner(postAuthor).category(Category.PROJECT).title("테스트 프로젝트")
                .content("내용").recruitCount(4).onOffline(OnOffline.ONLINE)
                .endDate(LocalDate.now().plusDays(10)).build());

        BoardPost post = boardPostRepository.save(BoardPost.builder()
                .project(project).author(postAuthor).title("글쓴이의 글").content("본문")
                .type(BoardPostType.GENERAL).viewCount(0).build());

        Comment comment = commentRepository.save(Comment.builder()
                .post(post).author(commenter).content("댓글러의 댓글").build());

        em.flush();

        // when: 회원탈퇴 시 실행되는 것과 동일한 순서로 정리 (댓글 -> 게시글)
        commentRepository.softDeleteAllByPostAuthorId(postAuthor.getId());
        boardPostRepository.softDeleteAllByAuthorId(postAuthor.getId());

        em.flush();
        em.clear();

        // then: 게시글도, 남의 댓글도 더 이상 조회되지 않는다 (고아 데이터로 안 남음)
        Optional<BoardPost> foundPost = boardPostRepository.findById(post.getId());
        Optional<Comment> foundComment = commentRepository.findById(comment.getId());

        assertThat(foundPost).isEmpty();
        assertThat(foundComment)
                .as("게시글 작성자 탈퇴 시, 그 글에 달린 남의 댓글도 함께 정리되어야 더 이상 조회되지 않는다")
                .isEmpty();
    }

    @Test
    @DisplayName("탈퇴 순서를 반대로 하면(게시글 먼저) 서브쿼리가 대상을 못 찾아 댓글이 남는다 - 순서가 중요함을 증명")
    void wrongOrderLeavesOrphanComment() {
        User postAuthor = createUser("postauthor2@mate.com", "글쓴이2");
        User commenter = createUser("commenter2@mate.com", "댓글러2");

        Project project = projectRepository.save(Project.builder()
                .owner(postAuthor).category(Category.PROJECT).title("테스트 프로젝트2")
                .content("내용").recruitCount(4).onOffline(OnOffline.ONLINE)
                .endDate(LocalDate.now().plusDays(10)).build());

        BoardPost post = boardPostRepository.save(BoardPost.builder()
                .project(project).author(postAuthor).title("글쓴이2의 글").content("본문")
                .type(BoardPostType.GENERAL).viewCount(0).build());

        Comment comment = commentRepository.save(Comment.builder()
                .post(post).author(commenter).content("댓글러2의 댓글").build());

        em.flush();

        // 게시글을 먼저 지워버리면 (잘못된 순서)
        boardPostRepository.softDeleteAllByAuthorId(postAuthor.getId());
        commentRepository.softDeleteAllByPostAuthorId(postAuthor.getId());

        em.flush();
        em.clear();

        // 댓글은 지워지지 않고 고아로 남는다 - 이게 바로 순서를 지켜야 하는 이유
        assertThat(commentRepository.findById(comment.getId())).isPresent();
    }
}
