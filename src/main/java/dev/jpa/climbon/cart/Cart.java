package dev.jpa.climbon.cart;

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
 * 장바구니 엔티티. — CART 테이블
 *
 * <p><b>[면접 포인트] 장바구니에 상품명/가격을 복사해 두지 않는 이유</b><br>
 * 주문(ORDER_ITEM)은 <b>스냅샷</b>을 저장하지만 장바구니는 그 반대로 PNO만 들고
 * 조회 시점에 PRODUCT를 조인합니다. 이유는 둘의 성격이 정반대이기 때문입니다.
 * <ul>
 *   <li>주문서는 <b>과거의 계약</b>입니다. 그때의 가격이 영원히 유지돼야 합니다.</li>
 *   <li>장바구니는 <b>아직 사지 않은 목록</b>입니다. 가격이 내렸으면 내린 가격이,
 *       품절이면 품절이 보여야 사용자가 올바른 판단을 합니다.</li>
 * </ul>
 * 담을 때 가격을 복사해 두면 "장바구니에는 5만원인데 결제하면 7만원"이 되어 분쟁이 납니다.</p>
 *
 * <p><b>[면접 포인트] 같은 상품+같은 사이즈를 다시 담으면 왜 행을 늘리지 않고 수량만 올리나?</b><br>
 * 행이 늘어나면 장바구니에 "스카르파 240 × 1"이 세 줄로 보입니다. 사용자는 이것을
 * 명백한 버그로 인식하고, 수량 변경/삭제 UI도 어느 줄을 건드려야 할지 모호해집니다.
 * 또 주문 생성 때 같은 상품을 여러 번 재고 차감하게 되어 로직이 복잡해집니다.
 * <b>(MNO, PNO, OPT_SIZE)를 논리적 유일키로 보고 수량만 증가</b>시키는 것이 정답입니다.
 * (스키마에 UNIQUE 제약이 없으므로 이 규칙은 애플리케이션이 책임집니다.
 *  OPT_SIZE가 NULL일 수 있어 Oracle에서는 UNIQUE 제약으로 완전히 막기도 어렵습니다 —
 *  NULL끼리는 서로 다른 값으로 취급되기 때문입니다.)</p>
 */
@Entity
@Table(name = "CART")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Cart {

  /** 장바구니번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_seq_use")
  @SequenceGenerator(name = "cart_seq_use", sequenceName = "CART_SEQ", allocationSize = 1)
  private Long no;

  /** 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 상품번호 (FK -> PRODUCT.NO) */
  private Long pno;

  /** 수량 */
  @Builder.Default
  private int qty = 1;

  /** 선택 사이즈 — 사이즈 옵션이 없는 상품이면 null */
  private String optSize;

  /** 담은 일시 */
  private String cdate;

  // ==========================================================
  // 상태 변경 전용 메서드
  // ==========================================================

  /**
   * 수량을 더합니다. (같은 상품+사이즈를 다시 담았을 때)
   * <p>재고를 넘지 않도록 상한을 함께 받습니다. 담는 단계에서 막아 두면
   * 결제 직전에야 "재고가 부족합니다"를 보는 나쁜 경험을 줄일 수 있습니다.</p>
   */
  public void addQty(int delta, int maxStock) {
    int next = this.qty + delta;
    this.qty = Math.max(1, Math.min(next, Math.max(1, maxStock)));
  }

  /** 수량을 지정 값으로 변경합니다. (수량 입력창) */
  public void changeQty(int qty, int maxStock) {
    this.qty = Math.max(1, Math.min(qty, Math.max(1, maxStock)));
  }

  /** 내 장바구니 행인지 확인 — 남의 장바구니를 수정/삭제하지 못하게 막는 기준입니다. */
  public boolean isOwner(Long mno) {
    return mno != null && mno.equals(this.mno);
  }
}
