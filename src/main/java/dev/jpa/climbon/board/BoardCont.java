package dev.jpa.climbon.board;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * 커뮤니티 게시글 컨트롤러. — {@code /board}
 *
 * <p>경로는 CONVENTIONS.md의 REST 명세를 그대로 따릅니다.
 * 컨트롤러는 <b>파라미터를 받아 서비스에 넘기고 응답 포맷을 맞추는 일만</b> 합니다.
 * 권한 검사·집계·트랜잭션은 전부 Service의 책임입니다.
 * (컨트롤러에 로직이 들어가면 같은 로직을 다른 진입점에서 재사용할 수 없습니다.)</p>
 *
 * <p>댓글 API는 파일이 비대해지지 않도록 {@code BoardCommentCont}로 분리했습니다.</p>
 */
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardCont {

  private final BoardService boardService;

  /**
   * 게시글 목록.
   * <pre>GET /board/list?type=0&amp;word=파트너&amp;searchType=title&amp;sido=서울&amp;sort=new&amp;page=0&amp;size=10</pre>
   *
   * <p>공지({@code NOTICE_YN='Y'})는 정렬과 무관하게 항상 최상단에 붙습니다.
   * 정렬(sort)은 "new(최신) / view(조회순) / like(좋아요순)" 키워드로만 받습니다.
   * 프론트가 컬럼명을 직접 보내게 하면 DB 구조가 API 계약에 새어 나가고,
   * 존재하지 않는 필드명이 오면 500이 납니다.</p>
   *
   * @param type       0:자유 1:파트너구함 2:암장후기 3:질문답변 4:중고거래 (null이면 전체)
   * @param searchType title | content | writer
   */
  @GetMapping("/list")
  public ResponseEntity<PageResponse<BoardDTO>> getBoards(
      @RequestParam(name = "type", required = false) Integer type,
      @RequestParam(name = "word", required = false) String word,
      @RequestParam(name = "searchType", required = false) String searchType,
      @RequestParam(name = "sido", required = false) String sido,
      @RequestParam(name = "sort", defaultValue = "new") String sort,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    // 정렬은 쿼리 안의 ORDER BY가 전담하므로 Pageable에는 Sort를 싣지 않습니다.
    // (Sort까지 실으면 쿼리의 ORDER BY 뒤에 덧붙어 공지 고정 순서가 흐트러질 수 있습니다.)
    Page<BoardDTO> result = boardService.getBoards(
        type, word, searchType, sido, sort, PageRequest.of(page, size));

    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 게시글 상세. (조회수 +1)
   * <pre>GET /board/12</pre>
   */
  @GetMapping("/{no}")
  public ResponseEntity<BoardDTO> getBoard(@PathVariable("no") Long no) {
    return ResponseEntity.ok(boardService.getBoard(no));
  }

  /**
   * 인기글.
   * <pre>GET /board/popular?size=5</pre>
   *
   * <p>최근 7일 내 작성된 글 중 (조회수 + 좋아요×5)가 높은 순입니다.</p>
   */
  @GetMapping("/popular")
  public ResponseEntity<List<BoardDTO>> getPopularBoards(
      @RequestParam(name = "size", defaultValue = "5") int size) {
    return ResponseEntity.ok(boardService.getPopularBoards(size));
  }

  /**
   * 게시글 등록.
   * <pre>POST /board</pre>
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> createBoard(@RequestBody BoardDTO dto) {
    Long no = boardService.createBoard(dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "게시글이 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 게시글 수정. (작성자 본인 또는 관리자 — 아니면 403)
   * <pre>PUT /board/12</pre>
   */
  @PutMapping("/{no}")
  public ResponseEntity<Map<String, Object>> updateBoard(
      @PathVariable("no") Long no,
      @RequestBody BoardDTO dto) {

    boardService.updateBoard(no, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "게시글이 수정되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 게시글 삭제. (작성자 본인 또는 관리자 — 아니면 403)
   * <pre>DELETE /board/12</pre>
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<Map<String, Object>> deleteBoard(@PathVariable("no") Long no) {
    boardService.deleteBoard(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "게시글이 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 좋아요 토글.
   * <pre>POST /board/12/like</pre>
   *
   * <p>등록/취소를 서버가 판단하므로 프론트는 파라미터 없이 호출만 하면 됩니다.
   * 응답 {@code {liked:boolean, count:number}}을 그대로 화면에 반영하면
   * 다른 탭에서 눌러 둔 경우에도 상태가 어긋나지 않습니다.</p>
   */
  @PostMapping("/{no}/like")
  public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable("no") Long no) {
    return ResponseEntity.ok(boardService.toggleLike(no));
  }
}
