package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByPostIdOrderByCreatedAtAsc(Long postId);

    //프로젝트 삭제 -> 댓글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE c.post.id IN (SELECT b.id FROM BoardPost b WHERE b.project.id = :projectId) " +
            "AND c.deletedAt IS NULL")
    void softDeleteAllByProjectId(@Param("projectId") Long projectId);

    //게시글 삭제 -> 댓글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE c.post.id = :postId AND c.deletedAt IS NULL")
    void softDeleteAllByPostId(@Param("postId") Long postId);

    //댓글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.id = :commentId AND c.deletedAt IS NULL")
    void softDeleteById(@Param("commentId") Long commentId);

    //회원 탈퇴 -> 댓글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.author.id = :userId AND c.deletedAt IS NULL")
    void softDeleteAllByAuthorId(@Param("userId") Long userId);

    //회원 탈퇴 -> 그 회원이 쓴 게시글에 달린 (다른 사람 포함) 댓글 삭제
    //반드시 boardPostRepository.softDeleteAllByAuthorId보다 먼저 호출해야 함 (게시글이 먼저 삭제되면 @Where 필터에 걸려 서브쿼리가 대상을 못 찾음)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE c.post.id IN (SELECT b.id FROM BoardPost b WHERE b.author.id = :postAuthorId) " +
            "AND c.deletedAt IS NULL")
    void softDeleteAllByPostAuthorId(@Param("postAuthorId") Long postAuthorId);

    //방장 멤버 강제 탈퇴 -> 댓글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE c.post.id IN (SELECT b.id FROM BoardPost b WHERE b.project.id = :projectId) " + // b.deletedAt 조건 제거
            "AND c.author.id = :authorId " +
            "AND c.deletedAt IS NULL")
    void softDeleteAllByProjectIdAndAuthorId(@Param("projectId") Long projectId, @Param("authorId") Long authorId);
}

