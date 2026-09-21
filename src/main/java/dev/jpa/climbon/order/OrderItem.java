package dev.jpa.climbon.order;

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
 * 주문 상세(주문 상품) 엔티티. — ORDER_ITEM 테이블
 *
 * <p><b>[면접 포인트] 왜 상품명·단가·썸네일을 여기에 복사(스냅샷)해 두는가?</b><br>
 * 주문 내역 화면을 {@code ORDER_ITEM JOIN PRODUCT} 로 만들면 편해 보이지만 치명적인 문제가 있습니다.
 * <ol>
 *   <li><b>가격이 바뀌면 과거 주문서까지 바뀝니다.</b> 5만원에 산 신발이 가격 인상 후
 *       "7만원 결제됨"으로 보이면 그 자체가 분쟁이고, 전자상거래법상으로도 문제가 됩니다.</li>
 *   <li><b>상품명이 바뀌거나 상품이 삭제되면 내역이 깨집니다.</b>
 *       판매 종료된 상품을 지우면 주문 상세가 텅 비게 됩니다.</li>
 *   <li>주문서는 <b>그 시점에 체결된 계약의 사본</b>입니다. 계약서 내용이 나중에
 *       한쪽의 사정으로 바뀌면 그건 이미 계약서가 아닙니다.</li>
 * </ol>
 * 그래서 PNO(현재 상품으로 가는 링크)는 남기되, <b>화면에 보여줄 값은 주문 시점 값을 복사</b>합니다.
 * 이것이 "정규화를 일부러 어기는" 대표적인 사례이고, 그 이유가 성능이 아니라
 * <b>시간에 따라 변하지 않아야 하는 사실(historical data)</b>이라는 점이 핵심입니다.</p>
 */
@Entity
@Table(name = "ORDER_ITEM")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {

  /** 주문상세번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq_use")
  @SequenceGenerator(name = "order_item_seq_use", sequenceName = "ORDER_ITEM_SEQ", allocationSize = 1)
  private Long no;

  /** 주문번호 (FK -> ORDERS.NO) */
  private Long ono;

  /** 상품번호 (FK -> PRODUCT.NO) — "상품 다시 보기" 링크용 */
  private Long pno;

  /** 주문 시점 상품명 (스냅샷) */
  private String pname;

  /** 주문 시점 단가 (스냅샷) — 할인가가 있었다면 할인가가 저장됩니다. */
  private Integer price;

  /** 수량 */
  private Integer qty;

  /** 선택 사이즈 */
  private String optSize;

  /** 주문 시점 썸네일 (스냅샷) */
  private String thumb;

  /** 이 줄의 금액 (단가 × 수량) — 저장 컬럼이 아니라 계산값입니다. */
  public int getLineTotal() {
    return (price == null ? 0 : price) * (qty == null ? 0 : qty);
  }
}
