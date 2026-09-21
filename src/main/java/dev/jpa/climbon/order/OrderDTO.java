package dev.jpa.climbon.order;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 주문 DTO. (생성 요청 / 목록 / 상세 공용)
 *
 * <p><b>주문 생성 요청에서 서버가 신뢰하는 값과 무시하는 값</b>
 * <ul>
 *   <li>신뢰: 배송지({@code receiver}, {@code phone}, {@code addr} ...), 결제수단, 배송메모,
 *       그리고 직접 주문 시 {@code items}의 {@code pno / qty / optSize}</li>
 *   <li><b>무시</b>: {@code totalPrice}, {@code deliveryFee}, {@code orderCode}, {@code mno},
 *       {@code payStatus} — 전부 서버가 계산하거나 토큰에서 가져옵니다.</li>
 * </ul>
 * 금액을 클라이언트가 보낸 값으로 저장하면 <b>1원 결제</b>가 가능해집니다.
 * "클라이언트가 보낸 값 중 돈과 권한에 관한 것은 하나도 믿지 않는다"가 원칙입니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {

  /** 주문번호 (요청 시 null) */
  private Long no;

  /** 주문코드 (20260918-000001) — 서버 생성 */
  private String orderCode;

  /** 주문 회원번호 — 서버가 토큰에서 채웁니다. */
  private Long mno;

  /** 주문명 ("스카르파 인스팅트 외 2건") — 서버 생성 */
  private String orderName;

  /** 총 결제금액 (상품합계 + 배송비) — 서버 계산 */
  private Integer totalPrice;

  /** 배송비 — 서버 계산 */
  private Integer deliveryFee;

  /** 결제수단 (CARD/BANK/KAKAO/TOSS) */
  private String payMethod;

  /** 결제상태 (0: 대기, 1: 완료, 2: 취소, 3: 환불) */
  private Integer payStatus;

  /**
   * PG(토스페이먼츠) 결제승인키 — 응답 전용입니다.
   * 요청 바디로 보내도 {@link #toEntity()}가 옮기지 않으므로 클라이언트가 조작할 수 없습니다
   * (결제완료 조작 방지 — 이 값은 오직 {@code OrderService.confirmTossPayment}만 채웁니다).
   */
  private String payKey;

  /** 배송상태 (0: 준비, 1: 출고, 2: 배송중, 3: 완료) */
  private Integer deliveryStatus;

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

  /* ---------------- 조인/파생값 (DB 컬럼 아님) ---------------- */

  /**
   * 주문 상품 목록.
   * <ul>
   *   <li><b>요청</b>: 비워 두면 <b>장바구니 전체</b>를 주문합니다("장바구니 주문").
   *       값이 있으면 그 목록만 주문합니다("바로 구매").</li>
   *   <li><b>응답</b>: 주문 시점 스냅샷이 담겨 내려갑니다.</li>
   * </ul>
   */
  private List<OrderItemDTO> items;

  /** 상품 합계 금액 (배송비 제외) — 응답 전용 */
  private Integer itemsPrice;

  /** 주문자 닉네임 — 관리자 목록에서만 채워집니다. */
  private String nickname;

  /** 취소 가능 여부 — 프론트가 "주문취소" 버튼을 보여줄지 판단하는 값 */
  private Boolean cancelable;

  /**
   * JPQL 생성자 표현식 전용 생성자. (주문 + 주문자 닉네임)
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응합니다.</p>
   */
  public OrderDTO(Long no, String orderCode, Long mno, String orderName,
      Integer totalPrice, Integer deliveryFee, String payMethod,
      Integer payStatus, Integer deliveryStatus,
      String receiver, String phone, String zipcode, String addr, String addrDetail,
      String memo, String cdate, String udate, String nickname) {
    this.no = no;
    this.orderCode = orderCode;
    this.mno = mno;
    this.orderName = orderName;
    this.totalPrice = totalPrice;
    this.deliveryFee = deliveryFee;
    this.payMethod = payMethod;
    this.payStatus = payStatus;
    this.deliveryStatus = deliveryStatus;
    this.receiver = receiver;
    this.phone = phone;
    this.zipcode = zipcode;
    this.addr = addr;
    this.addrDetail = addrDetail;
    this.memo = memo;
    this.cdate = cdate;
    this.udate = udate;
    this.nickname = nickname;

    this.itemsPrice = (totalPrice == null ? 0 : totalPrice) - (deliveryFee == null ? 0 : deliveryFee);
    // 엔티티의 isCancelable()과 같은 규칙입니다. (배송중 이상이거나 이미 취소/환불이면 불가)
    this.cancelable = payStatus != null && deliveryStatus != null
        && payStatus != Order.PAY_CANCEL && payStatus != Order.PAY_REFUND
        && deliveryStatus < Order.DELIVERY_ON_THE_WAY;
  }

  /**
   * 배송지 정보만 엔티티로 옮깁니다. (주문 생성)
   *
   * <p>금액·주문코드·회원번호·상태는 <b>일부러 담지 않습니다.</b>
   * OrderService가 상품을 조회해 계산한 뒤 직접 채웁니다.
   * "요청 DTO가 만들 수 있는 것"과 "서버만 만들 수 있는 것"을 구조적으로 갈라 두면
   * 나중에 누가 코드를 고쳐도 금액 조작 경로가 생기지 않습니다.</p>
   */
  public Order toEntity() {
    return Order.builder()
        .receiver(this.receiver)
        .phone(this.phone)
        .zipcode(this.zipcode)
        .addr(this.addr)
        .addrDetail(this.addrDetail)
        .memo(Tool.escapeHtml(this.memo))
        .payMethod(this.payMethod)
        .payStatus(Order.PAY_WAIT)
        .deliveryStatus(Order.DELIVERY_READY)
        .deliveryFee(0)
        .totalPrice(0)
        .cdate(Tool.getDate())
        .build();
  }

  /** Entity -> DTO (상세 조회용 — items는 호출부에서 따로 채웁니다) */
  public static OrderDTO fromEntity(Order entity) {
    if (entity == null) return null;

    int total = entity.getTotalPrice() == null ? 0 : entity.getTotalPrice();
    int fee = entity.getDeliveryFee() == null ? 0 : entity.getDeliveryFee();

    return OrderDTO.builder()
        .no(entity.getNo())
        .orderCode(entity.getOrderCode())
        .mno(entity.getMno())
        .orderName(entity.getOrderName())
        .totalPrice(total)
        .deliveryFee(fee)
        .payMethod(entity.getPayMethod())
        .payStatus(entity.getPayStatus())
        .payKey(entity.getPayKey())
        .deliveryStatus(entity.getDeliveryStatus())
        .receiver(entity.getReceiver())
        .phone(entity.getPhone())
        .zipcode(entity.getZipcode())
        .addr(entity.getAddr())
        .addrDetail(entity.getAddrDetail())
        .memo(entity.getMemo())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .itemsPrice(total - fee)
        .cancelable(entity.isCancelable())
        .build();
  }
}
