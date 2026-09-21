package dev.jpa.climbon.board.comment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게시글 댓글 Repository.
 */
public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

  /** 살아 있는 댓글 단건 조회 (수정/삭제 전 검증용) */
  Optional<BoardComment> findByNoAndIsdel(Long no, String isdel);

  /**
   * 게시글의 댓글 목록 — <b>원댓글 바로 아래에 그 대댓글이 오도록</b> 정렬합니다.
   *
   * <p><b>[면접 포인트] COALESCE(c.parentNo, c.no) 정렬 트릭</b><br>
   * 댓글을 그냥 {@code ORDER BY c.no} 로 뽑으면 등록 순서대로 나열되어
   * 대댓글이 엉뚱한 위치에 떨어집니다.
   * <pre>
   *   1 원댓글 A        parentNo = null  ->  정렬키 = COALESCE(null, 1) = 1
   *   2 원댓글 B        parentNo = null  ->  정렬키 = 2
   *   3 A의 대댓글      parentNo = 1     ->  정렬키 = 1
   *   4 B의 대댓글      parentNo = 2     ->  정렬키 = 2
   * </pre>
   * 1차 정렬키를 <b>"내가 속한 묶음의 대표 번호"</b>, 즉
   * {@code COALESCE(parentNo, no)} 로 두면 (1, 3) / (2, 4) 로 묶입니다.
   * 2차로 {@code c.no ASC} 를 주면 <b>원댓글이 자기 대댓글보다 항상 번호가 작으므로</b>
   * 자연스럽게 원댓글 → 대댓글 순서가 됩니다.
   * 재귀 쿼리나 자바에서의 2중 루프 없이 <b>쿼리 하나로</b> 트리 순서가 완성됩니다.</p>
   *
   * <p>삭제된 댓글({@code isdel = 'Y'})도 <b>일부러 포함</b>합니다.
   * 대댓글이 달린 원댓글을 목록에서 빼 버리면 자식이 부모 없이 떠 버리기 때문입니다.
   * 내용 숨김 처리는 {@link BoardCommentDTO} 생성자가 담당합니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.board.comment.BoardCommentDTO(
             c.no, c.bno, c.mno, c.parentNo, c.content,
             c.likeCnt, c.cdate, c.udate, c.isdel,
             m.nickname, m.profileImg)
      FROM BoardComment c
      LEFT JOIN Member m ON m.no = c.mno
      WHERE c.bno = :bno
      ORDER BY COALESCE(c.parentNo, c.no) ASC, c.no ASC
      """)
  List<BoardCommentDTO> findCommentsByBno(@Param("bno") Long bno);

  /**
   * 게시글의 살아 있는 댓글 수 — 삭제 후 BOARD.REPLY_CNT에 복사할 값입니다.
   * <p>삭제된 댓글({@code isdel='Y'})은 화면에 자리는 남지만
   * "댓글 3개" 같은 카운트에는 포함하지 않는 것이 사용자 기대에 맞습니다.</p>
   */
  long countByBnoAndIsdel(Long bno, String isdel);

  /**
   * 이 댓글에 달린 <b>살아 있는</b> 대댓글 수.
   * <p>0이면 물리삭제해도 트리가 깨지지 않지만, 이 프로젝트는
   * 신고/분쟁 대응을 위해 원본을 남기는 논리삭제로 통일했습니다.
   * (그래도 "자식이 있는지"는 삭제 정책 분기에 필요해 남겨 둡니다.)</p>
   */
  long countByParentNoAndIsdel(Long parentNo, String isdel);

  /** 게시글이 삭제될 때 댓글도 함께 논리삭제하기 위한 조회 */
  List<BoardComment> findByBnoAndIsdel(Long bno, String isdel);
}
