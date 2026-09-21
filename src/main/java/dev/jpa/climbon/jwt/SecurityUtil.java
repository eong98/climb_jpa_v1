package dev.jpa.climbon.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext에 담긴 로그인 정보를 어디서나 꺼내 쓰기 위한 정적 유틸리티.
 *
 * <p><b>왜 이런 클래스를 두는가?</b><br>
 * 컨트롤러마다 {@code @AuthenticationPrincipal}을 선언하고 형변환하는 코드가 반복됩니다.
 * 특히 Service 계층에서는 파라미터로 회원번호를 계속 넘겨줘야 해서 시그니처가 지저분해집니다.
 * 한 곳에 모아두면 "로그인 정보를 어떻게 꺼내는가"가 바뀌어도 이 파일만 고치면 됩니다.</p>
 *
 * <p>[면접 포인트] SecurityContextHolder는 기본적으로 <b>ThreadLocal</b>에 인증 정보를 보관합니다.
 * 요청 하나 = 스레드 하나이므로 다른 사용자의 정보가 섞이지 않습니다.
 * 반대로 비동기 스레드(@Async)나 별도 스레드풀에서는 값이 비어 있으니 주의해야 합니다.</p>
 *
 * <p>[실무 팁] 모든 메서드는 <b>비로그인일 때 예외 대신 null/false를 반환</b>합니다.
 * permitAll 경로(암장 목록 등)는 로그인 여부와 상관없이 동작해야 하고,
 * "로그인했으면 찜 여부까지 채워서 준다" 같은 분기를 간단히 쓰기 위함입니다.</p>
 */
public class SecurityUtil {

  /** 등급 권한 문자열 접두사. 예) 등급 6 -> "GRADE_6" */
  public static final String GRADE_PREFIX = "GRADE_";

  /** 유틸 클래스이므로 인스턴스를 만들지 못하게 막습니다. */
  private SecurityUtil() {
  }

  /**
   * 현재 SecurityContext의 Authentication을 반환합니다.
   *
   * <p>익명 사용자일 때 Spring Security는 principal이 "anonymousUser"(String)인
   * AnonymousAuthenticationToken을 넣어두기 때문에, 단순히 null 체크만 하면
   * 비로그인인데 로그인으로 오판할 수 있습니다. 그래서 principal 타입까지 확인합니다.</p>
   */
  private static Authentication getAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    // JwtTokenProvider.getAuthentication()이 principal에 회원번호(Long)를 넣습니다.
    if (!(authentication.getPrincipal() instanceof Long)) {
      return null;
    }
    return authentication;
  }

  /**
   * 로그인한 회원번호를 반환합니다.
   *
   * @return 회원번호. 비로그인이면 null
   */
  public static Long getMemberNo() {
    Authentication authentication = getAuthentication();
    return (authentication == null) ? null : (Long) authentication.getPrincipal();
  }

  /**
   * 로그인한 회원의 등급을 반환합니다.
   *
   * <p>토큰에서 뽑아 "GRADE_6" 형태의 권한으로 심어둔 값을 다시 숫자로 되돌립니다.
   * 등급을 별도 필드로 들고 다니지 않고 권한 목록에 태우면
   * {@code hasAuthority('GRADE_1')} 같은 선언적 보안 설정도 함께 쓸 수 있습니다.</p>
   *
   * @return 등급(1~10). 비로그인이거나 등급 정보가 없으면 null
   */
  public static Integer getGrade() {
    Authentication authentication = getAuthentication();
    if (authentication == null) {
      return null;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      String value = authority.getAuthority();
      if (value != null && value.startsWith(GRADE_PREFIX)) {
        try {
          return Integer.parseInt(value.substring(GRADE_PREFIX.length()));
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * 로그인 상태인지 확인합니다.
   *
   * @return 로그인했으면 true
   */
  public static boolean isLogin() {
    return getMemberNo() != null;
  }

  /**
   * 관리자인지 확인합니다.
   *
   * <p>schema.sql의 MEMBER.GRADE 정의상 <b>1~5가 관리자</b>, 6~10이 일반회원입니다.
   * 숫자 5를 코드 곳곳에 흩어놓으면 정책이 바뀔 때 전부 찾아 고쳐야 하므로
   * 판단 로직을 이 메서드 하나로 모읍니다.</p>
   *
   * @return 관리자면 true (비로그인이면 false)
   */
  public static boolean isAdmin() {
    Integer grade = getGrade();
    return grade != null && grade <= 5;
  }
}
