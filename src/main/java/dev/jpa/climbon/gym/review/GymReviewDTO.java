package dev.jpa.climbon.gym.review;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 암장 리뷰 DTO.
 *
 * <p><b>[면접 포인트] 작성자 정보를 왜 DTO 생성자 표현식으로 가져오나? (N+1 방지)</b><br>
 * 리뷰 목록에는 작성자 닉네임·프로필 이미지·실력 등급이 함께 보여야 합니다.
 * 리뷰 엔티티만 조회한 뒤 반복문에서 {@code memberRepository.findById(mno)}를 호출하면
 * 리뷰 10건에 회원 조회 10번이 추가로 나갑니다(= N+1 문제).
 * {@code @ManyToOne}으로 연관관계를 걸어도 지연로딩이면 결국 같은 일이 벌어집니다.</p>
 *
 * <p>그래서 JPQL에서 Member를 JOIN하고
 * {@code SELECT new dev.jpa.climbon.gym.review.GymReviewDTO(r.no, ..., m.nickname, ...)}
 * <b>생성자 표현식</b>으로 필요한 컬럼만 뽑아 DTO를 바로 만듭니다.
 * 결과적으로 <b>쿼리 1번</b>으로 끝나고, 엔티티를 영속화하지 않아 메모리도 덜 씁니다.</p>
 *
 * <p>주의: 생성자 표현식은 <b>파라미터 순서·타입이 JPQL과 정확히 일치</b>해야 합니다.
 * 아래 {@link #GymReviewDTO(Long, Long, Long, Double, Integer, Integer, Integer,
 * String, String, String, Integer, String, String, String, String, String, String)}
 * 생성자가 그 계약이며, 함부로 파라미터를 끼워 넣으면 안 됩니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymReviewDTO {

  /** 리뷰번호 (등록 시 null) */
  private Long no;

  /** 암장번호 */
  private Long gno;

  /** 작성 회원번호 */
  private Long mno;

  /** 종합 평점 (1.0 ~ 5.0) */
  private Double rating;

  /** 시설 점수 */
  private Integer scoreFacility;

  /** 루트 다양성 점수 */
  private Integer scoreRoute;

  /** 청결도 점수 */
  private Integer scoreClean;

  /** 제목 */
  private String title;

  /** 내용 */
  private String content;

  /** 방문일 (yyyy-MM-dd) */
  private String visitDate;

  /** 도움돼요 수 */
  private Integer likeCnt;

  /** 첨부 이미지 보유 여부 */
  private String fileyn;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /* ---------------- MEMBER 조인 값 (DB 컬럼 아님) ---------------- */

  /** 작성자 닉네임 */
  private String nickname;

  /** 작성자 프로필 이미지 */
  private String profileImg;

  /** 작성자 볼더링 자가 등급 (V0~V12) — "V5 클라이머가 남긴 후기"처럼 신뢰도 표시에 씁니다. */
  private String boulderLevel;

  /** 암장명 — 내가 쓴 리뷰 목록에서만 채워집니다. */
  private String gname;

  /**
   * JPQL 생성자 표현식 전용 생성자. (리뷰 + 작성자 정보)
   *
   * <p>파라미터 순서는 JPQL의 SELECT 절 순서와 1:1로 대응됩니다.</p>
   */
  public GymReviewDTO(Long no, Long gno, Long mno, Double rating,
      Integer scoreFacility, Integer scoreRoute, Integer scoreClean,
      String title, String content, String visitDate,
      Integer likeCnt, String fileyn, String cdate, String udate,
      String nickname, String profileImg, String boulderLevel) {
    this.no = no;
    this.gno = gno;
    this.mno = mno;
    this.rating = rating;
    this.scoreFacility = scoreFacility;
    this.scoreRoute = scoreRoute;
    this.scoreClean = scoreClean;
    this.title = title;
    this.content = content;
    this.visitDate = visitDate;
    this.likeCnt = likeCnt;
    this.fileyn = fileyn;
    this.cdate = cdate;
    this.udate = udate;
    this.nickname = nickname;
    this.profileImg = profileImg;
    this.boulderLevel = boulderLevel;
  }

  /**
   * JPQL 생성자 표현식 전용 생성자. (내가 쓴 리뷰 — 작성자 대신 암장명을 조인)
   */
  public GymReviewDTO(Long no, Long gno, Long mno, Double rating,
      String title, String content, String visitDate,
      Integer likeCnt, String cdate, String gname) {
    this.no = no;
    this.gno = gno;
    this.mno = mno;
    this.rating = rating;
    this.title = title;
    this.content = content;
    this.visitDate = visitDate;
    this.likeCnt = likeCnt;
    this.cdate = cdate;
    this.gname = gname;
  }

  /**
   * DTO -> Entity. (리뷰 등록)
   *
   * <p>likeCnt는 항상 0에서 시작하고, mno는 서비스가 로그인 정보로 덮어씁니다.
   * 요청 바디의 mno를 그대로 믿으면 남의 이름으로 리뷰를 쓸 수 있습니다.</p>
   */
  public GymReview toEntity() {
    return GymReview.builder()
        .no(this.no)
        .gno(this.gno)
        .mno(this.mno)
        .rating(this.rating)
        .scoreFacility(this.scoreFacility)
        .scoreRoute(this.scoreRoute)
        .scoreClean(this.scoreClean)
        .title(this.title)
        // 리뷰 본문은 화면에 그대로 출력되므로 저장 전에 HTML 특수문자를 이스케이프합니다(XSS 방어).
        .content(Tool.escapeHtml(this.content))
        .visitDate(this.visitDate)
        .likeCnt(0)
        .fileyn("Y".equalsIgnoreCase(this.fileyn) ? "Y" : "N")
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /** Entity -> DTO (작성자 정보 없이 단건 조회용) */
  public static GymReviewDTO fromEntity(GymReview entity) {
    if (entity == null) return null;
    return GymReviewDTO.builder()
        .no(entity.getNo())
        .gno(entity.getGno())
        .mno(entity.getMno())
        .rating(entity.getRating())
        .scoreFacility(entity.getScoreFacility())
        .scoreRoute(entity.getScoreRoute())
        .scoreClean(entity.getScoreClean())
        .title(entity.getTitle())
        .content(entity.getContent())
        .visitDate(entity.getVisitDate())
        .likeCnt(entity.getLikeCnt())
        .fileyn(entity.getFileyn())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .build();
  }
}
