package dev.jpa.climbon.board.comment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 게시글 댓글 엔티티. — BOARD_COMMENT 테이블
 *
 * <p><b>[면접 포인트] 무한 깊이 트리가 아니라 왜 1단계 대댓글인가?</b><br>
 * {@code PARENT_NO}만 있으면 이론상 무한 깊이가 가능하지만, 실제 서비스에서는
 * <ul>
 *   <li>깊이가 깊어질수록 <b>모바일 화면에서 들여쓰기가 망가지고</b>,</li>
 *   <li>정렬을 위해 경로(path)나 재귀 쿼리(CONNECT BY / WITH RECURSIVE)가 필요해지며,</li>
 *   <li>부모가 삭제됐을 때 하위 트리 처리 규칙이 급격히 복잡해집니다.</li>
 * </ul>
 * 그래서 <b>원댓글(parentNo = null) / 대댓글(parentNo = 원댓글 번호)</b> 두 단계로 제한합니다.
 * 이러면 정렬도 단순 ORDER BY 하나로 끝납니다. (BoardCommentRepository 주석 참고)</p>
 *
 * <p><b>[면접 포인트] 삭제된 댓글을 왜 물리삭제하지 않나?</b><br>
 * 대댓글이 달린 원댓글을 지워 버리면 자식 댓글이 <b>부모를 잃은 고아</b>가 되어
 * 목록 정렬 기준(부모 번호)이 깨지고 대화 맥락도 사라집니다.
 * 그래서 {@code ISDEL = 'Y'}로만 표시하고 프론트는 "삭제된 댓글입니다"로 그립니다.
 * (내용 자체는 남지만 API 응답에서 본문을 비워 내보내므로 노출되지 않습니다.)</p>
 */
@Entity
@Table(name = "BOARD_COMMENT")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardComment {

  /** 댓글번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "board_comment_seq_use")
  @SequenceGenerator(name = "board_comment_seq_use", sequenceName = "BOARD_COMMENT_SEQ", allocationSize = 1)
  private Long no;

  /** 게시글번호 (FK -> BOARD.NO) */
  private Long bno;

  /** 작성 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 부모 댓글번호 — null이면 원댓글, 값이 있으면 대댓글 */
  private Long parentNo;

  /** 내용 (VARCHAR2(1000)) */
  private String content;

  /** 좋아요 수 */
  @Builder.Default
  private int likeCnt = 0;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) */
  @Builder.Default
  private String isdel = "N";

  // ==========================================================
  // 상태 변경 전용 메서드
  // ==========================================================

  /** 댓글 내용 수정 */
  public void updateContent(String content, String udate) {
    this.content = content;
    this.udate = udate;
  }

  /** 논리 삭제 — 대댓글이 남아 있어도 트리가 깨지지 않습니다. */
  public void delete(String udate) {
    this.isdel = "Y";
    this.udate = udate;
  }

  /** 원댓글인지 여부 */
  public boolean isRoot() {
    return this.parentNo == null;
  }

  /** 작성자 본인인지 확인 */
  public boolean isWriter(Long mno) {
    return mno != null && mno.equals(this.mno);
  }
}
