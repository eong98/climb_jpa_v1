package dev.jpa.climbon.board;

import jakarta.persistence.Column;
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
 * 커뮤니티 게시글 엔티티. — BOARD 테이블
 *
 * <p><b>[면접 포인트] 게시판 5종(자유/파트너/후기/질문/중고)을 왜 테이블 하나로 합쳤나?</b><br>
 * 게시판마다 테이블을 만들면 "전체 통합검색", "내가 쓴 글 모아보기", "인기글 TOP 10" 같은
 * 화면이 전부 UNION 5개가 되고, 게시판이 하나 늘 때마다 Entity/Service/Controller가
 * 통째로 복제됩니다. 컬럼 구성이 90% 이상 같으므로 {@code TYPE} 하나로 구분하는
 * <b>단일 테이블 방식</b>이 훨씬 유지보수하기 쉽습니다.</p>
 *
 * <p>대신 게시판별 전용 컬럼이 생깁니다.
 * {@code meetDate}(파트너 모집), {@code dealPrice}/{@code dealStatus}(중고거래),
 * {@code gno}(암장 후기)가 그것이고, 해당 타입이 아니면 NULL로 둡니다.
 * NULL 컬럼이 조금 늘어나는 비용보다 조회 단순화의 이득이 큽니다.</p>
 *
 * <p><b>[면접 포인트] vcnt / likeCnt / replyCnt 반정규화 컬럼</b><br>
 * 목록 한 화면에 20개 글이 뜨는데 글마다 댓글 수를 COUNT 하면
 * 스칼라 서브쿼리 20번이 추가로 돕니다. 댓글 등록/삭제는 조회에 비하면 드문 이벤트이므로
 * <b>쓰기 시점에 한 번 계산해 컬럼으로 들고</b> 목록은 그 값을 그냥 읽습니다.</p>
 */
