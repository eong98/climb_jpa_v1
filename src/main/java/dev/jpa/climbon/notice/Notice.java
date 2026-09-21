package dev.jpa.climbon.notice;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 공지사항 엔티티. (테이블: NOTICE)
 *
 * <p>[면접 포인트] <b>{@code @Lob} String 과 CLOB</b><br>
 * CONTENT는 CLOB이라 길이 제한이 사실상 없습니다.
 * 자바 쪽에서는 {@code @Lob private String content;} 한 줄이면
 * 하이버네이트가 방언(Oracle/MySQL)에 맞는 대용량 문자 타입으로 매핑해 줍니다.
 * {@code columnDefinition}으로 DB 타입을 직접 박아 넣으면
 * MySQL로 바꿀 때 "CLOB 타입 없음" 오류가 나므로 쓰지 않습니다.</p>
 *
 * <p>[실무 팁] {@code @ToString}에서 content를 제외했습니다.
 * 본문이 수십 KB일 수 있어 로그에 통째로 찍히면 로그 파일이 순식간에 커집니다.</p>
 */
@Entity
@Table(name = "NOTICE")
@Getter
@Setter
@ToString(exclude = "content")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Notice {

  /** 공지번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notice_seq_use")
  @SequenceGenerator(name = "notice_seq_use", sequenceName = "NOTICE_SEQ", allocationSize = 1)
  private Long no;

  /** 구분 (0: 일반, 1: 이벤트, 2: 점검, 3: 업데이트) */
  @Builder.Default
  private Integer type = 0;

  /** 제목 */
  private String title;

  /** 내용 (CLOB) */
  @Lob
  private String content;

  /** 작성 관리자 번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 상단 고정 여부 (Y/N) */
  @Builder.Default
  private String topYn = "N";

  /** 첨부파일 보유 여부 (Y/N) */
  @Builder.Default
  private String fileyn = "N";

  /** 조회수 */
  @Builder.Default
  private Integer vcnt = 0;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) */
  @Builder.Default
  private String isdel = "N";

  /* ======================================================================
   * 상태 변경 메서드
   * ====================================================================== */

  /** 공지 내용을 수정합니다. 등록일(cdate)과 작성자(mno)는 바꾸지 않습니다. */
  public void update(Integer type, String title, String content, String topYn, String fileyn, String udate) {
    if (type != null) this.type = type;
    if (title != null) this.title = title;
    if (content != null) this.content = content;
    if (topYn != null) this.topYn = topYn;
    if (fileyn != null) this.fileyn = fileyn;
    this.udate = udate;
  }

  /** 상세 조회 시 조회수를 1 올립니다. */
  public void increaseVcnt() {
    this.vcnt = (this.vcnt == null ? 0 : this.vcnt) + 1;
  }

  /**
   * 논리삭제 — 행을 지우지 않고 ISDEL만 'Y'로 바꿉니다.
   *
   * <p>[면접 포인트] <b>왜 논리삭제인가?</b><br>
   * 1) 관리자가 실수로 지웠을 때 플래그만 되돌리면 즉시 복구됩니다.<br>
   * 2) "언제 무엇이 공지되었는지"는 분쟁 시 근거 자료가 되므로 이력이 남아야 합니다.<br>
   * 3) 첨부파일이 물려 있어 물리삭제하면 연관 정리가 복잡해집니다.<br>
   * 대가로 <b>모든 조회에 {@code isdel = 'N'} 조건을 빠짐없이 넣어야 한다</b>는 부담이 생깁니다.
   * 하나라도 빠지면 지운 글이 다시 보이므로 리포지토리 쿼리에 조건을 고정해 두었습니다.</p>
   */
  public void delete() {
    this.isdel = "Y";
  }

  /** 첨부파일 등록/삭제에 맞춰 보유 여부를 갱신합니다. (목록에서 클립 아이콘 표시용) */
  public void updateFileyn(String fileyn) {
    this.fileyn = fileyn;
  }
}
