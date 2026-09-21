package dev.jpa.climbon.product;

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
 * 클라이밍 상품 엔티티. — PRODUCT 테이블
 *
 * <p><b>[면접 포인트] PRICE와 SALE_PRICE를 왜 따로 두나?</b><br>
 * 할인가 하나만 저장하면 "정가 대비 30% 할인" 같은 표시를 할 수 없고,
 * 할인이 끝났을 때 원래 가격으로 되돌릴 근거가 사라집니다.
 * 그래서 <b>정가(price)는 고정</b>, <b>할인가(salePrice)는 있을 때만</b> 채우고
 * 실제 판매가는 {@code salePrice != null ? salePrice : price} 로 계산합니다.
 * 이 규칙은 {@link #getRealPrice()} 한 곳에만 두어 코드 전체가 같은 답을 쓰게 합니다.
 * (SQL에서는 같은 의미가 {@code COALESCE(SALE_PRICE, PRICE)} 입니다.)</p>
 *
 * <p><b>[면접 포인트] 재고(stock)를 엔티티 메서드로만 바꾸는 이유</b><br>
 * 재고는 돈과 직결되는 값이라 <b>음수가 되면 안 됩니다.</b>
 * {@code setStock()}을 아무 데서나 부르면 검증이 빠진 경로가 반드시 하나 생깁니다.
 * {@link #decreaseStock(int)} / {@link #increaseStock(int)} 로만 바꾸게 하면
 * "재고 부족" 판정이 한 곳에 모여 빠뜨릴 수 없습니다.</p>
 */
@Entity
@Table(name = "PRODUCT")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {

  /* ======================================================================
   * CATEGORY 상수
   *  0: 암벽화 1: 초크/초크백 2: 하네스 3: 의류
   *  4: 크래시패드 5: 확보장비(퀵드로/로프) 6: 기타 액세서리
   * ====================================================================== */
  public static final int CATEGORY_SHOES = 0;
  public static final int CATEGORY_CHALK = 1;
  public static final int CATEGORY_HARNESS = 2;
  public static final int CATEGORY_APPAREL = 3;
  public static final int CATEGORY_CRASHPAD = 4;
  public static final int CATEGORY_PROTECTION = 5;
  public static final int CATEGORY_ETC = 6;

  /** 상태 — 0: 판매중지, 1: 판매중, 2: 품절 */
  public static final int STATUS_STOPPED = 0;
  public static final int STATUS_ON_SALE = 1;
  public static final int STATUS_SOLD_OUT = 2;

  /** 상품번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq_use")
  @SequenceGenerator(name = "product_seq_use", sequenceName = "PRODUCT_SEQ", allocationSize = 1)
  private Long no;

  /** 카테고리 (0~6) */
  @Builder.Default
  private int category = CATEGORY_SHOES;

  /** 브랜드 (스카르파, 라스포르티바 ...) */
  private String brand;

  /** 상품명 */
  private String pname;

  /** 한줄 설명 */
  private String summary;

  /** 상세 설명 (CLOB) */
  @Lob
  @Column(name = "CONTENT")
  private String content;

  /** 정가 */
  private Integer price;

  /** 판매가 (할인가) — null이면 정가가 곧 판매가입니다. */
  private Integer salePrice;

  /** 재고 수량 */
  @Builder.Default
  private int stock = 0;

  /** 사이즈 옵션 (쉼표 구분: "230,235,240") */
  private String sizeInfo;

  /** 성별 (0: 공용, 1: 남성, 2: 여성) */
  @Builder.Default
  private int gender = 0;

  /** 추천 레벨 태그 (입문 / 중급 / 상급) */
  private String levelTag;

  /** 대표 이미지 파일명 */
  private String thumb;

  /** 평균 평점 — 후기 변경 시 재계산되는 반정규화 컬럼 */
  @Builder.Default
  private Double ratingAvg = 0.0;

  /** 리뷰 수 — 반정규화 컬럼 */
  @Builder.Default
  private int reviewCnt = 0;

  /** 조회수 */
  @Builder.Default
  private int vcnt = 0;

  /** 판매 수량 — 베스트 상품 정렬 기준 */
  @Builder.Default
  private int sellCnt = 0;

  /** 상태 (0: 판매중지, 1: 판매중, 2: 품절) */
  @Builder.Default
  private int status = STATUS_ON_SALE;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) */
  @Builder.Default
  private String isdel = "N";

  // ==========================================================
  // 도메인 메서드
  // ==========================================================

  /**
   * 실제 판매가. (할인가가 있으면 할인가, 없으면 정가)
   * <p>주문 시 ORDER_ITEM에 스냅샷으로 복사되는 값이 바로 이 값입니다.</p>
   */
  public int getRealPrice() {
    return (this.salePrice != null) ? this.salePrice : (this.price == null ? 0 : this.price);
  }

  /** 주문 가능한 상태인지 (판매중 + 삭제 안 됨) */
  public boolean isOrderable() {
    return "N".equals(this.isdel) && this.status == STATUS_ON_SALE;
  }

  /**
   * 재고 차감. (주문 생성 시)
   *
   * <p>재고가 모자라면 {@code false}를 돌려주고 <b>아무것도 바꾸지 않습니다.</b>
   * 예외를 던지지 않고 boolean을 돌려주는 이유는, 호출부에서
   * "어떤 상품이 몇 개 부족한지"를 모아 사용자에게 한 번에 알려주기 위함입니다.</p>
   *
   * <p><b>[실무 팁] 이 방식의 한계</b><br>
   * 엔티티를 읽어 검사하고 빼는 구조라 동시에 두 명이 마지막 1개를 주문하면
   * 둘 다 통과할 수 있습니다(갱신 손실). 실무에서는
   * {@code SELECT ... FOR UPDATE}(비관적 락) 또는 {@code @Version}(낙관적 락),
   * 혹은 {@code UPDATE PRODUCT SET STOCK = STOCK - :qty WHERE NO = :no AND STOCK >= :qty}
   * 의 갱신 건수(0이면 실패)로 막습니다.
   * 이 프로젝트는 학습용이라 트랜잭션 경계만 정확히 잡고 락은 생략했습니다.</p>
   *
   * @return 차감 성공 여부
   */
  public boolean decreaseStock(int qty) {
    if (qty <= 0 || this.stock < qty) {
      return false;
    }
    this.stock -= qty;
    this.sellCnt += qty;

    // 재고가 0이 되면 자동으로 품절 상태로 바꿔 목록에서 "품절" 배지가 뜨게 합니다.
    if (this.stock == 0) {
      this.status = STATUS_SOLD_OUT;
    }
    return true;
  }

  /**
   * 재고 원복. (주문 취소 시)
   * <p>판매 수량도 함께 되돌립니다. 되돌리지 않으면 취소된 주문이
   * 베스트 상품 순위를 계속 끌어올리게 됩니다.</p>
   */
  public void increaseStock(int qty) {
    if (qty <= 0) return;
    this.stock += qty;
    this.sellCnt = Math.max(0, this.sellCnt - qty);

    // 품절로 내려갔던 상품은 재고가 생기면 다시 판매중으로 돌립니다.
    // (관리자가 의도적으로 내린 '판매중지(0)'는 건드리지 않습니다.)
    if (this.status == STATUS_SOLD_OUT && this.stock > 0) {
      this.status = STATUS_ON_SALE;
    }
  }

  /** 상세 조회 시 조회수 1 증가 */
  public void increaseVcnt() {
    this.vcnt += 1;
  }

  /**
   * 후기 통계(평균 평점 / 리뷰 수)를 갱신합니다.
   * <p>ProductReviewService가 후기 등록·삭제 직후에 호출합니다.</p>
   */
  public void applyReviewStats(Double ratingAvg, long reviewCnt) {
    // 후기가 하나도 없으면 AVG 결과가 null로 오므로 0으로 떨어뜨립니다.
    this.ratingAvg = (ratingAvg == null) ? 0.0 : Math.round(ratingAvg * 100) / 100.0;
    this.reviewCnt = (int) reviewCnt;
  }

  /** 논리 삭제 */
  public void delete(String udate) {
    this.isdel = "Y";
    this.udate = udate;
  }
}