@Entity
@Table(name = "BOARD")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Board {

  /* ======================================================================
   * TYPE 상수 — 숫자 리터럴이 코드 곳곳에 흩어지면 의미를 잃습니다.
   *  0: 자유게시판
   *  1: 파트너 구해요 (같이 등반할 사람 찾기) — meetDate, sido 사용
   *  2: 암장 후기                              — gno 사용
   *  3: 질문/답변
   *  4: 장비 중고거래                          — dealPrice, dealStatus 사용
   * ====================================================================== */

  /** 자유게시판 */
  public static final int TYPE_FREE = 0;
  /** 파트너 구해요 */
  public static final int TYPE_PARTNER = 1;
  /** 암장 후기 */
  public static final int TYPE_GYM_REVIEW = 2;
  /** 질문/답변 */
  public static final int TYPE_QNA = 3;
  /** 장비 중고거래 */
  public static final int TYPE_DEAL = 4;

  /** 거래 상태 — 0: 판매중, 1: 예약중, 2: 완료 */
  public static final int DEAL_ON_SALE = 0;
  public static final int DEAL_RESERVED = 1;
  public static final int DEAL_DONE = 2;

  /** 게시글 상태 — 0: 블라인드(신고 누적 등), 1: 정상 */
  public static final int STATUS_BLIND = 0;
  public static final int STATUS_NORMAL = 1;

  /** 게시글번호 (PK) — Oracle 시퀀스 BOARD_SEQ 사용 */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "board_seq_use")
  @SequenceGenerator(name = "board_seq_use", sequenceName = "BOARD_SEQ", allocationSize = 1)
  private Long no;

  /** 게시판 구분 (0: 자유, 1: 파트너, 2: 암장후기, 3: 질문답변, 4: 중고거래) */
  @Builder.Default
  private int type = TYPE_FREE;

  /** 작성 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 제목 */
  private String title;

  /**
   * 내용 (CLOB).
   * <p>{@code @Lob}을 빼면 Oracle에서 VARCHAR2로 바인딩되어 4000byte 초과 시 저장에 실패합니다.</p>
   */
  @Lob
  @Column(name = "CONTENT")
  private String content;

  /** 연관 암장번호 (암장 후기 / 파트너 모집 시) */
  private Long gno;

  /** 지역 태그 (파트너 모집 시 "서울" 등) */
  private String sido;

  /** 만남 예정 일시 (파트너 모집) */
  private String meetDate;

  /** 거래 가격 (중고거래) */
  private Integer dealPrice;

  /** 거래 상태 (0: 판매중, 1: 예약중, 2: 완료) */
  private Integer dealStatus;

  /** 조회수 — 반정규화 컬럼 */
  @Builder.Default
  private int vcnt = 0;

  /** 좋아요 수 — BOARD_LIKE COUNT 결과를 복사해 두는 반정규화 컬럼 */
  @Builder.Default
  private int likeCnt = 0;

  /** 댓글 수 — BOARD_COMMENT COUNT 결과를 복사해 두는 반정규화 컬럼 */
  @Builder.Default
  private int replyCnt = 0;

  /** 첨부파일 보유 여부 (Y/N) */
  @Builder.Default
  private String fileyn = "N";

  /** 공지 고정 여부 (Y/N) — 목록에서 항상 상단에 노출됩니다. */
  @Builder.Default
  private String noticeYn = "N";

  /** 상태 (0: 블라인드, 1: 정상) */
  @Builder.Default
  private int status = STATUS_NORMAL;

  /** 등록일시 'yyyy-MM-dd HH:mm:ss' */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) — 논리삭제 */
  @Builder.Default
  private String isdel = "N";

  // ==========================================================
  // 상태 변경 전용 메서드
  //  - setter를 아무 데서나 부르면 "언제 왜 바뀌었는지" 추적이 안 되므로
  //    의미 있는 변경은 이름 있는 메서드로만 노출합니다.
  // ==========================================================

  /**
   * 본문 수정.
   *
   * <p>작성자(mno)·조회수·좋아요 수는 <b>일부러 파라미터에서 뺐습니다.</b>
   * 수정 요청 바디에 mno나 likeCnt를 끼워 넣어 남의 글을 가로채거나
   * 좋아요를 조작하는 것을 구조적으로 막기 위함입니다.</p>
   */
  public void updateBoard(Integer type, String title, String content, Long gno, String sido,
      String meetDate, Integer dealPrice, Integer dealStatus, String fileyn, String udate) {
    if (type != null) this.type = type;
    this.title = title;
    this.content = content;
    this.gno = gno;
    this.sido = sido;
    this.meetDate = meetDate;
    this.dealPrice = dealPrice;
    this.dealStatus = dealStatus;
    if (fileyn != null) this.fileyn = fileyn;
    this.udate = udate;
  }

  /** 공지 고정 여부 변경 (관리자 전용) */
  public void changeNoticeYn(String noticeYn) {
    this.noticeYn = "Y".equalsIgnoreCase(noticeYn) ? "Y" : "N";
  }

  /**
   * 좋아요 수를 BOARD_LIKE의 실제 COUNT 값으로 맞춥니다.
   * <p>{@code likeCnt + 1} 증감식이 아니라 <b>재계산 값 대입</b>인 이유는
   * 증감식은 한 번이라도 어긋나면 오차가 영구히 누적되기 때문입니다.</p>
   */
  public void applyLikeCnt(long likeCnt) {
    this.likeCnt = (int) likeCnt;
  }

  /** 댓글 수를 BOARD_COMMENT의 실제 COUNT 값으로 맞춥니다. */
  public void applyReplyCnt(long replyCnt) {
    this.replyCnt = (int) replyCnt;
  }

  /** 논리 삭제 */
  public void delete(String udate) {
    this.isdel = "Y";
    this.udate = udate;
  }

  /** 작성자 본인인지 확인 */
  public boolean isWriter(Long mno) {
    return mno != null && mno.equals(this.mno);
  }
}
