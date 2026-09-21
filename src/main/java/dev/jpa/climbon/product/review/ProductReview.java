package dev.jpa.climbon.product.review;

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
 * 상품 후기 엔티티. — PRODUCT_REVIEW 테이블
 *
 * <p>암장 리뷰(GYM_REVIEW)와 달리 세부 점수 컬럼이 없고 평점 + 내용 + 구매 사이즈만 받습니다.
 * 장비 후기에서 가장 궁금한 정보가 <b>"내 발 사이즈에 이 사이즈가 맞았는지"</b>라서
 * {@code optSize}를 별도 컬럼으로 둔 것이 이 도메인의 특징입니다.</p>
 */
@Entity
@Table(name = "PRODUCT_REVIEW")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductReview {

  /** 후기번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_review_seq_use")
  @SequenceGenerator(name = "product_review_seq_use", sequenceName = "PRODUCT_REVIEW_SEQ", allocationSize = 1)
  private Long no;

  /** 상품번호 (FK -> PRODUCT.NO) */
  private Long pno;

  /** 작성 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 평점 (1.0 ~ 5.0) */
  private Double rating;

  /** 내용 (VARCHAR2(2000)) */
  private String content;

  /** 구매 사이즈 — "정사이즈보다 반 치수 크게" 같은 판단에 쓰입니다. */
  private String optSize;

  /** 첨부 이미지 보유 여부 (Y/N) */
  @Builder.Default
  private String fileyn = "N";

  /** 등록일시 */
  private String cdate;

  /** 삭제 여부 (Y/N) */
  @Builder.Default
  private String isdel = "N";

  // ==========================================================
  // 상태 변경 전용 메서드
  // ==========================================================

  /**
   * 논리 삭제.
   * <p>물리삭제하지 않는 이유: 신고/분쟁 대응 시 원본이 필요하고,
   * 평점 재계산 이력을 나중에 검증할 수 있어야 하기 때문입니다.</p>
   */
  public void delete() {
    this.isdel = "Y";
  }

  /** 작성자 본인인지 확인 */
  public boolean isWriter(Long mno) {
    return mno != null && mno.equals(this.mno);
  }
}
