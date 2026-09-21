package dev.jpa.climbon.attach;

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
 * 공통 첨부파일 엔티티. (테이블: ATTACH)
 *
 * <p>게시판·리뷰·암장·상품 등 모든 도메인이 <b>하나의 테이블</b>을 공유합니다.
 * 어느 테이블의 몇 번 글에 붙은 파일인지는 {@code tname}(테이블명) + {@code bno}(원글 PK)로 구분합니다.</p>
 *
 * <p>[면접 포인트] 테이블마다 첨부 컬럼(file1, file2...)을 두지 않고 공통 테이블로 뺀 이유<br>
 * 1) 파일 개수 제한이 사라집니다 (1:N 구조).<br>
 * 2) 새 게시판이 생겨도 업로드/삭제 로직을 그대로 재사용합니다.<br>
 * 3) 전체 첨부 용량 통계·관리자 검색을 한 테이블에서 처리할 수 있습니다.<br>
 * 단점은 FK로 원글을 강제할 수 없다는 점입니다. 원글이 지워질 때 첨부도 지우는 책임을
 * 애플리케이션(AttachService.deleteByTnameAndBno)이 져야 합니다.</p>
 *
 * <p>※ 팀 프로젝트(allimio)의 ATTACH에는 메뉴번호 tno 컬럼이 있었지만
 * 이 프로젝트의 schema.sql에는 <b>tno가 없습니다</b>. 대신 업로더 회원번호 mno와
 * 삭제 예정일시 ddate가 있습니다. Entity는 schema.sql을 기준으로 맞춥니다.</p>
 */
@Entity
@Table(name = "ATTACH")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Attach {

  /** 첨부파일번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attach_seq_use")
  @SequenceGenerator(name = "attach_seq_use", sequenceName = "ATTACH_SEQ", allocationSize = 1)
  private Long no;

  /** 등록 테이블명(대문자) = 저장 폴더명. 예) BOARD, GYM_REVIEW, PRODUCT */
  private String tname;

  /** 등록 게시글/원글 PK 번호 */
  private Long bno;

  /** 파일 종류 (0: IMAGE, 1: FILE) */
  @Builder.Default
  private Integer type = 1;

  /** 원본 파일명 — 다운로드할 때 사용자에게 보여줄 이름 */
  private String name;

  /** 파일 크기 (BYTE) */
  @Builder.Default
  private Long fsize = 0L;

  /** 서버 저장 파일명 (UUID) — 한글/공백/중복 문제를 피하려고 이름을 바꿔 저장합니다. */
  private String sname;

  /** 썸네일 파일명 (이미지일 때만) */
  private String thumb;

  /** 상대 저장 경로. 예) /attach/storage/BOARD/images */
  private String purl;

  /** 업로드 회원번호 */
  private Long mno;

  /** 등록일시 */
  private String cdate;

  /** 삭제 예정일시 — 배치로 실물 파일을 정리할 때 사용합니다. */
  private String ddate;

  /**
   * 삭제 예약 표시. (물리 삭제 전 유예 기간을 두고 싶을 때 사용)
   *
   * <p>[실무 팁] 파일은 한 번 지우면 복구가 어렵습니다.
   * 이 프로젝트의 기본 삭제는 "DB 행 + 실물 파일 즉시 삭제"지만,
   * 운영에서 실수 복구가 중요한 데이터라면 ddate만 찍어두고
   * 며칠 뒤 배치로 지우는 방식이 안전합니다.</p>
   */
  public void reserveDelete(String ddate) {
    this.ddate = ddate;
  }
}
