package com.rookies5.Backend_MATE.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rookies5.Backend_MATE.entity.Project;
import com.rookies5.Backend_MATE.entity.QProject;
import com.rookies5.Backend_MATE.entity.enums.Category;
import com.rookies5.Backend_MATE.entity.enums.OnOffline;
import com.rookies5.Backend_MATE.entity.enums.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Project> findAllWithFilters(
            Category category,
            String keyword,
            OnOffline onOffline,
            ProjectStatus status,
            String techStack,
            Pageable pageable
    ) {
        QProject project = QProject.project;

        BooleanBuilder where = new BooleanBuilder();
        where.and(project.deletedAt.isNull());

        if (category != null) {
            where.and(project.category.eq(category));
        }
        if (onOffline != null) {
            where.and(project.onOffline.eq(onOffline));
        }
        if (status != null) {
            where.and(project.status.eq(status));
        }
        if (StringUtils.hasText(techStack)) {
            where.and(project.techStacks.any().equalsIgnoreCase(techStack));
        }
        if (StringUtils.hasText(keyword)) {
            where.and(
                    project.title.containsIgnoreCase(keyword)
                            .or(project.content.containsIgnoreCase(keyword))
                            .or(project.techStacks.any().containsIgnoreCase(keyword))
            );
        }

        List<Project> content = queryFactory
                .selectFrom(project)
                .distinct()
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(toOrderSpecifiers(pageable, project))
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(project.countDistinct())
                .from(project)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // Pageable의 Sort를 QueryDSL OrderSpecifier로 변환 (필드명을 하드코딩하지 않고 그대로 위임)
    private OrderSpecifier<?>[] toOrderSpecifiers(Pageable pageable, QProject project) {
        PathBuilder<Project> entityPath = new PathBuilder<>(Project.class, project.getMetadata());

        return pageable.getSort().stream()
                .map(order -> {
                    Order direction = order.isAscending() ? Order.ASC : Order.DESC;
                    return new OrderSpecifier<>(
                            direction,
                            entityPath.getComparable(order.getProperty(), Comparable.class)
                    );
                })
                .toArray(OrderSpecifier[]::new);
    }
}
