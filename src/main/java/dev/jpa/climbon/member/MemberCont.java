package dev.jpa.climbon.member;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 회원 REST 컨트롤러. (CONVENTIONS.md의 /member 명세 그대로 구현)
 *
 * <p><b>컨트롤러의 책임</b>은 "요청을 받아 서비스에 넘기고, 결과를 HTTP 상태코드로 번역"하는 것까지입니다.
 * 비즈니스 판단(중복 검사, 비밀번호 대조 등)은 전부 {@link MemberService}에 있습니다.</p>
 *
 * <p>[면접 포인트] 회원번호를 <b>경로 파라미터로 받지 않고</b>
 * {@link SecurityUtil#getMemberNo()}로 토큰에서 꺼내 쓰는 이유:
 * {@code PUT /member/3} 처럼 남의 번호를 넣어 요청하면 타인 정보를 수정할 수 있습니다.
 * 토큰에서 얻은 번호는 서명으로 보호되므로 위조할 수 없습니다. (IDOR 취약점 예방)</p>
 */
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberCont {

  private final MemberService memberService;

  /* ======================================================================
   * 인증 없이 접근 가능한 API (SecurityConfig의 permitAll 목록과 일치)
   * ====================================================================== */

  /**
   * 로그인 — POST /member/login
   *
   * <p>실패를 예외로 받아 401로 바꿉니다.
   * 200 + {success:false} 대신 상태코드로 알려야 프론트의 axios 인터셉터가
   * 공통 처리(토큰 재발급, 로그인 페이지 이동)를 할 수 있습니다.</p>
   *
   * @return {@code {accessToken, refreshToken, member}}
   */
  @PostMapping("/login")
  public ResponseEntity<Map<String, Object>> login(@RequestBody MemberDTO memberDTO) {
    try {
      Map<String, Object> result = memberService.login(memberDTO.getId(), memberDTO.getPassword());
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message(e.getMessage()));
    }
  }

  /**
   * 회원가입 — POST /member/join
   *
   * <p>생성 성공은 201 Created로 응답합니다. (REST 규약)</p>
   */
  @PostMapping("/join")
  public ResponseEntity<?> join(@RequestBody MemberDTO memberDTO) {
    try {
      MemberDTO saved = memberService.join(memberDTO);
      return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(message(e.getMessage()));
    }
  }

  /**
   * 아이디 중복확인 — GET /member/check/{id}
   *
   * @return 사용 가능하면 true
   */
  @GetMapping("/check/{id}")
  public ResponseEntity<Boolean> checkId(@PathVariable("id") String id) {
    return ResponseEntity.ok(memberService.isIdAvailable(id));
  }

  /**
   * 닉네임 중복확인 — GET /member/check-nickname/{nickname}
   *
   * @return 사용 가능하면 true
   */
  @GetMapping("/check-nickname/{nickname}")
  public ResponseEntity<Boolean> checkNickname(@PathVariable("nickname") String nickname) {
    return ResponseEntity.ok(memberService.isNicknameAvailable(nickname));
  }

  /* ======================================================================
   * 로그인이 필요한 API
   * ====================================================================== */

  /**
   * 내 정보 조회 — GET /member/me
   */
  @GetMapping("/me")
  public ResponseEntity<MemberDTO> me() {
    Long no = SecurityUtil.getMemberNo();
    if (no == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    MemberDTO dto = memberService.findByNo(no);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }

  /**
   * 내 정보 수정 — PUT /member/me
   */
  @PutMapping("/me")
  public ResponseEntity<?> updateMe(@RequestBody MemberDTO memberDTO) {
    Long no = SecurityUtil.getMemberNo();
    if (no == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      return ResponseEntity.ok(memberService.updateMe(no, memberDTO));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(message(e.getMessage()));
    }
  }

  /**
   * 비밀번호 변경 — PUT /member/password
   *
   * <p>요청 본문: {@code {currentPassword, newPassword}}</p>
   */
  @PutMapping("/password")
  public ResponseEntity<Map<String, Object>> changePassword(@RequestBody MemberDTO memberDTO) {
    Long no = SecurityUtil.getMemberNo();
    if (no == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      memberService.changePassword(no, memberDTO.getCurrentPassword(), memberDTO.getNewPassword());
      return ResponseEntity.ok(message("비밀번호가 변경되었습니다. 다시 로그인해 주세요."));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(message(e.getMessage()));
    }
  }

  /**
   * 회원 탈퇴 — DELETE /member/me (STATUS를 2로 바꾸는 논리삭제)
   *
   * @return 처리 건수
   */
  @DeleteMapping("/me")
  public ResponseEntity<Integer> withdraw() {
    Long no = SecurityUtil.getMemberNo();
    if (no == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(memberService.withdraw(no));
  }

  /* ======================================================================
   * 관리자 전용 API
   * ====================================================================== */

  /**
   * 관리자 회원 목록 — GET /member/list/admin?word=&grade=&status=&page=0&size=10
   *
   * <p>URL만 보면 관리자용이지만 URL은 누구나 호출할 수 있으므로
   * 반드시 서버에서 {@link SecurityUtil#isAdmin()}으로 등급을 확인합니다.
   * (프론트에서 메뉴를 숨기는 것은 보안이 아니라 편의 기능입니다.)</p>
   */
  @GetMapping("/list/admin")
  public ResponseEntity<PageResponse<MemberDTO>> listAdmin(
      @RequestParam(name = "word", defaultValue = "") String word,
      @RequestParam(name = "grade", required = false) Integer grade,
      @RequestParam(name = "status", required = false) Integer status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    if (!SecurityUtil.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Pageable pageable = PageRequest.of(page, size, Sort.by("no").descending());
    Page<MemberDTO> result = memberService.searchMembers(word, grade, status, pageable);
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 관리자 회원 상태 변경 — PUT /member/{no}/status
   *
   * <p>요청 본문: {@code {status: 0|1|2}}</p>
   */
  @PutMapping("/{no}/status")
  public ResponseEntity<?> changeStatus(
      @PathVariable("no") Long no,
      @RequestBody MemberDTO memberDTO) {

    if (!SecurityUtil.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("관리자 권한이 필요합니다."));
    }

    try {
      return ResponseEntity.ok(memberService.changeStatus(no, memberDTO.getStatus()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(message(e.getMessage()));
    }
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /** 프론트가 항상 같은 모양의 오류 메시지를 받도록 응답 형태를 통일합니다. */
  private Map<String, Object> message(String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", message);
    return body;
  }
}
