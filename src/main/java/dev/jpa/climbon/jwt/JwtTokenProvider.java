package dev.jpa.climbon.jwt;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT(JSON Web Token)의 생성 / 파싱 / 서명 검증을 전담하는 컴포넌트.
 *
 * <p><b>왜 JWT인가?</b><br>
 * 세션 방식은 서버 메모리에 로그인 상태를 보관하므로 서버를 2대 이상으로 늘리면
 * "세션 불일치" 문제가 생깁니다(스티키 세션 / 세션 클러스터링이 필요).
 * JWT는 로그인 정보를 토큰 자체에 담아 클라이언트가 들고 다니므로
 * 서버는 상태를 갖지 않고(STATELESS) 수평 확장이 자유롭습니다.</p>
 *
 * <p>[면접 포인트] AccessToken을 짧게(30분), RefreshToken을 길게(14일) 나누는 이유:<br>
 * AccessToken은 매 요청마다 네트워크를 타므로 탈취 위험이 큽니다. 그래서 수명을 짧게 두고,
 * 대신 안전하게 보관하는 RefreshToken으로 조용히 재발급받아 사용자가 자주 로그인하지 않게 합니다.
 * 탈취되더라도 피해 시간이 30분으로 제한됩니다.</p>
 *
 * <p>[실무 팁] JJWT는 0.11.x와 0.12.x의 API가 다릅니다.
 * 이 프로젝트는 build.gradle에 명시된 <b>0.11.5</b> 기준으로 작성되어 있어
 * {@code Jwts.parserBuilder()}, {@code setClaims()} 같은 구(舊) API를 사용합니다.
 * 버전을 올린다면 이 클래스만 고치면 됩니다.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

  /** 서명에 사용할 비밀키 문자열. HS256은 최소 32byte(256bit) 이상이어야 합니다. */
  @Value("${jwt.secret}")
  private String secretKeyString;

  /** AccessToken 유효시간(ms). application.properties: jwt.access-token-validity */
  @Value("${jwt.access-token-validity}")
  private long accessTokenValidity;

  /** RefreshToken 유효시간(ms). application.properties: jwt.refresh-token-validity */
  @Value("${jwt.refresh-token-validity}")
  private long refreshTokenValidity;

  /** 문자열 비밀키를 HMAC-SHA 서명용 객체로 변환해 둔 것. 매번 만들면 낭비이므로 1회만 생성합니다. */
  private SecretKey secretKey;

  /** 회원번호를 담는 커스텀 클레임 키 */
  public static final String CLAIM_NO = "no";
  /** 로그인 아이디를 담는 커스텀 클레임 키 */
  public static final String CLAIM_ID = "id";
  /** 회원등급을 담는 커스텀 클레임 키 */
  public static final String CLAIM_GRADE = "grade";

  /**
   * 의존성 주입(@Value)이 모두 끝난 뒤 실행되는 초기화 메서드.
   *
   * <p>생성자에서 처리하지 않는 이유: 생성자 시점에는 @Value 주입이 아직 끝나지 않아
   * secretKeyString이 null이기 때문입니다. 그래서 @PostConstruct를 씁니다.</p>
   */
  @PostConstruct
  protected void init() {
    this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
  }

  /* ======================================================================
   * 토큰 생성
   * ====================================================================== */

  /**
   * API 인가에 사용할 AccessToken을 생성합니다.
   *
   * <p>페이로드에 회원번호(no) / 아이디(id) / 등급(grade)을 담아두면
   * 요청마다 DB를 다시 조회하지 않고도 "누가, 어떤 권한으로" 요청했는지 알 수 있습니다.</p>
   *
   * <p>[면접 포인트] JWT 페이로드는 서명만 되어 있을 뿐 <b>암호화되어 있지 않습니다</b>.
   * Base64 디코딩만 하면 누구나 내용을 볼 수 있으므로 비밀번호·주민번호 같은
   * 민감정보는 절대 넣지 않습니다. 여기 담은 값은 노출돼도 무해한 식별정보뿐입니다.</p>
   *
   * @param no    회원번호(PK). subject에도 같이 넣어 표준 규격을 지킵니다.
   * @param id    로그인 아이디
   * @param grade 회원등급 (1~5 관리자 / 6~10 일반회원)
   * @return 서명이 끝난 JWT 문자열
   */
  public String createAccessToken(Long no, String id, int grade) {
    Claims claims = Jwts.claims().setSubject(String.valueOf(no));
    claims.put(CLAIM_NO, no);
    claims.put(CLAIM_ID, id);
    claims.put(CLAIM_GRADE, grade);

    Date now = new Date();
    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(now)                                                 // iat: 발급시각
        .setExpiration(new Date(now.getTime() + accessTokenValidity))     // exp: 만료시각
        .signWith(secretKey, SignatureAlgorithm.HS256)                    // 위변조 방지 서명
        .compact();
  }

  /**
   * AccessToken 재발급에만 사용하는 RefreshToken을 생성합니다.
   *
   * <p>수명이 길기 때문에 탈취 시 피해가 크므로 <b>식별자(no)만</b> 담습니다.
   * 등급 같은 값은 재발급 시점에 DB에서 다시 읽어야 "등급 강등/정지"가 즉시 반영됩니다.</p>
   *
   * @param no 회원번호(PK)
   * @return 서명이 끝난 RefreshToken 문자열
   */
  public String createRefreshToken(Long no) {
    Claims claims = Jwts.claims().setSubject(String.valueOf(no));
    claims.put(CLAIM_NO, no);

    Date now = new Date();
    return Jwts.builder()
        .setClaims(claims)
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + refreshTokenValidity))
        .signWith(secretKey, SignatureAlgorithm.HS256)
        .compact();
  }

  /* ======================================================================
   * 토큰 검증 / 파싱
   * ====================================================================== */

  /**
   * 토큰의 서명과 만료 여부를 검사합니다.
   *
   * <p>[실무 팁] 실패 원인별로 로그를 다르게 남겨두면
   * "만료라서 401인지 / 키가 안 맞아서 401인지"를 로그만 보고 구분할 수 있어
   * 장애 대응 시간이 크게 줄어듭니다.</p>
   *
   * @param token 검사할 JWT 문자열
   * @return 유효하면 true, 아니면 false (예외를 밖으로 던지지 않습니다)
   */
  public boolean validateToken(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
      return true;
    } catch (SignatureException e) {
      log.warn("[JWT] 서명이 일치하지 않습니다. 위변조 가능성이 있습니다.");
    } catch (ExpiredJwtException e) {
      log.warn("[JWT] 만료된 토큰입니다. /auth/reissue 로 재발급이 필요합니다.");
    } catch (MalformedJwtException e) {
      log.warn("[JWT] 토큰 형식이 올바르지 않습니다.");
    } catch (UnsupportedJwtException e) {
      log.warn("[JWT] 지원하지 않는 형식의 토큰입니다.");
    } catch (IllegalArgumentException e) {
      log.warn("[JWT] 토큰 값이 비어 있습니다.");
    }
    return false;
  }

  /**
   * 토큰 본문(Claims)을 꺼냅니다.
   *
   * <p>만료 예외가 나더라도 {@code e.getClaims()}로 내용을 돌려줍니다.
   * 재발급 로직에서 "만료된 AccessToken의 주인이 누구였는지"를 알아야 하기 때문입니다.</p>
   */
  public Claims getClaims(String token) {
    try {
      return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
    } catch (ExpiredJwtException e) {
      return e.getClaims();
    }
  }

  /** 토큰에서 회원번호(no)를 꺼냅니다. */
  public Long getMemberNo(String token) {
    Claims claims = getClaims(token);
    Object no = claims.get(CLAIM_NO);
    // 숫자 클레임은 JSON 파싱 결과가 Integer/Long으로 갈릴 수 있어 Number로 받아 통일합니다.
    if (no instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(claims.getSubject());
  }

  /** 토큰에서 회원등급(grade)을 꺼냅니다. RefreshToken에는 없으므로 null일 수 있습니다. */
  public Integer getGrade(String token) {
    Object grade = getClaims(token).get(CLAIM_GRADE);
    return (grade instanceof Number number) ? number.intValue() : null;
  }

  /** 토큰에서 로그인 아이디(id)를 꺼냅니다. */
  public String getId(String token) {
    return getClaims(token).get(CLAIM_ID, String.class);
  }

  /**
   * 토큰 내용을 Spring Security가 이해하는 {@link Authentication} 객체로 변환합니다.
   *
   * <p>principal에 <b>회원번호(Long)</b>를 넣는 이유: 컨트롤러에서
   * {@code @AuthenticationPrincipal Long memberNo} 또는 {@code SecurityUtil.getMemberNo()}로
   * 곧바로 꺼내 쓰기 위함입니다. 매번 DB에서 회원을 조회하는 비용을 아낄 수 있습니다.</p>
   *
   * <p>권한(authority)은 두 종류를 넣습니다.<br>
   * - {@code ROLE_ADMIN} / {@code ROLE_MEMBER} : hasRole() 로 큰 단위 제어<br>
   * - {@code GRADE_6} 같은 등급 권한 : 세밀한 제어 및 {@link SecurityUtil#getGrade()}에서 역산</p>
   */
  public Authentication getAuthentication(String token) {
    Long no = getMemberNo(token);
    Integer grade = getGrade(token);

    List<GrantedAuthority> authorities = new ArrayList<>();
    // 등급 1~5는 관리자, 6 이상은 일반회원 (schema.sql의 MEMBER.GRADE 주석 기준)
    authorities.add(new SimpleGrantedAuthority(
        (grade != null && grade <= 5) ? "ROLE_ADMIN" : "ROLE_MEMBER"));
    if (grade != null) {
      authorities.add(new SimpleGrantedAuthority(SecurityUtil.GRADE_PREFIX + grade));
    }

    // credentials(비밀번호)는 이미 JWT 서명으로 검증이 끝났으므로 null을 넣습니다.
    return new UsernamePasswordAuthenticationToken(no, null, authorities);
  }

  /* ======================================================================
   * 만료시각 계산 (DB 저장용)
   * ====================================================================== */

  /** RefreshToken 유효시간(ms)을 반환합니다. */
  public long getRefreshTokenValidity() {
    return refreshTokenValidity;
  }

  /**
   * RefreshToken의 만료 일시를 'yyyy-MM-dd HH:mm:ss' 문자열로 계산합니다.
   *
   * <p>MEMBER_REFRESH_TOKEN.EXPIRE_DATE 컬럼이 VARCHAR2(19)이므로
   * 프로젝트 공통 날짜 포맷에 맞춰 문자열로 저장합니다.</p>
   */
  public String getRefreshTokenExpireDate() {
    long expireAt = System.currentTimeMillis() + refreshTokenValidity;
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(expireAt));
  }
}
