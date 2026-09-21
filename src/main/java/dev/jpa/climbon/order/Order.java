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
 * 주문 엔티티. — ORDERS 테이블
 *
 * <p><b>[면접 포인트] 테이블명이 왜 ORDER가 아니라 ORDERS인가?</b><br>
 * {@code ORDER}는 SQL 예약어({@code ORDER BY})라 Oracle에서 테이블명으로 쓰면
 * {@code CREATE TABLE ORDER (...)} 부터 실패합니다.
 * 그래서 관례적으로 복수형 ORDERS를 씁니다. Java 클래스명은 도메인 용어인 {@code Order}를 쓰고
 * {@code @Table(name = "ORDERS")}로 실제 테이블을 지정합니다.</p>
 *
 * <p><b>[실무 팁] {@code @Entity(name = "Orders")}를 함께 지정한 이유</b><br>
 * 엔티티 이름은 <b>JPQL에서 쓰는 이름</b>입니다. 기본값은 클래스명인 {@code Order}인데,
 * {@code SELECT o FROM Order o} 라고 쓰면 HQL 파서가 {@code ORDER} 키워드와 충돌할 수 있습니다.
 * 엔티티 이름을 {@code Orders}로 바꿔 두면 JPQL에서 {@code FROM Orders o}로 안전하게 쓸 수 있습니다.
 * (DTO 생성자 표현식에서는 클래스의 <b>패키지 전체 경로</b>를 쓰므로 영향이 없습니다.)</p>
 */
@Entity(name = "Orders")
@Table(name = "ORDERS")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {

  /* ======================================================================
   * 상태 상수
   *   PAY_STATUS      0: 대기, 1: 완료, 2: 취소, 3: 환불
   *   DELIVERY_STATUS 0: 준비, 1: 출고, 2: 배송중, 3: 완료
   * ====================================================================== */
  public static final int PAY_WAIT = 0;
  public static final int PAY_DONE = 1;
  public static final int PAY_CANCEL = 2;
  public static final int PAY_REFUND = 3;

  public static final int DELIVERY_READY = 0;
  public static final int DELIVERY_SHIPPED = 1;
  public static final int DELIVERY_ON_THE_WAY = 2;
  public static final int DELIVERY_DONE = 3;

  /** 주문번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq_use")
  @SequenceGenerator(name = "orders_seq_use", sequenceName = "ORDERS_SEQ", allocationSize = 1)
  private Long no;

  /**
   * 주문코드 (화면 노출용: 20260918-000001).
   *
   * <p><b>왜 PK를 그대로 보여주지 않는가?</b><br>
   * PK를 노출하면 "1번 주문 다음은 2번"이라는 사실이 드러나 <b>총 주문 건수가 추측</b>되고,
   * 번호를 하나씩 바꿔 가며 남의 주문을 조회하려는 시도가 쉬워집니다.
   * 날짜가 섞인 코드를 별도로 두면 고객센터 문의 시 "언제 주문"인지도 바로 알 수 있습니다.</p>
   */
  private String orderCode;

  /** 주문 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 주문명 ("스카르파 인스팅트 외 2건") — 목록에서 한 줄로 보여주기 위한 요약 */
  private String orderName;

  /** 총 결제금액 (상품 합계 + 배송비) */
  private Integer totalPrice;

  /** 배송비 */
  @Builder.Default
  private Integer deliveryFee = 0;

  /** 결제수단 (CARD/BANK/KAKAO/TOSS) */
  private String payMethod;

  /** 결제상태 (0: 대기, 1: 완료, 2: 취소, 3: 환불) */
  @Builder.Default
  private int payStatus = PAY_WAIT;

  /**
   * PG(토스페이먼츠) 결제승인키(paymentKey).
   *
   * <p>결제가 <b>실제로 승인된 뒤에만</b> 채워집니다(OrderService.confirmTossPayment).
   * 주문 생성 시점에는 아직 결제가 이루어지지 않았으므로 항상 NULL이고,
   * CARD/BANK/KAKAO처럼 PG 연동이 없는 결제수단은 승인 과정 자체가 없어 계속 NULL로 남습니다.
   * 나중에 결제 취소/환불 API를 붙일 때 "토스의 어느 거래를 가리키는지" 특정하는 키로 씁니다.</p>
   */
  private String payKey;

  /** 배송상태 (0: 준비, 1: 출고, 2: 배송중, 3: 완료) */
  @Builder.Default
  private int deliveryStatus = DELIVERY_READY;

  /** 수령인 */
  private String receiver;

  /** 연락처 */
  private String phone;

  /** 우편번호 */
  private String zipcode;

  /** 배송 주소 */
  private String addr;

  /** 상세 주소 */
  private String addrDetail;

  /** 배송 메모 */
  private String memo;

  /** 주문일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  // ==========================================================
  // 도메인 메서드
  // ==========================================================

  /**
   * 주문을 취소할 수 있는 상태인지 판단합니다.
   *
   * <p><b>[면접 포인트] 취소 가능 조건을 엔티티가 들고 있는 이유</b><br>
   * "배송중이면 취소 불가"는 <b>주문이라는 개념 자체의 규칙</b>입니다.
   * 이 판단을 Service나 Controller에 흩어 놓으면 관리자 취소, 배치 취소 등
   * 경로가 늘어날 때마다 같은 조건을 복사하게 되고 그중 하나는 반드시 빠집니다.
   * 규칙을 엔티티에 두면 "주문을 가진 곳이면 어디서든" 같은 답을 얻습니다.</p>
   *
   * <p>이미 출고되어 물건이 이동 중이면 시스템에서 되돌릴 수 없고,
   * 반품 절차(환불)로 넘겨야 하므로 취소가 아니라 별개의 흐름입니다.</p>
   */
  public boolean isCancelable() {
    return this.payStatus != PAY_CANCEL
        && this.payStatus != PAY_REFUND
        && this.deliveryStatus < DELIVERY_ON_THE_WAY;
  }

  /** 주문 취소 처리 — 결제상태를 '취소(2)'로 바꿉니다. */
  public void cancel(String udate) {
    this.payStatus = PAY_CANCEL;
    this.udate = udate;
  }

  /** 관리자 상태 변경 (null인 항목은 변경하지 않음) */
  public void changeStatus(Integer payStatus, Integer deliveryStatus, String udate) {
    if (payStatus != null) this.payStatus = payStatus;
    if (deliveryStatus != null) this.deliveryStatus = deliveryStatus;
    this.udate = udate;
  }

  /** 주문자 본인인지 확인 — 남의 주문을 조회/취소하지 못하게 막는 기준입니다. */
  public boolean isOwner(Long mno) {
    return mno != null && mno.equals(this.mno);
  }
}
