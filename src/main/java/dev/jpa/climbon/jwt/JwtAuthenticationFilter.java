package dev.jpa.climbon.jwt;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 요청을 한 번씩 가로채서 {@code Authorization: Bearer <token>} 헤더를 해석하고,
 * 유효한 토큰이면 SecurityContext에 로그인 정보를 심어주는 필터.
 *
 * <p><b>왜 OncePerRequestFilter인가?</b><br>
 * forward / include 같은 내부 디스패치가 일어나면 일반 Filter는 여러 번 실행될 수 있습니다.
 * OncePerRequestFilter를 상속하면 한 요청당 정확히 1회 실행이 보장되어
 * 인증 로직이 중복 수행되는 것을 막습니다.</p>
 *
 * <p>[면접 포인트] <b>토큰이 없거나 틀려도 여기서 예외를 던지지 않습니다.</b><br>
 * 이 필터의 책임은 "인증 정보가 있으면 담아준다"까지입니다.
 * 접근을 막을지 말지(인가)는 SecurityConfig의 permitAll / authenticated 설정이 판단하고,
 * 최종 거절 응답은 {@link JwtAuthenticationEntryPoint}가 만듭니다.
 * 만약 여기서 예외를 던져버리면 비로그인도 볼 수 있어야 하는
 * 암장 목록(/gym/**) 같은 permitAll 경로까지 전부 401이 되어 버립니다.
 * → <b>관심사의 분리(인증 정보 세팅 / 인가 판단 / 실패 응답)</b>가 핵심입니다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    // 1) Authorization 헤더에서 순수 토큰만 추출
    String token = resolveToken(request);

    // 2) 토큰이 유효할 때만 인증 정보를 심는다 (없으면 '비로그인 사용자'로 그냥 통과)
    if (token != null && jwtTokenProvider.validateToken(token)) {
      try {
        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (Exception e) {
        // 클레임 구조가 예상과 다른 경우에도 요청 자체는 막지 않는다.
        // (permitAll 경로는 계속 열려 있어야 하므로)
        log.warn("[JWT] 인증 정보 구성 실패 - 비로그인으로 처리합니다. {}", e.getMessage());
        SecurityContextHolder.clearContext();
      }
    }

    // 3) 성공/실패와 무관하게 다음 필터로 넘긴다
    filterChain.doFilter(request, response);
  }

  /**
   * "Authorization: Bearer eyJhbGciOi..." 형식에서 앞의 "Bearer "(7글자)를 잘라냅니다.
   *
   * @return 토큰 문자열. 헤더가 없거나 형식이 다르면 null
   */
  private String resolveToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      return bearer.substring(7).trim();
    }
    return null;
  }
}
