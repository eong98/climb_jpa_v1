package dev.jpa.climbon.jwt;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import dev.jpa.climbon.member.Member;
import dev.jpa.climbon.member.MemberRepository;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 토큰 재발급 / 로그아웃 컨트롤러.
 *
 * <p><b>재발급이 필요한 순간</b><br>
 * AccessToken은 30분이면 만료됩니다. 그때마다 사용자에게 다시 로그인하라고 하면
 * 서비스를 쓸 수가 없습니다. 그래서 프론트의 axios 인터셉터가 401을 감지하면
 * 보관 중인 RefreshToken으로 이 API를 호출해 조용히 새 토큰을 받아 옵니다.</p>
 *
 * <p>[면접 포인트] 재발급 시 검사하는 항목은 <b>세 가지</b>입니다.<br>
 * 1) 서명/만료가 유효한가 ({@code validateToken})<br>
 * 2) 서버가 발급했고 아직 살아 있는 토큰인가 (DB 조회 — 위조·폐기 토큰 차단)<br>
 * 3) 그 회원이 지금도 정상 상태인가 (DB 조회 — 정지/탈퇴 즉시 반영)<br>
 * 1번만 하면 로그아웃·강제탈퇴시킨 사용자가 계속 토큰을 받아 갈 수 있습니다.</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TokenCont {

  private final JwtTokenProvider jwtTokenProvider;
  private final MemberRefreshTokenRepository memberRefreshTokenRepository;
  private final MemberRepository memberRepository;

  /** 재발급 요청 본문. 전달값이 하나뿐이라 record로 간결하게 정의합니다. */
  public record ReissueRequest(String refreshToken) {
  }

  /**
   * 토큰 재발급 — POST /auth/reissue
   *
   * <p>요청 본문: {@code {refreshToken}} / 응답: {@code {accessToken, refreshToken}}</p>
   *
   * <p>새 RefreshToken까지 함께 내려주는 것이 <b>Refresh Token Rotation</b>입니다.
   * 한 번 쓴 RefreshToken은 DB에서 지워지므로, 탈취범이 같은 토큰을 다시 쓰면 거절됩니다.</p>
   */
  @PostMapping("/reissue")
  @Transactional
  public ResponseEntity<Map<String, Object>> reissue(@RequestBody ReissueRequest request) {
    String refreshToken = request.refreshToken();

    // 1) 서명 + 만료 검증
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      return unauthorized("리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요.");
    }

    // 2) 서버가 보관 중인 토큰과 일치하는지 확인 (탈취/재사용 차단)
    MemberRefreshToken saved = memberRefreshTokenRepository.findByRefreshToken(refreshToken)
        .orElse(null);
    if (saved == null) {
      return unauthorized("이미 폐기되었거나 알 수 없는 토큰입니다.");
    }

    // 3) 회원 상태 재확인 — 등급 변경/정지/탈퇴를 즉시 반영하기 위해 DB에서 다시 읽습니다.
    //    (토큰 안의 grade를 그대로 믿으면 강등된 관리자가 계속 관리자로 행동할 수 있습니다.)
    Member member = memberRepository.findById(saved.getMno()).orElse(null);
    if (member == null || member.getStatus() == null || member.getStatus() != 1) {
      memberRefreshTokenRepository.deleteByMno(saved.getMno());
      return unauthorized("이용할 수 없는 계정입니다.");
    }

    // 4) 새 토큰 발급 + 기존 토큰 폐기(회전)
    String newAccessToken = jwtTokenProvider.createAccessToken(
        member.getNo(), member.getId(), member.getGrade());
    String newRefreshToken = jwtTokenProvider.createRefreshToken(member.getNo());

    String now = Tool.getDate();
    memberRefreshTokenRepository.deleteByMno(member.getNo());
    memberRefreshTokenRepository.deleteExpired(now);
    memberRefreshTokenRepository.save(MemberRefreshToken.builder()
        .mno(member.getNo())
        .refreshToken(newRefreshToken)
        .expireDate(jwtTokenProvider.getRefreshTokenExpireDate())
        .cdate(now)
        .build());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("accessToken", newAccessToken);
    body.put("refreshToken", newRefreshToken);
    return ResponseEntity.ok(body);
  }

  /**
   * 로그아웃 — POST /auth/logout
   *
   * <p>[실무 팁] JWT는 서버가 회수할 수 없으므로 "로그아웃 = AccessToken 폐기"가 불가능합니다.
   * 대신 RefreshToken을 DB에서 지워 <b>더 이상 재발급이 안 되게</b> 만들고,
   * 프론트는 저장소(localStorage 등)에서 토큰을 지웁니다.
   * 남아 있는 AccessToken은 최대 30분 뒤 자연 만료됩니다.</p>
   */
  @PostMapping("/logout")
  @Transactional
  public ResponseEntity<Map<String, Object>> logout(@RequestBody ReissueRequest request) {
    String refreshToken = request.refreshToken();

    memberRefreshTokenRepository.findByRefreshToken(refreshToken)
        .ifPresent(token -> memberRefreshTokenRepository.deleteByMno(token.getMno()));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "로그아웃 되었습니다.");
    return ResponseEntity.ok(body);
  }

  /** 401 응답을 같은 형태로 만들어 주는 헬퍼 */
  private ResponseEntity<Map<String, Object>> unauthorized(String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", message);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }
}
