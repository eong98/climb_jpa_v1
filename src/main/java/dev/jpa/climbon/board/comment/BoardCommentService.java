package dev.jpa.climbon.board.comment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dev.jpa.climbon.board.Board;
import dev.jpa.climbon.board.BoardRepository;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 댓글 서비스.
 *
 * <p><b>[면접 포인트] 댓글 등록/삭제 때 BOARD.REPLY_CNT를 같은 트랜잭션에서 동기화하는 이유</b><br>
 * 게시글 목록은 "댓글 3" 배지를 보여줘야 하는데, 목록 20건마다
 * {@code (SELECT COUNT(*) FROM BOARD_COMMENT WHERE BNO = B.NO)} 스칼라 서브쿼리를 돌리면
 * 화면 한 번에 집계가 20번 일어납니다. 댓글 쓰기는 목록 조회에 비해 압도적으로 드무므로
 * <b>쓰기 시점에 한 번 COUNT 해서 BOARD 컬럼에 복사</b>해 둡니다.
 * (쓰기 비용 ↑, 읽기 비용 ↓↓ — 조회가 훨씬 많은 도메인의 정석적인 반정규화)</p>
 *
 * <p>대신 "집계값과 원본이 어긋날 수 있다"는 위험이 생기므로, 동기화를
 * {@link #syncReplyCnt(Long)} 한 메서드로만 하도록 좁히고
 * 댓글을 건드리는 모든 경로가 <b>같은 트랜잭션 안에서</b> 이 메서드를 거치게 했습니다.
 * 트랜잭션이 하나라 "댓글은 저장됐는데 카운트는 그대로"인 상태가 남지 않습니다.</p>
 *
 * <p><b>[실무 팁] 권한 위반을 왜 {@code ResponseStatusException}으로 던지나?</b><br>
 * 서비스가 {@code IllegalStateException}을 던지면 Spring 기본 처리로 <b>500</b>이 나갑니다.
 * "남의 댓글을 지우려 했다"는 것은 서버 장애가 아니라 <b>클라이언트의 권한 오류(403)</b>이므로
 * 상태 코드를 정확히 돌려줘야 프론트가 "권한이 없습니다"를 안내할 수 있습니다.
 * 프로젝트 규모가 커지면 도메인 예외 + {@code @RestControllerAdvice}로 옮기는 것이 정석이지만,
 * 여기서는 전역 핸들러가 없어 상태 코드를 명시적으로 지정했습니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardCommentService {

  private final BoardCommentRepository boardCommentRepository;
  private final BoardRepository boardRepository;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 게시글의 댓글 목록. (원댓글 → 대댓글 순)
   *
   * <p>정렬은 Repository의 {@code COALESCE(parentNo, no)} 트릭이 담당하고,
   * 여기서는 <b>로그인 사용자 기준의 파생값(editable)</b>만 채웁니다.
   * "수정 버튼을 보여줄지"는 조회한 사람이 누구냐에 따라 달라지므로 DB가 아니라 서버 로직의 몫입니다.</p>
   */
  public List<BoardCommentDTO> getComments(Long bno) {
    List<BoardCommentDTO> comments = boardCommentRepository.findCommentsByBno(bno);

    Long loginNo = SecurityUtil.getMemberNo();
    boolean admin = SecurityUtil.isAdmin();
    for (BoardCommentDTO dto : comments) {
      // 삭제된 댓글에는 수정/삭제 버튼이 필요 없습니다.
      boolean deleted = "Y".equals(dto.getIsdel());
      boolean mine = loginNo != null && loginNo.equals(dto.getMno());
      dto.setEditable(!deleted && (mine || admin));
    }
    return comments;
  }

  /* ======================================================================
   * 등록 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 댓글 등록. (원댓글 또는 1단계 대댓글)
   *
   * @param bno 게시글번호
   * @return 생성된 댓글번호
   */
  @Transactional
  public Long createComment(Long bno, BoardCommentDTO dto) {
    Long mno = requireLogin();

    if (Tool.isEmpty(dto.getContent())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용은 필수입니다.");
    }

    // 삭제된 글에 댓글이 달리면 어디에도 보이지 않는 데이터가 생깁니다.
    Board board = boardRepository.findByNoAndIsdel(bno, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 게시글입니다. no=" + bno));

    Long parentNo = resolveParentNo(bno, dto.getParentNo());

    BoardComment entity = dto.toEntity();
    entity.setBno(board.getNo());
    entity.setMno(mno);       // 작성자는 요청 바디가 아니라 토큰에서 가져옵니다.
    entity.setParentNo(parentNo);

    BoardComment saved = boardCommentRepository.save(entity);

    syncReplyCnt(board.getNo());
    return saved.getNo();
  }

  /**
   * 댓글 수정. (작성자 본인만 — 관리자도 남의 말을 바꿀 수는 없습니다)
   *
   * <p>삭제는 관리자도 할 수 있지만 <b>수정은 작성자만</b> 가능하게 한 이유는,
   * 관리자가 남의 발언 내용을 바꿔 버리면 그 자체가 서비스 신뢰를 무너뜨리기 때문입니다.
   * 부적절한 댓글은 "고치는" 것이 아니라 "지우는" 것이 맞습니다.</p>
   */
  @Transactional
  public void updateComment(Long no, BoardCommentDTO dto) {
    Long mno = requireLogin();

    BoardComment comment = boardCommentRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 댓글입니다. no=" + no));

    if (!comment.isWriter(mno)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 수정할 수 있습니다.");
    }
    if (Tool.isEmpty(dto.getContent())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용은 필수입니다.");
    }

    comment.updateContent(Tool.escapeHtml(dto.getContent()), Tool.getDate());
    // 변경감지(dirty checking)로 UPDATE가 나가므로 save() 호출은 불필요합니다.
  }

  /**
   * 댓글 삭제 (논리 삭제). — 작성자 본인 또는 관리자
   *
   * <p><b>[면접 포인트] 왜 무조건 논리삭제인가?</b><br>
   * 대댓글이 달린 원댓글을 물리삭제하면 자식 댓글의 {@code parentNo}가 존재하지 않는 번호를 가리켜
   * <b>정렬키가 깨지고 대화 맥락이 사라집니다.</b> (FK 제약이 없으므로 DB가 막아 주지도 않습니다.)
   * "자식이 없으면 물리삭제, 있으면 논리삭제"로 분기할 수도 있지만
   * 그러면 <b>삭제 동작이 두 가지</b>가 되어 신고·통계·복구 로직이 전부 두 갈래가 됩니다.
   * 규칙은 하나일수록 버그가 적으므로 전부 {@code ISDEL='Y'}로 통일했습니다.</p>
   */
  @Transactional
  public void deleteComment(Long no) {
    Long mno = requireLogin();

    BoardComment comment = boardCommentRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 이미 삭제된 댓글입니다. no=" + no));

    if (!comment.isWriter(mno) && !SecurityUtil.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 삭제할 수 있습니다.");
    }

    comment.delete(Tool.getDate());
    syncReplyCnt(comment.getBno());
  }

  /**
   * 게시글이 삭제될 때 딸린 댓글을 함께 논리삭제합니다. (BoardService에서 호출)
   *
   * <p>게시글만 지우고 댓글을 남기면 "어느 글에도 속하지 않은 댓글"이 쌓여
   * 통계·신고 처리에서 계속 걸리적거립니다.</p>
   */
  @Transactional
  public void deleteCommentsByBoard(Long bno) {
    String now = Tool.getDate();
    for (BoardComment comment : boardCommentRepository.findByBnoAndIsdel(bno, "N")) {
      comment.delete(now);
    }
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /**
   * 대댓글의 부모 번호를 검증해 반환합니다.
   *
   * <p><b>[실무 팁] 여기서 2단계 이상을 막습니다.</b><br>
   * 프론트가 대댓글의 번호를 parentNo로 보내면 depth 3이 되어 버립니다.
   * 그럴 때는 거부하지 않고 <b>부모의 부모(=원댓글)로 승격</b>시킵니다.
   * 사용자 입장에서는 "답글이 안 써져요"보다 "같은 묶음 맨 아래에 붙는" 동작이 자연스럽고,
   * 우리 정렬 규칙({@code COALESCE(parentNo, no)})도 그대로 유지됩니다.</p>
   *
   * @return 원댓글이면 null, 대댓글이면 <b>원댓글의 번호</b>
   */
  private Long resolveParentNo(Long bno, Long requestedParentNo) {
    if (requestedParentNo == null) {
      return null; // 원댓글
    }

    BoardComment parent = boardCommentRepository.findById(requestedParentNo)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "답글을 달 원댓글이 존재하지 않습니다. parentNo=" + requestedParentNo));

    // 다른 글의 댓글에 답글을 다는 요청은 조작된 요청입니다.
    if (!bno.equals(parent.getBno())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "다른 게시글의 댓글에는 답글을 달 수 없습니다.");
    }

    // 대댓글에 대댓글을 달려는 경우 -> 원댓글로 승격 (1단계 유지)
    return parent.isRoot() ? parent.getNo() : parent.getParentNo();
  }

  /**
   * BOARD.REPLY_CNT를 BOARD_COMMENT의 실제 COUNT 값으로 맞춥니다.
   *
   * <p>증감식({@code replyCnt + 1})이 아니라 <b>매번 전체 COUNT</b>로 다시 구하는 이유:
   * 증감식은 중간에 한 번이라도 어긋나면 그 오차가 영구히 누적되지만,
   * 재계산은 언제 실행해도 항상 정답으로 수렴합니다(멱등).
   * IDX_BOARD_COMMENT_BNO (BNO, ISDEL) 인덱스를 그대로 타므로 비용도 충분히 쌉니다.</p>
   */
  private void syncReplyCnt(Long bno) {
    long count = boardCommentRepository.countByBnoAndIsdel(bno, "N");
    boardRepository.findById(bno).ifPresent(board -> board.applyReplyCnt(count));
  }

  /** 로그인 회원번호를 얻고, 비로그인이면 401을 던집니다. */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return mno;
  }
}
