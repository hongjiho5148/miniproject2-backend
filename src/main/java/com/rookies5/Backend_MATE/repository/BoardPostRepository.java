package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.entity.BoardPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {
    // 프로젝트 ID로 게시글 찾기 (페이징 지원)
    // 목록 응답에서 작성자 닉네임·프로필이미지를 매번 꺼내 쓰므로, author를 함께 즉시 로딩해 N+1을 방지한다.
    @EntityGraph(attributePaths = "author")
    Page<BoardPost> findAllByProjectId(Long projectId, Pageable pageable);

    // 기존: 프로젝트 ID로 게시글 찾기 + 작성일 최신순 정렬
    List<BoardPost> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);

    // 프로젝트 ID로 게시글 ID 목록 조회 (댓글 삭제용)
    List<BoardPost> findAllByProjectId(Long projectId);

    //프로젝트 삭제 -> 게시글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BoardPost b SET b.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE b.project.id = :projectId AND b.deletedAt IS NULL")
    void softDeleteAllByProjectId(@Param("projectId") Long projectId);

    // 게시글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BoardPost b SET b.deletedAt = CURRENT_TIMESTAMP WHERE b.id = :postId AND b.deletedAt IS NULL")
    void softDeleteById(@Param("postId") Long postId);

    //회원 탈퇴 -> 게시글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BoardPost b SET b.deletedAt = CURRENT_TIMESTAMP WHERE b.author.id = :userId AND b.deletedAt IS NULL")
    void softDeleteAllByAuthorId(@Param("userId") Long userId);

    //방장 멤버 강제 탈퇴 -> 게시글 삭제
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BoardPost b SET b.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE b.project.id = :projectId AND b.author.id = :authorId AND b.deletedAt IS NULL")
    void softDeleteAllByProjectIdAndAuthorId(@Param("projectId") Long projectId, @Param("authorId") Long authorId);

}