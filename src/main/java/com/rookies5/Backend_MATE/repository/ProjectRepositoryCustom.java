package com.rookies5.Backend_MATE.repository;

import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.enums.Category;
import com.rookies5.Backend_MATE.entity.enums.OnOffline;
import com.rookies5.Backend_MATE.entity.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectRepositoryCustom {

    /**
     * 카테고리·키워드·온오프라인·모집상태·기술스택을 조합해 동적으로 필터링하는 통합 검색.
     * 파라미터가 null(또는 빈 문자열)이면 해당 조건은 검색에서 제외된다.
     */
    Page<Project> findAllWithFilters(
            Category category,
            String keyword,
            OnOffline onOffline,
            ProjectStatus status,
            String techStack,
            Pageable pageable
    );
}
