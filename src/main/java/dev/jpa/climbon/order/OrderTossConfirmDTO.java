package dev.jpa.climbon.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 토스페이먼츠 결제창(SDK)이 successUrl로 돌려준 값을 그대로 받는 요청 DTO.
 *
 * <p>토스 결제창은 결제가 끝나면 프론트의 successUrl을 브라우저 리다이렉트로 호출하면서
 * {@code paymentKey}, {@code orderId}, {@code amount} 세 값을 쿼리 파라미터로 붙여줍니다.
 * 이 값만으로는 "결제가 진짜로 끝났다"고 믿을 수 없습니다 — 리다이렉트는 브라우저가 하는 일이라
 * 사용자가 URL을 직접 조작해 아무 값이나 넣어 이 API를 호출할 수 있기 때문입니다.
 * 그래서 {@link OrderService#confirmTossPayment(OrderTossConfirmDTO)}가 이 값들로
 * <b>서버 → 토스 서버</b> 승인 API를 한 번 더 호출해 진위를 확인합니다.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class OrderTossConfirmDTO {

  /** 토스가 발급한 결제 건 고유키 */
  private String paymentKey;

  /** 우리가 넘긴 주문 식별자 — 이 프로젝트에서는 ORDERS.ORDER_CODE를 그대로 씁니다. */
  private String orderId;

  /** 결제 금액 — 서버가 저장해 둔 주문 금액(TOTAL_PRICE)과 반드시 같아야 합니다. */
  private Long amount;
}
