package dev.jpa.climbon.board;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dev.jpa.climbon.board.comment.BoardCommentService;
import dev.jpa.climbon.board.like.BoardLike;
import dev.jpa.climbon.board.like.BoardLikeRepository;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 커뮤니티 게시글 서비스.
 *
 * <p>이 서비스가 지키는 원칙 세 가지입니다.
 * <ol>
 *   <li><b>목록은 쿼리 1번</b> — 작성자 정보는 JPQL 조인 + DTO 생성자 표현식으로 한 번에 가져옵니다.</li>
 *   <li><b>권한 판단은 서버가</b> — "수정 버튼을 보여줄지"뿐 아니라 실제 수정 요청도 서버가 다시 검사합니다.
 *       프론트에서 버튼을 숨기는 것은 UX일 뿐, 보안이 아닙니다.</li>
 *   <li><b>집계 컬럼은 재계산</b> — likeCnt/replyCnt는 증감이 아니라 COUNT 결과로 덮어씁니다.</li>
 * </ol>
 * </p>
 *
 * <p><b>[실무 팁] 권한/검증 실패를 왜 {@code ResponseStatusException}으로 던지나?</b><br>
 * {@code IllegalStateException}을 던지면 Spring 기본 처리로 <b>500 Internal Server Error</b>가 나갑니다.
 * 500은 "서버가 고장났다"는 뜻이라 모니터링 알람이 울리고, 프론트는 사용자에게
 * 안내 문구를 띄울 수 없습니다. "남의 글을 수정하려 했다"는 <b>403</b>, "없는 글"은 <b>404</b>처럼
 * 상태 코드를 정확히 돌려줘야 프론트가 상황에 맞는 화면을 그릴 수 있습니다.
 * (규모가 커지면 도메인 예외 + {@code @RestControllerAdvice}로 분리하는 것이 정석입니다.)</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

  private final BoardRepository boardRepository;
  private final BoardLikeRepository boardLikeRepository;
  private final BoardCommentService boardCommentService;

  /** 인기글 집계 기간 (일) — "최근 7일"은 커뮤니티 회전 속도를 보고 정한 정책값입니다. */
  private static final int POPULAR_DAYS = 7;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 게시글 목록 검색.
   *
   * <p>정렬/공지 고정은 Repository의 JPQL이 담당하고, 여기서는
   * <b>로그인 사용자에 따라 달라지는 파생값</b>(liked / editable)만 채웁니다.
   * 이 값들은 DB에 저장된 사실이 아니라 "지금 이 요청을 보낸 사람 기준의 해석"이기 때문입니다.</p>
   *
   * @param searchType title | content | writer (null이면 제목+내용 동시 검색)
   * @param sort       new(기본) | view | like
   */
  public Page<BoardDTO> getBoards(Integer type, String word, String searchType,
      String sido, String sort, Pageable pageable) {

    Page<BoardDTO> page = boardRepository.searchBoards(
        type, word, searchType, sido, (sort == null ? "new" : sort), pageable);

    fillViewerFlags(page.getContent());
    return page;
  }

  /**
   * 게시글 상세 — 조회수 +1 후 조회.
   *
   * <p><b>[면접 포인트] 왜 "증가 → 조회" 순서인가?</b><br>
   * {@code increaseVcnt()}는 영속성 컨텍스트를 우회하는 <b>벌크 UPDATE</b>이고
   * {@code clearAutomatically = true}로 컨텍스트를 비웁니다.
   * 그래서 UPDATE를 먼저 날리고 그다음 조회해야 <b>증가된 값이 화면에 반영</b>됩니다.
   * 순서를 바꾸면 방금 본 사람은 항상 1 적은 조회수를 보게 됩니다.</p>
   *
   * <p>새로고침 연타로 조회수가 부풀려지는 문제는 남습니다. 실무에서는
   * (회원번호 또는 IP + 글번호)를 Redis에 TTL 10분으로 넣어 중복을 걸러내지만,
   * 이 프로젝트는 Redis를 쓰지 않아 단순 증가로 두었습니다.</p>
   */
  @Transactional
  public BoardDTO getBoard(Long no) {
    boardRepository.increaseVcnt(no);

    BoardDTO dto = boardRepository.findBoardDetail(no)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 게시글입니다. no=" + no));

    fillViewerFlags(List.of(dto));
    return dto;
  }

  /**
   * 인기글 — 최근 7일 내 (조회수 + 좋아요×5) 상위 N건.
   *
   * <p>기간을 자르지 않으면 몇 년 전 글이 누적 조회수로 영원히 1등을 차지합니다.
   * 커뮤니티의 "인기"는 <b>지금 뜨는 글</b>이라는 뜻이므로 최근 기간으로 제한합니다.</p>
   */
  public List<BoardDTO> getPopularBoards(int size) {
    List<BoardDTO> list = boardRepository.findPopularBoards(
        Tool.getDateBefore(POPULAR_DAYS), PageRequest.of(0, size));
    fillViewerFlags(list);
    return list;
  }

  /* ======================================================================
   * 등록 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 게시글 등록.
   *
   * @return 생성된 게시글번호
   */
  @Transactional
  public Long createBoard(BoardDTO dto) {
    Long mno = requireLogin();

    if (Tool.isEmpty(dto.getTitle())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 필수입니다.");
    }
    if (Tool.isEmpty(dto.getContent())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용은 필수입니다.");
    }

    Board entity = dto.toEntity();
    entity.setMno(mno); // 작성자는 요청 바디가 아니라 토큰에서 가져옵니다.

    // 중고거래(TYPE 4)는 거래 상태가 비어 있으면 '판매중'으로 시작시킵니다.
    if (entity.getType() == Board.TYPE_DEAL && entity.getDealStatus() == null) {
      entity.setDealStatus(Board.DEAL_ON_SALE);
    }

    // 공지 고정은 관리자만 켤 수 있습니다. (일반 회원이 noticeYn='Y'를 보내도 무시됨)
    if (SecurityUtil.isAdmin()) {
      entity.changeNoticeYn(dto.getNoticeYn());
    }

    return boardRepository.save(entity).getNo();
  }

  /**
   * 게시글 수정. — <b>작성자 본인 또는 관리자만</b>
   *
   * <p>권한이 없으면 403을 던집니다. 404가 아니라 403인 이유는
   * "글은 있지만 당신에게 권한이 없다"를 프론트가 구분해서 안내해야 하기 때문입니다.
   * (반대로 글의 존재 자체를 숨겨야 하는 비공개 게시판이라면 404가 더 안전합니다.)</p>
   */
  @Transactional
  public void updateBoard(Long no, BoardDTO dto) {
    Long mno = requireLogin();

    Board board = boardRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 게시글입니다. no=" + no));

    checkEditPermission(board, mno);

    board.updateBoard(
        dto.getType(),
        dto.getTitle(),
        Tool.escapeHtml(dto.getContent()),
        dto.getGno(),
        dto.getSido(),
        dto.getMeetDate(),
        dto.getDealPrice(),
        dto.getDealStatus(),
        dto.getFileyn(),
        Tool.getDate());

    // 공지 고정 전환은 관리자만
    if (SecurityUtil.isAdmin() && dto.getNoticeYn() != null) {
      board.changeNoticeYn(dto.getNoticeYn());
    }
    // 변경감지(dirty checking)로 UPDATE가 나가므로 save() 호출은 불필요합니다.
  }

  /**
   * 게시글 삭제 (논리 삭제). — <b>작성자 본인 또는 관리자만</b>
   *
   * <p>딸린 댓글도 같은 트랜잭션에서 함께 논리삭제합니다.
   * 글만 지우고 댓글을 남기면 "어느 글에도 속하지 않은 댓글"이 쌓여
   * 신고 처리·통계에서 계속 걸리적거립니다.</p>
   *
   * <p>좋아요(BOARD_LIKE)는 <b>물리삭제</b>합니다. 좋아요 행은 "누가 눌렀나"라는 사실만 담고 있어
   * 원글이 사라지면 보존할 가치가 없고, 남겨 두면 (BNO, MNO) UNIQUE 제약 때문에
   * 나중에 같은 번호가 재사용될 때 꼬일 수 있습니다.</p>
   */
  @Transactional
  public void deleteBoard(Long no) {
    Long mno = requireLogin();

    Board board = boardRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 이미 삭제된 게시글입니다. no=" + no));

    checkEditPermission(board, mno);

    board.delete(Tool.getDate());
    boardCommentService.deleteCommentsByBoard(no);
    boardLikeRepository.deleteByBno(no);
  }

  /* ======================================================================
   * 좋아요
   * ====================================================================== */

  /**
   * 좋아요 토글.
   *
   * <p><b>[면접 포인트] 토글을 왜 "행의 존재 여부"로 판단하나?</b><br>
   * 프론트가 {@code cancel=true/false}를 보내 주는 방식은
   * 다른 탭에서 이미 눌러 둔 경우 화면 상태와 DB가 어긋나
   * <b>좋아요가 두 번 들어가거나 취소가 두 번 일어납니다.</b>
   * 서버가 BOARD_LIKE에 내 행이 있는지 직접 보고 결정하면
   * 클라이언트 상태와 무관하게 항상 올바른 결과가 나옵니다(멱등에 가까운 토글).</p>
   *
   * <p>카운트는 증감이 아니라 <b>COUNT 재계산</b>으로 맞춥니다.
   * 증감식은 한 번만 어긋나도 오차가 영구히 남지만, 재계산은 항상 정답으로 수렴합니다.
   * 같은 트랜잭션이라 "행은 지웠는데 카운트는 그대로"인 상태도 남지 않습니다.</p>
   *
   * @return {@code {liked: 최종 상태, count: 최종 좋아요 수}}
   */
  @Transactional
  public Map<String, Object> toggleLike(Long no) {
    Long mno = requireLogin();

    Board board = boardRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 게시글입니다. no=" + no));

    boolean liked;
    var existing = boardLikeRepository.findByBnoAndMno(no, mno);
    if (existing.isPresent()) {
      boardLikeRepository.delete(existing.get());  // 이미 눌렀다 -> 취소
      liked = false;
    } else {
      boardLikeRepository.save(BoardLike.builder()
          .bno(no)
          .mno(mno)
          .cdate(Tool.getDate())
          .build());
      liked = true;
    }

    // 방금 insert/delete한 내용을 COUNT가 보도록 flush를 겸한 재계산.
    long count = boardLikeRepository.countByBno(no);
    board.applyLikeCnt(count);

    Map<String, Object> result = new HashMap<>();
    result.put("liked", liked);
    result.put("count", count);
    return result;
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /**
   * 목록/상세 DTO에 <b>보는 사람 기준의 파생값</b>을 채웁니다.
   *
   * <p>liked(내가 좋아요를 눌렀는지)를 글마다 {@code existsByBnoAndMno()}로 확인하면
   * 20건 목록에 쿼리 20번이 추가됩니다(N+1). 그래서 화면에 보이는 글번호를 모아
   * <b>IN 절로 한 번에</b> 조회하고 {@code Set}으로 만들어 O(1) 대조합니다.</p>
   *
   * <p>비로그인 사용자에게는 liked를 null로 둡니다. false로 내리면 프론트가
   * "안 눌렀다"와 "로그인 안 했다"를 구분할 수 없어 하트를 눌렀을 때의 동작을 정할 수 없습니다.</p>
   */
  private void fillViewerFlags(List<BoardDTO> list) {
    if (list == null || list.isEmpty()) return;

    Long loginNo = SecurityUtil.getMemberNo();
    if (loginNo == null) {
      return; // 비로그인: liked / editable 모두 null (JSON에서 제외됨)
    }

    boolean admin = SecurityUtil.isAdmin();
    List<Long> bnos = list.stream().map(BoardDTO::getNo).toList();
    Set<Long> likedSet = new HashSet<>(boardLikeRepository.findLikedBnos(loginNo, bnos));

    for (BoardDTO dto : list) {
      dto.setLiked(likedSet.contains(dto.getNo()));
      dto.setEditable(admin || loginNo.equals(dto.getMno()));
    }
  }

  /**
   * 수정/삭제 권한 검사 — 작성자 본인 또는 관리자만 통과합니다.
   *
   * <p>프론트에서 버튼을 숨기는 것만으로는 아무것도 막지 못합니다.
   * 누구든 API를 직접 호출할 수 있으므로 <b>서버가 반드시 다시 검사</b>해야 합니다.</p>
   */
  private void checkEditPermission(Board board, Long mno) {
    if (!board.isWriter(mno) && !SecurityUtil.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 글만 수정/삭제할 수 있습니다.");
    }
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
