package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, ProjectRepositoryCustom {
    // 작성자(Owner)의 ID로 프로젝트 목록을 찾는 메서드 정의
    @Query("SELECT p FROM Project p WHERE p.deletedAt IS NULL AND p.owner.id = :ownerId")
    List<Project> findAllByOwnerId(@Param("ownerId") Long ownerId);

    //프로젝트 삭제 (Soft Delete)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Project p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id = :projectId")
    void softDeleteById(@Param("projectId") Long projectId);

    //회원 탈퇴 -> 프로젝트 삭제 (Soft Delete)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Project p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.owner.id = :ownerId AND p.deletedAt IS NULL")
    void softDeleteAllByOwnerId(@Param("ownerId") Long ownerId);

    // 카테고리·키워드·온오프라인·모집상태·기술스택 통합 검색은 QueryDSL(ProjectRepositoryImpl.findAllWithFilters)로 이전됨

    // 키워드로 프로젝트 제목, 내용, 기술 스택 통합 검색 (삭제된 프로젝트 제외) - 기존 호환용
    @Query("SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN p.techStacks ts " +
            "WHERE p.deletedAt IS NULL AND (" +
            "p.title LIKE %:keyword% OR " +
            "p.content LIKE %:keyword% OR " +
            "ts LIKE %:keyword%)")
    Page<Project> searchProjects(@Param("keyword") String keyword, Pageable pageable);

    //관리자용
    @Query(value = "SELECT * FROM projects ORDER BY created_at DESC",
            countQuery = "SELECT count(*) FROM projects",
            nativeQuery = true)
    Page<Project> findAllIncludingDeleted(Pageable pageable);

    @Query(value = "SELECT * FROM projects WHERE project_id = :id", nativeQuery = true)
    Optional<Project> findByIdIncludingDeleted(@Param("id") Long id);

    @Query(value = "SELECT count(*) FROM projects", nativeQuery = true)
    long countIncludingDeleted();

    @Query(value = "SELECT * FROM projects", nativeQuery = true)
    List<Project> findAllIncludingDeletedList();

    // ✅ 추가: 삭제되지 않은 프로젝트 전체 페이징 조회 (최신순)
    @Query("SELECT p FROM Project p WHERE p.deletedAt IS NULL")
    Page<Project> findAllActiveProjects(Pageable pageable);
}