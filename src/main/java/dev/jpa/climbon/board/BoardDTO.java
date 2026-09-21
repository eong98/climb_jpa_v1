package dev.jpa.climbon.board;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 커뮤니티 게시글 DTO. (목록 / 상세 / 등록·수정 공용)
 *
 * <p><b>[면접 포인트] 왜 JPQL 생성자 표현식(new BoardDTO(...))으로 조회하나? — N+1 방지</b><br>
 * 게시글 목록에는 작성자 닉네임·프로필 이미지가 함께 보여야 합니다.
 * {@code List<Board>}를 조회한 뒤 반복문에서 {@code memberRepository.findById(mno)}를 부르면
 * 글 20건에 회원 조회 20번이 추가로 나갑니다(= <b>N+1 문제</b>).
 * {@code @ManyToOne} 연관관계를 걸어도 지연로딩이면 결국 같은 일이 벌어집니다.</p>
 *
 * <p>그래서 JPQL에서 {@code LEFT JOIN Member m ON m.no = b.mno} 로 조인하고
 * {@code SELECT new dev.jpa.climbon.board.BoardDTO(b.no, ..., m.nickname, ...)} 형태의
 * <b>생성자 표현식</b>으로 필요한 컬럼만 뽑아 DTO를 바로 만듭니다.
 * 결과는 <b>쿼리 1번</b>이고, 엔티티를 영속화하지 않아 1차 캐시 메모리도 덜 씁니다.
 * LEFT JOIN인 이유는 탈퇴 회원의 글이 목록에서 통째로 사라지지 않게 하기 위함입니다.</p>
 *
 * <p><b>주의</b>: 생성자 표현식은 파라미터의 <b>순서와 타입이 JPQL과 정확히 일치</b>해야 합니다.
 * 아래 두 생성자가 그 계약이므로 중간에 필드를 끼워 넣으면 안 됩니다.
 * (목록용 생성자는 CLOB인 content를 <b>일부러 제외</b>합니다 — 목록 20건마다
 *  본문 전체를 읽으면 네트워크·메모리가 낭비됩니다.)</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BoardDTO {

  /** 게시글번호 (등록 시 null) */
  private Long no;

  /** 게시판 구분 (0: 자유, 1: 파트너, 2: 암장후기, 3: 질문답변, 4: 중고거래) */
  private Integer type;

  /** 작성 회원번호 — 등록/수정 시 서버가 토큰 값으로 덮어씁니다. */
  private Long mno;

  /** 제목 */
  private String title;

  /** 내용 — 목록 조회에서는 null (CLOB이라 상세에서만 채웁니다) */
  private String content;

  /** 연관 암장번호 */
  private Long gno;

  /** 지역 태그 */
  private String sido;

  /** 만남 예정 일시 (파트너 모집) */
  private String meetDate;

  /** 거래 가격 (중고거래) */
  private Integer dealPrice;

  /** 거래 상태 (0: 판매중, 1: 예약중, 2: 완료) */
  private Integer dealStatus;

  /** 조회수 */
  private Integer vcnt;

  /** 좋아요 수 */
  private Integer likeCnt;

  /** 댓글 수 */
  private Integer replyCnt;

  /** 첨부파일 보유 여부 */
  private String fileyn;

  /** 공지 고정 여부 (Y/N) */
  private String noticeYn;

  /** 상태 (0: 블라인드, 1: 정상) */
  private Integer status;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /* ---------------- 조인 값 (DB 컬럼 아님) ---------------- */

  /** 작성자 닉네임 */
  private String nickname;

  /** 작성자 프로필 이미지 */
  private String profileImg;

  /** 작성자 볼더링 자가 등급 — "V5 클라이머의 조언"처럼 신뢰도 표시에 씁니다. */
  private String boulderLevel;

  /** 연관 암장명 (gno가 있을 때만) */
  private String gname;

  /* ---------------- 응답 전용 파생값 ---------------- */

  /**
   * 로그인 회원이 이 글에 좋아요를 눌렀는지 여부.
   * <p>비로그인 요청에서는 null로 두어 JSON에서 아예 빠지게 합니다.
   * false로 내리면 프론트가 "안 누름"과 "로그인 안 함"을 구분할 수 없습니다.</p>
   */
  private Boolean liked;

  /** 로그인 회원이 수정/삭제 버튼을 볼 수 있는지 (작성자 본인 또는 관리자) */
  private Boolean editable;

  /**
   * JPQL 생성자 표현식 전용 — <b>목록</b>용. (content 제외)
   *
   * <p>파라미터 순서는 JPQL SELECT 절 순서와 1:1로 대응합니다.</p>
   */
  public BoardDTO(Long no, Integer type, Long mno, String title,
      Long gno, String sido, String meetDate, Integer dealPrice, Integer dealStatus,
      Integer vcnt, Integer likeCnt, Integer replyCnt, String fileyn, String noticeYn,
      Integer status, String cdate, String udate,
      String nickname, String profileImg, String boulderLevel, String gname) {
    this.no = no;
    this.type = type;
    this.mno = mno;
    this.title = title;
    this.gno = gno;
    this.sido = sido;
    this.meetDate = meetDate;
    this.dealPrice = dealPrice;
    this.dealStatus = dealStatus;
    this.vcnt = vcnt;
    this.likeCnt = likeCnt;
    this.replyCnt = replyCnt;
    this.fileyn = fileyn;
    this.noticeYn = noticeYn;
    this.status = status;
    this.cdate = cdate;
    this.udate = udate;
    this.nickname = nickname;
    this.profileImg = profileImg;
    this.boulderLevel = boulderLevel;
    this.gname = gname;
  }

  /**
   * JPQL 생성자 표현식 전용 — <b>상세</b>용. (content 포함)
   */
  public BoardDTO(Long no, Integer type, Long mno, String title, String content,
      Long gno, String sido, String meetDate, Integer dealPrice, Integer dealStatus,
      Integer vcnt, Integer likeCnt, Integer replyCnt, String fileyn, String noticeYn,
      Integer status, String cdate, String udate,
      String nickname, String profileImg, String boulderLevel, String gname) {
    this(no, type, mno, title, gno, sido, meetDate, dealPrice, dealStatus,
        vcnt, likeCnt, replyCnt, fileyn, noticeYn, status, cdate, udate,
        nickname, profileImg, boulderLevel, gname);
    this.content = content;
  }

  /**
   * DTO -> Entity. (게시글 등록)
   *
   * <p>통계 컬럼(vcnt/likeCnt/replyCnt)은 <b>클라이언트 값을 신뢰하지 않고</b> 0으로 시작합니다.
   * noticeYn도 여기서는 항상 'N'이며, 공지 고정은 관리자 전용 경로에서만 켭니다.
   * mno는 Service가 로그인 정보로 덮어씁니다(요청 바디의 mno를 믿으면 남의 이름으로 글이 써집니다).</p>
   */
  public Board toEntity() {
    return Board.builder()
        .no(this.no)
        .type(this.type == null ? Board.TYPE_FREE : this.type)
        .mno(this.mno)
        .title(this.title)
        // 본문은 화면에 그대로 출력되므로 저장 전에 HTML 특수문자를 이스케이프합니다(XSS 방어).
        .content(Tool.escapeHtml(this.content))
        .gno(this.gno)
        .sido(this.sido)
        .meetDate(this.meetDate)
        .dealPrice(this.dealPrice)
        .dealStatus(this.dealStatus)
        .vcnt(0)
        .likeCnt(0)
        .replyCnt(0)
        .fileyn("Y".equalsIgnoreCase(this.fileyn) ? "Y" : "N")
        .noticeYn("N")
        .status(Board.STATUS_NORMAL)
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /** Entity -> DTO (작성자 조인 없이 단건 변환이 필요할 때) */
  public static BoardDTO fromEntity(Board entity) {
    if (entity == null) return null;
    return BoardDTO.builder()
        .no(entity.getNo())
        .type(entity.getType())
        .mno(entity.getMno())
        .title(entity.getTitle())
        .content(entity.getContent())
        .gno(entity.getGno())
        .sido(entity.getSido())
        .meetDate(entity.getMeetDate())
        .dealPrice(entity.getDealPrice())
        .dealStatus(entity.getDealStatus())
        .vcnt(entity.getVcnt())
        .likeCnt(entity.getLikeCnt())
        .replyCnt(entity.getReplyCnt())
        .fileyn(entity.getFileyn())
        .noticeYn(entity.getNoticeYn())
        .status(entity.getStatus())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .build();
  }
}
