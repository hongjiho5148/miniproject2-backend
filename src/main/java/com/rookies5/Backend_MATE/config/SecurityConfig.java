package com.rookies5.Backend_MATE.config;

import com.rookies5.Backend_MATE.security.JwtAccessDeniedHandler;
import com.rookies5.Backend_MATE.security.JwtAuthenticationEntryPoint;
import com.rookies5.Backend_MATE.security.JwtAuthenticationFilter;
import com.rookies5.Backend_MATE.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 💡 1. CORS 설정 활성화 (우리가 만든 corsConfigurationSource를 가져다 씁니다)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 401 에러 처리
                        .accessDeniedHandler(jwtAccessDeniedHandler) // 403 에러 처리
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)

                // API 명세서 기반 권한 맵핑
                .authorizeHttpRequests(auth -> auth
                        // 1. 누구나 접근 가능 (GUEST)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/check-phone", "/api/users/check-nickname").permitAll()

                        // 프로젝트 목록/상세 조회 허용
                        .requestMatchers(HttpMethod.GET, "/api/projects/**").permitAll()

                        // 💡 업로드 파일(프로필 이미지 등) 누구나 접근 가능
                        .requestMatchers("/uploads/**").permitAll()

                        // 3. 관리자 페이지 및 기타 설정들...
                        .requestMatchers("/admin/signup", "/admin/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // 나머지는 로그인 필수
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    // 💡 2. 프론트엔드 연결을 위한 CORS 세부 규칙 정의
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 프론트엔드 주소 허용
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));

        // 허용할 HTTP 메서드 (GET, POST 등)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 허용할 헤더 (모든 헤더 허용)
        configuration.setAllowedHeaders(List.of("*"));

        // 프론트엔드에서 응답 헤더의 Authorization 값을 읽을 수 있도록 노출 허용
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

        // 쿠키나 인증 정보(JWT)를 포함한 요청 허용 (true 필수)
        configuration.setAllowCredentials(true);

        // 모든 API 경로(/**)에 대해 위에서 설정한 규칙을 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}