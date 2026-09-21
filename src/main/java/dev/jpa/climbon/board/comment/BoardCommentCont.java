package dev.jpa.climbon.board.comment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 게시글 댓글 컨트롤러. — {@code /board}
 *
 * <p>경로는 CONVENTIONS.md의 REST 명세를 그대로 따릅니다.
 * <pre>
 *   GET    /board/{bno}/comment   댓글 목록
 *   POST   /board/{bno}/comment   댓글 등록
 *   PUT    /board/comment/{no}    댓글 수정
 *   DELETE /board/comment/{no}    댓글 삭제
 * </pre>
 * 목록·등록은 <b>게시글에 종속</b>이라 {@code /board/{bno}/} 아래에 두고,
 * 수정·삭제는 댓글번호만으로 특정되므로 {@code /board/comment/{no}} 로 둡니다.
 * (REST에서 흔히 쓰는 "생성은 부모 경로, 수정/삭제는 자원 경로" 패턴입니다.)</p>
 *
 * <p>{@code BoardCont}와 같은 {@code /board} 접두사를 쓰지만
 * 댓글 로직만 따로 모아 파일이 비대해지는 것을 막았습니다.
 * {@code /board/{bno}/comment}와 {@code /board/comment/{no}}는
 * 두 번째 경로 조각이 리터럴 "comment"인지로 구분되어 충돌하지 않습니다.</p>
 */
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardCommentCont {

  private final BoardCommentService boardCommentService;

  /**
   * 댓글 목록.
   * <pre>GET /board/12/comment</pre>
   *
   * <p>댓글은 한 글에 수십 개 수준이라 페이징하지 않고 전체를 내려보냅니다.
   * 대댓글이 부모와 같은 페이지에 있어야 트리가 그려지는데,
   * 페이징하면 "부모는 1페이지, 자식은 2페이지" 같은 상황이 생겨
   * 프론트가 트리를 조립할 수 없기 때문입니다.</p>
   */
  @GetMapping("/{bno}/comment")
  public ResponseEntity<List<BoardCommentDTO>> getComments(@PathVariable("bno") Long bno) {
    return ResponseEntity.ok(boardCommentService.getComments(bno));
  }

  /**
   * 댓글 등록. (parentNo를 담아 보내면 대댓글)
   * <pre>POST /board/12/comment  {"content":"좋은 글이네요", "parentNo":null}</pre>
   */
  @PostMapping("/{bno}/comment")
  public ResponseEntity<Map<String, Object>> createComment(
      @PathVariable("bno") Long bno,
      @RequestBody BoardCommentDTO dto) {

    Long no = boardCommentService.createComment(bno, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "댓글이 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 댓글 수정. (작성자 본인만)
   * <pre>PUT /board/comment/34  {"content":"수정된 내용"}</pre>
   */
  @PutMapping("/comment/{no}")
  public ResponseEntity<Map<String, Object>> updateComment(
      @PathVariable("no") Long no,
      @RequestBody BoardCommentDTO dto) {

    boardCommentService.updateComment(no, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "댓글이 수정되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 댓글 삭제. (작성자 본인 또는 관리자)
   * <pre>DELETE /board/comment/34</pre>
   */
  @DeleteMapping("/comment/{no}")
  public ResponseEntity<Map<String, Object>> deleteComment(@PathVariable("no") Long no) {
    boardCommentService.deleteComment(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "댓글이 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }
}
