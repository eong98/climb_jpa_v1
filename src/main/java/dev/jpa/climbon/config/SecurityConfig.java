package dev.jpa.climbon.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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

import dev.jpa.climbon.jwt.JwtAuthenticationEntryPoint;
import dev.jpa.climbon.jwt.JwtAuthenticationFilter;
import dev.jpa.climbon.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

/**
 * Spring Security 설정.
 *
 * <p>이 프로젝트는 <b>세션을 쓰지 않는 JWT 기반 REST API 서버</b>입니다.
 * 브라우저가 직접 폼을 제출하는 전통적인 웹앱이 아니라 React가 axios로 호출하므로
 * 기본 설정(폼 로그인, 세션, CSRF 토큰)을 모두 걷어내고 다시 구성합니다.</p>
 *
 * <p>[면접 포인트] <b>왜 CSRF를 disable 하는가?</b><br>
 * CSRF 공격은 브라우저가 쿠키를 <b>자동으로</b> 실어 보내는 성질을 악용합니다.
 * 이 서버는 인증 정보를 쿠키가 아니라 {@code Authorization} 헤더로 받고,
 * 헤더는 공격자 사이트가 임의로 붙일 수 없으므로 CSRF가 성립하지 않습니다.
 * 반대로 <b>토큰을 쿠키에 담는다면 CSRF 방어를 반드시 켜야 합니다.</b></p>
 *
 * <p>[실무 팁] {@code setAllowedOriginPatterns("*")}는 개발 편의용입니다.
 * 자격증명 허용(allowCredentials=true)과 와일드카드 Origin은 원래 함께 쓸 수 없어
 * Spring이 패턴 방식으로 우회해 주는 것인데, 운영 배포 시에는
 * 실제 도메인 목록으로 좁혀야 합니다.</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  /**
   * 비밀번호 암호화 방식으로 BCrypt를 등록합니다.
   *
   * <p>[면접 포인트] BCrypt를 쓰는 이유<br>
   * 1) <b>단방향 해시</b>라 DB가 유출돼도 원문 비밀번호를 복원할 수 없습니다.<br>
   * 2) 해시마다 <b>salt가 자동 포함</b>되어 같은 비밀번호도 매번 다른 값이 됩니다
   *    → 레인보우 테이블 공격 무력화.<br>
   * 3) 일부러 <b>느리게</b> 설계되어(기본 10라운드 ≈ 100ms) 초당 대입 횟수를 떨어뜨립니다.
   *    빠른 SHA-256 계열을 비밀번호에 쓰면 안 되는 이유가 이것입니다.</p>
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * HTTP 보안 필터 체인 구성.
   *
   * <p>permitAll 경로는 "비로그인 방문자도 서비스를 둘러볼 수 있어야 한다"는
   * 기획 요구에서 나옵니다. 암장 검색·리뷰 열람·상품 목록은 로그인 없이 보이고,
   * 찜·리뷰 작성·주문처럼 <b>내 데이터를 건드리는 순간</b>부터 인증을 요구합니다.</p>
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // CORS 설정 적용 (아래 corsConfigurationSource 빈을 사용)
        .cors(Customizer.withDefaults())

        // CSRF 비활성화 (헤더 기반 토큰 인증이므로 불필요)
        .csrf(AbstractHttpConfigurer::disable)

        // 폼 로그인 / HTTP Basic 비활성화 — 로그인은 /member/login API가 담당
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)

        // 세션을 만들지 않는다 (STATELESS): 서버 확장이 자유로워집니다.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 인증 실패(401) 응답을 JSON으로 통일
        .exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))

        .authorizeHttpRequests(auth -> auth
            // --- CORS 사전 요청(preflight)은 인증 대상이 아니므로 전부 허용 ---
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // --- 인증/회원가입 관련 (토큰이 없는 상태에서 호출해야 함) ---
            .requestMatchers(
                "/member/login",
                "/member/join",
                "/member/check/**",
                "/member/check-nickname/**",
                "/auth/reissue"
            ).permitAll()

            // --- 비로그인 열람 허용: 조회(GET)만 열고 등록/수정/삭제는 막는다 ---
            .requestMatchers(HttpMethod.GET,
                "/gym/**",          // 암장 목록/상세/지도/인기
                "/review/gym/**",   // 암장 리뷰 목록
                "/board/list",      // 커뮤니티 목록
                "/board/{no}",      // 커뮤니티 상세
                "/board/*/comment", // 댓글 목록
                "/product/**",      // 상품 목록/상세
                "/notice/**",       // 공지 목록/상세
                "/attach/list/**"   // 첨부 목록
            ).permitAll()

            // --- 파일 다운로드 / 정적 이미지 (브라우저가 직접 요청하므로 헤더를 붙일 수 없다) ---
            // [실무 팁] <img src>와 <a download>는 axios를 거치지 않아 Authorization 헤더가
            //          붙지 않습니다. 이 경로를 막으면 이미지가 전부 깨져 보입니다.
            .requestMatchers(
                "/download",
                "/attach/storage/**",
                "/gym/storage/**",
                "/product/storage/**",
                "/member/storage/**"
            ).permitAll()

            // --- AI 검색/챗봇/리뷰요약: 비로그인 체험 허용 ---
            //   개인화가 필요한 /ai/level-report, /ai/recommend/** 는 아래 authenticated() 대상입니다.
            //   (실력 분석·맞춤 추천은 "누구의 등반일지인지"를 알아야 하므로 로그인 필수)
            .requestMatchers(
                "/ai/search",
                "/ai/chat",
                "/ai/chat/**",
                "/ai/review-summary/**"
            ).permitAll()

            // --- 그 외 모든 요청은 로그인 필요 ---
            .anyRequest().authenticated()
        )

        // JWT 필터를 아이디/비밀번호 인증 필터 '앞'에 끼워 넣는다.
        // 이 순서여야 컨트롤러에 도달하기 전에 SecurityContext가 채워지고,
        // 인가(authorizeHttpRequests) 판단에도 로그인 정보가 반영됩니다.
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * React 개발 서버(5173) 등 다른 출처에서의 호출을 허용하는 CORS 설정.
   *
   * <p>브라우저는 origin이 다른 서버로의 요청을 기본 차단합니다(동일 출처 정책).
   * 프론트(5173)와 백엔드(9200)는 포트가 다르므로 서로 다른 출처이며,
   * 서버가 "허용한다"는 응답 헤더를 보내 줘야 통신이 성립합니다.</p>
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    // 개발 단계에서는 모든 출처 허용. 운영 배포 시 실제 도메인으로 교체할 것.
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "*"));
    config.setAllowCredentials(true);

    // 프론트가 읽을 수 있게 노출할 응답 헤더 (기본적으로는 일부 헤더만 읽을 수 있음)
    config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
    config.setMaxAge(3600L); // preflight 결과를 1시간 캐시해 불필요한 OPTIONS 요청을 줄인다

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
