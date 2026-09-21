package dev.jpa.climbon.board.comment;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 게시글 댓글 DTO.
 *
 * <p>목록은 {@code LEFT JOIN Member} 생성자 표현식으로 조회하므로
 * 작성자 닉네임/프로필을 얻기 위한 추가 쿼리(N+1)가 발생하지 않습니다.
 * (BoardDTO의 같은 주석 참고)</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BoardCommentDTO {

  /** 댓글번호 (등록 시 null) */
  private Long no;

  /** 게시글번호 */
  private Long bno;

  /** 작성 회원번호 */
  private Long mno;

  /** 부모 댓글번호 — null이면 원댓글 */
  private Long parentNo;

  /** 내용 — 삭제된 댓글이면 빈 문자열로 내려갑니다. */
  private String content;

  /** 좋아요 수 */
  private Integer likeCnt;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /**
   * 삭제 여부 (Y/N).
   * <p>프론트는 이 값이 'Y'면 본문 대신 <b>"삭제된 댓글입니다"</b>를 그립니다.
   * 서버가 아예 목록에서 빼면 대댓글이 부모 없이 떠서 대화 맥락이 사라지므로
   * "자리는 남기되 내용은 지운다"가 맞습니다.</p>
   */
  private String isdel;

  /* ---------------- 조인 값 (DB 컬럼 아님) ---------------- */

  /** 작성자 닉네임 */
  private String nickname;

  /** 작성자 프로필 이미지 */
  private String profileImg;

  /* ---------------- 응답 전용 파생값 ---------------- */

  /** 대댓글 여부 — 프론트가 들여쓰기를 결정할 때 씁니다. */
  private Boolean reply;

  /** 수정/삭제 버튼 노출 여부 (작성자 본인 또는 관리자) */
  private Boolean editable;

  /**
   * JPQL 생성자 표현식 전용 생성자.
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응하므로 함부로 바꾸면 안 됩니다.</p>
   */
  public BoardCommentDTO(Long no, Long bno, Long mno, Long parentNo, String content,
      Integer likeCnt, String cdate, String udate, String isdel,
      String nickname, String profileImg) {
    this.no = no;
    this.bno = bno;
    this.mno = mno;
    this.parentNo = parentNo;
    // 삭제된 댓글의 원문은 API 밖으로 내보내지 않습니다. (자리만 남기고 내용은 숨김)
    this.content = "Y".equals(isdel) ? "" : content;
    this.likeCnt = likeCnt;
    this.cdate = cdate;
    this.udate = udate;
    this.isdel = isdel;
    this.nickname = nickname;
    this.profileImg = profileImg;
    this.reply = (parentNo != null);
  }

  /**
   * DTO -> Entity. (댓글 등록)
   * <p>mno는 Service가 로그인 정보로 덮어쓰고, likeCnt는 항상 0에서 시작합니다.</p>
   */
  public BoardComment toEntity() {
    return BoardComment.builder()
        .no(this.no)
        .bno(this.bno)
        .mno(this.mno)
        .parentNo(this.parentNo)
        // 댓글도 화면에 그대로 출력되므로 저장 전에 HTML 특수문자를 이스케이프합니다(XSS 방어).
        .content(Tool.escapeHtml(this.content))
        .likeCnt(0)
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /** Entity -> DTO (작성자 조인 없이 단건 변환) */
  public static BoardCommentDTO fromEntity(BoardComment entity) {
    if (entity == null) return null;
    return BoardCommentDTO.builder()
        .no(entity.getNo())
        .bno(entity.getBno())
        .mno(entity.getMno())
        .parentNo(entity.getParentNo())
        .content("Y".equals(entity.getIsdel()) ? "" : entity.getContent())
        .likeCnt(entity.getLikeCnt())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .isdel(entity.getIsdel())
        .reply(!entity.isRoot())
        .build();
  }
}
