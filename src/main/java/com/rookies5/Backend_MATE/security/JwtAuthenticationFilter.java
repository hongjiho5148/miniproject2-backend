package com.rookies5.Backend_MATE.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 요청 앞에서 토큰을 검사하는 보안 필터
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 요청 헤더에서 JWT 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰이 있고, 유효하다면 신원 확인 진행
        try {
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 토큰이 정상이면 유저 정보(Authentication)를 꺼내옴
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                // Spring Security의 보안 바구니(SecurityContext)에 이 유저를 담아둠
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // 💡 [핵심 수정] 토큰이 유효하지 않아도(AUTH_003 등) 에러를 던지지 않습니다.
            // 인증 정보를 비우고(Anonymous 유저로 취급) 다음 필터로 넘깁니다.
            // 이렇게 해야 permitAll() 설정된 상세페이지/이미지 경로가 정상 작동합니다.
            log.warn("유효하지 않은 토큰 요청입니다 (상세조회 및 이미지 허용을 위해 통과): {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        // 3. 다음 검사 단계로 이동 (에러가 나도 무조건 실행되어야 함)
        filterChain.doFilter(request, response);
    }

    // 헤더에서 "Bearer "를 떼고 순수 토큰만 가져오는 메서드
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}