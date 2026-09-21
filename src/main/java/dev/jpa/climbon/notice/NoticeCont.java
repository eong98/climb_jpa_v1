package dev.jpa.climbon.notice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 공지사항 REST 컨트롤러. (CONVENTIONS.md의 /notice 명세 그대로 구현)
 *
 * <p>목록/상세는 비로그인도 볼 수 있고(GET permitAll),
 * 등록/수정/삭제는 관리자만 가능합니다.</p>
 *
 * <p>[실무 팁] {@code GET /notice/list}와 {@code GET /notice/{no}}는 경로 깊이가 같습니다.
 * 스프링은 <b>변수 경로보다 고정 문자열 경로를 우선</b> 매칭하므로 "list"가 {no}로
 * 잘못 들어가지 않습니다. 다만 {@code @PathVariable Long no} 타입 덕분에
 * 숫자가 아닌 값은 애초에 매칭되지 않아 이중으로 안전합니다.</p>
 */
@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NoticeCont {

  private final NoticeService noticeService;

  /**
   * 공지 목록 — GET /notice/list?word=&type=&page=0&size=10
   */
  @GetMapping("/list")
  public ResponseEntity<PageResponse<NoticeDTO>> list(
      @RequestParam(name = "word", defaultValue = "") String word,
      @RequestParam(name = "type", required = false) Integer type,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    Page<NoticeDTO> result = noticeService.getList(word, type, page, size);
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 메인 화면용 최신 공지 — GET /notice/latest?size=5
   */
  @GetMapping("/latest")
  public ResponseEntity<List<NoticeDTO>> latest(
      @RequestParam(name = "size", defaultValue = "5") int size) {

    return ResponseEntity.ok(noticeService.getLatest(size));
  }

  /**
   * 공지 상세 — GET /notice/{no} (조회수 +1)
   */
  @GetMapping("/{no}")
  public ResponseEntity<NoticeDTO> read(@PathVariable("no") Long no) {
    NoticeDTO dto = noticeService.read(no);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }

  /**
   * 공지 등록 — POST /notice (관리자)
   *
   * <p>작성자 번호는 요청 본문이 아니라 토큰에서 꺼냅니다.
   * 요청 값을 믿으면 다른 관리자 이름으로 글을 쓸 수 있습니다.</p>
   */
  @PostMapping
  public ResponseEntity<?> create(@RequestBody NoticeDTO noticeDTO) {
    if (!SecurityUtil.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("관리자 권한이 필요합니다."));
    }

    try {
      NoticeDTO saved = noticeService.create(noticeDTO, SecurityUtil.getMemberNo());
      return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(message(e.getMessage()));
    }
  }

  /**
   * 공지 수정 — PUT /notice/{no} (관리자)
   */
  @PutMapping("/{no}")
  public ResponseEntity<?> update(
      @PathVariable("no") Long no,
      @RequestBody NoticeDTO noticeDTO) {

    if (!SecurityUtil.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("관리자 권한이 필요합니다."));
    }

    NoticeDTO updated = noticeService.update(no, noticeDTO);
    if (updated == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(updated);
  }

  /**
   * 공지 삭제 — DELETE /notice/{no} (관리자, 논리삭제)
   *
   * @return 처리 건수
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<?> delete(@PathVariable("no") Long no) {
    if (!SecurityUtil.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("관리자 권한이 필요합니다."));
    }

    int count = noticeService.delete(no);
    if (count == 0) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(count);
  }

  /** 오류 응답 형태를 통일하는 헬퍼 */
  private Map<String, Object> message(String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", message);
    return body;
  }
}
