package dev.jpa.climbon.climblog;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 등반 일지 컨트롤러. — {@code /climblog}
 *
 * <p>모든 엔드포인트가 로그인 사용자 본인의 데이터만 다룹니다.
 * 그래서 경로에 회원번호가 들어가지 않습니다({@code /climblog/my}).
 * 경로에 회원번호를 두면 번호를 바꿔 남의 기록을 요청해 볼 여지가 생깁니다.</p>
 */
@RestController
@RequestMapping("/climblog")
@RequiredArgsConstructor
public class ClimbLogCont {

  private final ClimbLogService climbLogService;

  /**
   * 내 등반 일지 목록.
   * <pre>GET /climblog/my?from=2026-01-01&amp;to=2026-03-31&amp;page=0&amp;size=10</pre>
   *
   * <p>from/to는 선택 항목입니다. 달력 화면은 월 단위로,
   * 목록 화면은 기간 없이 최신순으로 호출합니다.</p>
   */
  @GetMapping("/my")
  public ResponseEntity<PageResponse<ClimbLogDTO>> getMyLogs(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    Page<ClimbLogDTO> result = climbLogService.getMyLogs(from, to, page, size);
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 내 등반 통계.
   * <pre>GET /climblog/stats</pre>
   *
   * <p>총 등반일수 · 완등률 · 최고 난이도 · 월별 성장 그래프 · 난이도별 분포를 한 번에 반환합니다.
   * 마이페이지의 통계 대시보드가 이 응답 하나로 전부 그려집니다.</p>
   *
   * <p><b>주의</b>: 이 매핑은 {@code /climblog/{no}} 보다 <b>위에</b> 있어야 합니다.
   * 아래에 두면 "stats"가 {@code {no}} 로 해석돼 숫자 변환 오류(400)가 납니다.</p>
   */
  @GetMapping("/stats")
  public ResponseEntity<ClimbLogStatsDTO> getMyStats() {
    return ResponseEntity.ok(climbLogService.getMyStats());
  }

  /**
   * 일지 단건 조회.
   * <pre>GET /climblog/{no}</pre>
   */
  @GetMapping("/{no}")
  public ResponseEntity<ClimbLogDTO> getLog(@PathVariable("no") Long no) {
    return ResponseEntity.ok(climbLogService.getLog(no));
  }

  /**
   * 일지 등록.
   * <pre>POST /climblog</pre>
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> createLog(@RequestBody ClimbLogDTO dto) {
    Long no = climbLogService.createLog(dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "등반일지가 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 일지 수정.
   * <pre>PUT /climblog/{no}</pre>
   */
  @PutMapping("/{no}")
  public ResponseEntity<Map<String, Object>> updateLog(
      @PathVariable("no") Long no,
      @RequestBody ClimbLogDTO dto) {

    climbLogService.updateLog(no, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "등반일지가 수정되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 일지 삭제.
   * <pre>DELETE /climblog/{no}</pre>
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<Map<String, Object>> deleteLog(@PathVariable("no") Long no) {
    climbLogService.deleteLog(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "등반일지가 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }
}
