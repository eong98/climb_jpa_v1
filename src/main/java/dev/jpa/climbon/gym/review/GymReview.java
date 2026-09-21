package dev.jpa.climbon.gym.review;

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
 * 암장 리뷰 엔티티. — GYM_REVIEW 테이블
 *
 * <p>종합 평점(rating) 외에 시설/루트 다양성/청결도 세부 점수를 따로 받습니다.
 * 별점 하나만 받으면 "시설은 좋은데 루트가 단조롭다" 같은 정보가 사라지고,
 * 나중에 AI 리뷰 요약의 근거 데이터로도 쓸 수 없기 때문입니다.</p>
 */
@Entity
@Table(name = "GYM_REVIEW")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GymReview {

  /** 리뷰번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gym_review_seq_use")
  @SequenceGenerator(name = "gym_review_seq_use", sequenceName = "GYM_REVIEW_SEQ", allocationSize = 1)
  private Long no;

  /** 암장번호 (FK -> GYM.NO) */
  private Long gno;

  /** 작성 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 종합 평점 (1.0 ~ 5.0) */
  private Double rating;

  /** 시설 점수 (1~5) */
  private Integer scoreFacility;

  /** 루트 다양성 점수 (1~5) */
  private Integer scoreRoute;

  /** 청결도 점수 (1~5) */
  private Integer scoreClean;

  /** 리뷰 제목 */
  private String title;

  /** 리뷰 내용 (CLOB) */
  @Lob
  @Column(name = "CONTENT")
  private String content;

  /** 방문일 (yyyy-MM-dd) */
  private String visitDate;

  /** 도움돼요 수 */
  @Builder.Default
  private int likeCnt = 0;

  /** 첨부 이미지 보유 여부 (Y/N) */
  @Builder.Default
  private String fileyn = "N";

  /** (AI) 리뷰 요약 캐시 */
  private String aiSummary;

  /** (AI) 감성 분석 결과 (POSITIVE/NEUTRAL/NEGATIVE) */
  private String aiSentiment;

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

  /** 리뷰 내용 수정 */
  public void updateReview(Double rating, Integer scoreFacility, Integer scoreRoute, Integer scoreClean,
      String title, String content, String visitDate, String fileyn, String udate) {
    this.rating = rating;
    this.scoreFacility = scoreFacility;
    this.scoreRoute = scoreRoute;
    this.scoreClean = scoreClean;
    this.title = title;
    this.content = content;
    this.visitDate = visitDate;
    if (fileyn != null) this.fileyn = fileyn;
    this.udate = udate;
  }

  /**
   * 도움돼요 수를 증감합니다.
   *
   * <p>0 아래로는 내려가지 않게 막습니다. 동시 요청으로 감소가 겹쳐도
   * 음수 카운트라는 눈에 띄는 데이터 오염은 남지 않게 하기 위한 방어입니다.</p>
   */
  public void changeLikeCnt(int delta) {
    this.likeCnt = Math.max(0, this.likeCnt + delta);
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
