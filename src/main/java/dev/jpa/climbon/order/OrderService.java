package dev.jpa.climbon.order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;   // [추가] 같은 타입 빈 구분용
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.jpa.climbon.cart.Cart;
import dev.jpa.climbon.cart.CartRepository;
import dev.jpa.climbon.cart.CartService;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.product.Product;
import dev.jpa.climbon.product.ProductRepository;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 주문 서비스. — 이 프로젝트에서 <b>가장 정합성이 중요한 모듈</b>
 *
 * <p>주문은 "돈 · 재고 · 장바구니"라는 서로 다른 세 가지 상태를 <b>동시에</b> 바꿉니다.
 * 그래서 이 클래스의 설계 포인트는 두 가지로 압축됩니다.</p>
 *
 * <ol>
 *   <li><b>스냅샷</b> — 주문 시점의 상품명/단가/썸네일을 ORDER_ITEM에 복사해 둔다.</li>
 *   <li><b>트랜잭션</b> — 검증·저장·재고차감·장바구니 비우기를 전부 하나로 묶는다.</li>
 * </ol>
 *
 * <p><b>[면접 포인트] 왜 클래스에 {@code @Transactional(readOnly = true)}를 걸고
 * 쓰기 메서드에만 다시 {@code @Transactional}을 붙이나?</b><br>
 * 조회 메서드에 readOnly를 주면 Hibernate가 <b>플러시를 생략</b>하고(FlushMode.MANUAL)
 * 변경 감지를 위한 스냅샷도 만들지 않아 메모리와 CPU를 아낍니다.
 * 또 DB 드라이버에 읽기 전용임을 알려 일부 DB에서는 읽기 전용 커넥션을 쓸 수 있습니다.
 * 무엇보다 "조회 메서드에서 실수로 엔티티를 수정해 UPDATE가 나가는" 사고를 구조적으로 막습니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final CartRepository cartRepository;
  private final CartService cartService;
  private final ProductRepository productRepository;

  /** 토스페이먼츠 결제승인 전용 RestClient — 시크릿 키 인증 헤더가 미리 실려 있습니다. (RestClientConfig 참고) */
  @Qualifier("tossRestClient")   // [추가] RestClient 빈이 2개라 이름으로 명시 (lombok.config가 생성자로 복사)
  private final RestClient tossRestClient;
  /** 토스 API의 오류 응답(JSON)에서 사람이 읽을 메시지만 뽑아내는 데 씁니다. */
  private final ObjectMapper objectMapper;

  /** 주문명에 노출할 대표 상품명 최대 길이 — ORDER_NAME이 VARCHAR2(200)이라 여유 있게 자릅니다. */
  private static final int ORDER_NAME_CUT = 100;

  /* ======================================================================
   * 주문 생성
   * ====================================================================== */

  /**
   * 주문 생성. — 이 모듈의 핵심
   *
   * <p>처리 순서
   * <ol>
   *   <li>주문할 목록 확보: 요청에 {@code items}가 있으면 "바로 구매", 없으면 <b>장바구니 전체</b></li>
   *   <li>상품 조회 + 재고·판매상태 검증 (부족하면 {@link IllegalStateException})</li>
   *   <li>ORDERS 저장 (금액은 <b>서버가 계산한 값</b>만 사용)</li>
   *   <li>주문 시점의 상품명/단가/썸네일을 ORDER_ITEM에 <b>스냅샷</b>으로 저장</li>
   *   <li>재고 차감 + 판매수량(SELL_CNT) 증가</li>
   *   <li>장바구니 비우기</li>
   *   <li>ORDER_CODE 생성 ({@code Tool.getOrderCode(저장된 order.getNo())})</li>
   * </ol>
   * </p>
   *
   * <p><b>[면접 포인트] ④ 왜 상품 정보를 ORDER_ITEM에 복사(스냅샷)하는가?</b><br>
   * 주문 상세를 {@code ORDER_ITEM JOIN PRODUCT}로 그리면 편하지만,
   * <b>나중에 상품 가격이 바뀌면 과거 주문서의 금액까지 함께 바뀝니다.</b>
   * 5만원에 결제한 신발이 가격 인상 후 "7만원 결제됨"으로 보이면 그 자체가 분쟁이고,
   * 상품이 논리삭제되면 주문 내역이 통째로 비어 버립니다.
   * 주문서는 <b>그 시점에 체결된 계약의 사본</b>이라 시간이 지나도 변하면 안 됩니다.
   * 그래서 PNO(현재 상품으로 가는 링크)는 남기되 화면에 보여줄 값은 전부 복사해 둡니다.
   * 정규화를 일부러 어기는 대표 사례이고, 이유가 성능이 아니라
   * <b>변하면 안 되는 사실(historical data)</b>이라는 점이 핵심입니다.</p>
   *
   * <p><b>[면접 포인트] 왜 ①~⑦ 전체를 하나의 {@code @Transactional}로 묶는가?</b><br>
   * 이 흐름은 ORDERS INSERT, ORDER_ITEM INSERT, PRODUCT UPDATE(재고), CART DELETE 까지
   * <b>서로 다른 네 개의 테이블</b>을 건드립니다. 트랜잭션이 없다면
   * <ul>
   *   <li>재고는 줄었는데 ORDER_ITEM 저장에서 예외 → <b>팔리지도 않았는데 재고만 사라짐</b></li>
   *   <li>주문은 만들어졌는데 재고 차감 실패 → 재고보다 많이 팔림(오버셀)</li>
   *   <li>주문 실패인데 장바구니만 비워짐 → 사용자가 담은 목록을 통째로 잃음</li>
   * </ul>
   * 같은 "절반만 성공한 상태"가 남습니다. 돈과 재고가 걸린 작업에서 이 상태는 복구가 매우 어렵습니다.
   * 하나의 트랜잭션으로 묶으면 <b>전부 성공하거나 전부 없던 일이 되거나</b> 둘 중 하나만 남습니다(원자성).</p>
   *
   * <p><b>[실무 팁] 이 구현의 한계 — 동시성</b><br>
   * 재고를 "읽어서 검사하고 빼는" 구조라 두 명이 동시에 마지막 1개를 주문하면 둘 다 통과할 수 있습니다.
   * 실무에서는 {@code SELECT ... FOR UPDATE}(비관적 락), {@code @Version}(낙관적 락),
   * 혹은 {@code UPDATE PRODUCT SET STOCK = STOCK - :qty WHERE NO = :no AND STOCK >= :qty} 의
   * 갱신 건수(0이면 실패)로 막습니다. 학습용이라 트랜잭션 경계만 정확히 잡고 락은 생략했습니다.</p>
   *
   * @param dto 배송지/결제수단/주문 상품 목록 (금액·주문코드·회원번호는 요청 값을 무시합니다)
   * @return 생성된 주문 상세 (주문코드 포함)
   */
  @Transactional
  public OrderDTO createOrder(OrderDTO dto) {
    Long mno = requireLogin();
    validateDelivery(dto);

    // --- ① 주문할 목록 확보 ------------------------------------------------
    // 요청 items가 비어 있으면 "장바구니 주문", 있으면 "바로 구매"입니다.
    boolean fromCart = (dto.getItems() == null || dto.getItems().isEmpty());
    List<OrderItemDTO> lines = fromCart ? toLinesFromCart(mno) : normalize(dto.getItems());

    if (lines.isEmpty()) {
      throw new IllegalStateException("주문할 상품이 없습니다.");
    }

    // --- ② 상품 조회 + 재고/판매상태 검증 ----------------------------------
    // 검증을 먼저 "전부" 끝내고 나서 저장에 들어갑니다.
    // 저장과 검증을 섞으면 5번째 상품에서 실패했을 때 앞의 4건이 이미 반영된 상태가 됩니다.
    // (트랜잭션이 롤백해 주긴 하지만, 검증을 앞에 모으면 사용자에게 '무엇이 부족한지'도 한 번에 알려줄 수 있습니다.)
    List<Product> products = new ArrayList<>();
    int itemsPrice = 0;

    for (OrderItemDTO line : lines) {
      Product product = productRepository.findByNoAndIsdel(line.getPno(), "N")
          .orElseThrow(() -> new IllegalStateException(
              "존재하지 않거나 판매 종료된 상품이 포함되어 있습니다. pno=" + line.getPno()));

      if (!product.isOrderable()) {
        throw new IllegalStateException("현재 구매할 수 없는 상품입니다. (" + product.getPname() + ")");
      }
      if (product.getStock() < line.getQty()) {
        throw new IllegalStateException(
            "재고가 부족합니다. (" + product.getPname() + " — 남은 수량 " + product.getStock() + "개)");
      }

      products.add(product);
      // 금액은 절대 요청 값을 쓰지 않고 DB의 실제 판매가(할인가 우선)로 계산합니다.
      itemsPrice += product.getRealPrice() * line.getQty();
    }

    int deliveryFee = cartService.calcDeliveryFee(itemsPrice);

    // --- ③ ORDERS 저장 -----------------------------------------------------
    Order order = dto.toEntity();           // 배송지/결제수단만 담긴 엔티티
    order.setMno(mno);                      // 소유자는 요청 바디가 아니라 토큰에서
    order.setTotalPrice(itemsPrice + deliveryFee);
    order.setDeliveryFee(deliveryFee);
    order.setOrderName(buildOrderName(products));
    // ORDER_CODE는 NOT NULL인데 PK가 나와야 만들 수 있습니다.
    // 시퀀스 전략이라 save() 시점에 NO가 채워지므로, 임시값을 넣고 바로 아래에서 교체합니다.
    order.setOrderCode("PENDING");

    Order saved = orderRepository.save(order);

    // --- ④ 스냅샷 저장 + ⑤ 재고 차감 ---------------------------------------
    List<OrderItem> items = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      OrderItemDTO line = lines.get(i);
      Product product = products.get(i);

      // ④ 주문 "시점"의 값을 복사합니다. product를 참조로 들고 있지 않는 것이 핵심입니다.
      items.add(OrderItem.builder()
          .ono(saved.getNo())
          .pno(product.getNo())
          .pname(product.getPname())
          .price(product.getRealPrice())
          .qty(line.getQty())
          .optSize(line.getOptSize())
          .thumb(product.getThumb())
          .build());

      // ⑤ 재고 차감 + SELL_CNT 증가 (Product.decreaseStock이 두 가지를 함께 처리합니다)
      if (!product.decreaseStock(line.getQty())) {
        // ②에서 이미 검증했지만, 검증과 차감 사이에 상태가 바뀔 수 있으므로 최종 방어선을 둡니다.
        throw new IllegalStateException("재고가 부족합니다. (" + product.getPname() + ")");
      }
    }
    orderItemRepository.saveAll(items);

    // --- ⑥ 장바구니 비우기 -------------------------------------------------
    if (fromCart) {
      // 장바구니 전체를 주문했으므로 통째로 비웁니다.
      cartService.clearCart();
    } else {
      // "바로 구매"로 주문한 상품만 장바구니에서 제거합니다.
      // 담아 둔 다른 상품까지 지우면 사용자는 목록을 잃어버린 것으로 느낍니다.
      cartRepository.deleteByMnoAndPnoIn(mno, lines.stream().map(OrderItemDTO::getPno).toList());
    }

    // --- ⑦ ORDER_CODE 생성 -------------------------------------------------
    // 영속 상태 엔티티의 필드를 바꾸면 JPA 변경 감지가 UPDATE를 자동 생성합니다.
    saved.setOrderCode(Tool.getOrderCode(saved.getNo()));

    OrderDTO result = OrderDTO.fromEntity(saved);
    result.setItems(items.stream().map(OrderItemDTO::fromEntity).toList());
    return result;
  }

  /* ======================================================================
   * 주문 취소
   * ====================================================================== */

  /**
   * 주문 취소. — PAY_STATUS를 2(취소)로 바꾸고 재고를 원복합니다.
   *
   * <p><b>[면접 포인트] 왜 행을 지우지 않고 상태만 바꾸나?</b><br>
   * 취소된 주문도 <b>일어난 사실</b>입니다. 전자상거래법상 거래 기록은 보관 의무가 있고,
   * 매출 통계·정산·고객 문의 대응에도 취소 이력이 필요합니다.
   * 물리삭제하면 ORDER_ITEM의 FK까지 함께 지워야 해서 복구도 불가능합니다.</p>
   *
   * <p><b>왜 배송중(2) 이상이면 취소할 수 없나?</b><br>
   * 물건이 이미 창고를 떠났기 때문입니다. 시스템에서 상태만 되돌리면
   * "취소됐는데 물건은 도착하는" 상태가 되고, 재고도 실제로는 돌아오지 않았는데
   * 숫자만 늘어나 재고 정합성이 깨집니다. 이 경우는 취소가 아니라
   * <b>반품/환불</b>이라는 별개의 흐름으로 처리해야 합니다.</p>
   *
   * <p>재고 원복에 {@code Product.increaseStock()}을 쓰면 SELL_CNT도 함께 줄어듭니다.
   * 되돌리지 않으면 취소된 주문이 베스트 상품 순위를 계속 끌어올립니다.</p>
   */
  @Transactional
  public void cancelOrder(Long no) {
    Long mno = requireLogin();

    Order order = orderRepository.findById(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. no=" + no));

    // 본인 또는 관리자만 취소할 수 있습니다. 경로변수(no)만 믿으면 남의 주문도 취소됩니다.
    if (!order.isOwner(mno) && !SecurityUtil.isAdmin()) {
      throw new IllegalStateException("본인의 주문만 취소할 수 있습니다.");
    }

    if (order.getDeliveryStatus() >= Order.DELIVERY_ON_THE_WAY) {
      throw new IllegalStateException("이미 배송이 시작되어 취소할 수 없습니다. 반품 신청을 이용해 주세요.");
    }
    if (!order.isCancelable()) {
      throw new IllegalStateException("이미 취소되었거나 환불된 주문입니다.");
    }

    // 재고 원복 — 주문 상세에 남아 있는 "주문 당시 수량"을 기준으로 되돌립니다.
    for (OrderItem item : orderItemRepository.findByOnoOrderByNoAsc(order.getNo())) {
      productRepository.findById(item.getPno())
          .ifPresent(product -> product.increaseStock(item.getQty()));
      // 상품이 이미 물리삭제된 예외적인 경우에도 주문 취소 자체는 진행되어야 하므로
      // ifPresent로 조용히 건너뜁니다. (주문 취소가 막히는 편이 사용자에게 더 나쁩니다)
    }

    order.cancel(Tool.getDate());
  }

  /* ======================================================================
   * 토스페이먼츠 결제 승인
   * ====================================================================== */

  /**
   * 토스페이먼츠 결제 승인. — {@code POST /order/toss/confirm}
   *
   * <p><b>[면접 포인트] successUrl로 받은 값을 왜 그대로 믿으면 안 되나?</b><br>
   * 결제창(SDK)이 끝나면 브라우저가 successUrl로 <b>스스로</b> 이동합니다.
   * 이건 서버 간 통신이 아니라 사용자 브라우저의 리다이렉트라, 사용자가 개발자도구로
   * {@code paymentKey}, {@code orderId}, {@code amount} 쿼리값을 마음대로 바꿔
   * "결제 성공"을 위장해 이 API를 호출할 수 있습니다.
   * 그래서 이 세 값은 <b>단서일 뿐</b>이고, 진짜 승인은 반드시 서버가
   * 시크릿 키로 토스 서버에 다시 확인해야 합니다(아래 ③).</p>
   *
   * <p>처리 순서
   * <ol>
   *   <li>주문 조회 + 본인 확인 (남의 주문번호로 결제를 확정시키는 것을 방지)</li>
   *   <li>금액 대조 — 프론트가 보낸 {@code amount}가 <b>주문 생성 시 서버가 계산해 저장해 둔</b>
   *       {@code TOTAL_PRICE}와 정확히 같은지 확인 (다르면 결제 금액 조작 시도로 간주해 거부)</li>
   *   <li>토스 결제 승인 API({@code POST /v1/payments/confirm}) 호출 — 여기서 실패하면
   *       (카드 한도 초과, 이미 취소된 결제 등) 토스가 4xx로 거절합니다</li>
   *   <li>성공하면 PAY_STATUS를 완료(1)로 바꾸고 승인키를 저장</li>
   * </ol>
   * </p>
   *
   * <p><b>[실무 팁] 멱등성</b><br>
   * 새로고침이나 브라우저 뒤로가기로 이 API가 같은 결제 건에 대해 두 번 호출될 수 있습니다.
   * 이미 완료(PAY_DONE) 상태인 주문이면 토스를 다시 호출하지 않고 <b>그대로</b> 성공 응답을 돌려줍니다
   * (토스 승인 API는 이미 승인된 paymentKey로 다시 호출하면 오류를 내려줍니다).</p>
   */
  @Transactional
  public OrderDTO confirmTossPayment(OrderTossConfirmDTO req) {
    Long mno = requireLogin();

    if (Tool.isEmpty(req.getPaymentKey()) || Tool.isEmpty(req.getOrderId()) || req.getAmount() == null) {
      throw new IllegalArgumentException("결제 승인에 필요한 값이 부족합니다.");
    }

    // ① 주문 조회 + 본인 확인 — 토스의 orderId는 곧 우리 ORDER_CODE입니다.
    Order order = orderRepository.findByOrderCode(req.getOrderId())
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. orderId=" + req.getOrderId()));

    if (!order.isOwner(mno)) {
      throw new IllegalStateException("본인의 주문만 결제할 수 있습니다.");
    }

    // 이미 승인된 주문이면 토스를 다시 부르지 않고 그대로 돌려줍니다 (멱등 처리).
    if (order.getPayStatus() == Order.PAY_DONE) {
      return toOrderDetail(order);
    }
    if (!order.isCancelable()) {
      throw new IllegalStateException("이미 취소되었거나 환불된 주문은 결제를 진행할 수 없습니다.");
    }

    // ② 금액 대조 — 프론트가 보낸 금액이 아니라 항상 서버가 저장해 둔 금액을 기준으로 판단합니다.
    if (order.getTotalPrice() == null || order.getTotalPrice() != req.getAmount().intValue()) {
      throw new IllegalStateException("결제 금액이 주문 금액과 일치하지 않습니다.");
    }

    // ③ 토스 결제 승인 API 호출 — 이 호출이 성공해야 "진짜로 결제된 것"입니다.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("paymentKey", req.getPaymentKey());
    body.put("orderId", req.getOrderId());
    body.put("amount", req.getAmount());

    try {
      tossRestClient.post()
          .uri("/v1/payments/confirm")
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      // 토스가 4xx/5xx로 거절한 경우 — 응답 본문에 사람이 읽을 메시지가 들어 있습니다.
      throw new IllegalStateException("결제 승인이 거절되었습니다: " + extractTossMessage(e));
    }

    // ④ 승인 완료 반영
    order.setPayStatus(Order.PAY_DONE);
    order.setPayKey(req.getPaymentKey());
    order.setUdate(Tool.getDate());

    return toOrderDetail(order);
  }

  /** 토스 API의 JSON 오류 응답에서 {@code message} 필드만 뽑아냅니다. 파싱이 실패하면 원문을 그대로 돌려줍니다. */
  private String extractTossMessage(RestClientResponseException e) {
    String raw = e.getResponseBodyAsString();
    try {
      Map<String, Object> body = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
      Object message = body.get("message");
      return message != null ? message.toString() : raw;
    } catch (Exception parseError) {
      return raw;
    }
  }

  /** 주문 + 주문 상세(스냅샷)를 함께 담은 응답을 만듭니다. */
  private OrderDTO toOrderDetail(Order order) {
    OrderDTO dto = OrderDTO.fromEntity(order);
    dto.setItems(orderItemRepository.findItemDTOs(order.getNo()));
    return dto;
  }

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 내 주문 목록. (페이징 + 주문 상세 묶음)
   *
   * <p><b>[면접 포인트] N+1을 어떻게 피했나?</b><br>
   * 주문 10건을 그리면서 주문마다 상세를 조회하면 쿼리가 1 + 10 = 11번 나갑니다.
   * 현재 페이지의 주문번호만 모아 {@code findByOnoIn()} 으로 <b>한 번에</b> 읽고
   * 자바에서 {@code Map<주문번호, List<상세>>}로 묶어 쿼리 2번으로 끝냅니다.</p>
   */
  public Page<OrderDTO> getMyOrders(int page, int size) {
    Long mno = requireLogin();

    Pageable pageable = PageRequest.of(page, size);
    Page<OrderDTO> orders = orderRepository.findMyOrders(mno, pageable);

    attachItems(orders.getContent());
    return orders;
  }

  /**
   * 주문 상세.
   *
   * <p>본인 또는 관리자만 볼 수 있습니다. 주문 상세에는 수령인·연락처·주소 같은
   * <b>개인정보</b>가 들어 있어 번호만 바꿔 조회되면 그대로 개인정보 유출입니다.
   * (주문코드를 따로 두는 이유도 PK 추측을 어렵게 하기 위함이지만,
   *  코드 노출 여부와 무관하게 서버는 항상 소유자를 확인해야 합니다.)</p>
   */
  public OrderDTO getOrder(Long no) {
    Long mno = requireLogin();

    OrderDTO order = orderRepository.findOrderDetail(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. no=" + no));

    if (!mno.equals(order.getMno()) && !SecurityUtil.isAdmin()) {
      throw new IllegalStateException("본인의 주문만 조회할 수 있습니다.");
    }

    order.setItems(orderItemRepository.findItemDTOs(no));
    return order;
  }

  /**
   * 관리자 주문 목록. (주문코드/수령인 검색 + 상태 필터 + 페이징)
   *
   * <p>관리자 화면은 "어떤 주문을 지금 출고해야 하는가"를 찾는 곳이라
   * 결제상태·배송상태 필터가 검색어보다 훨씬 자주 쓰입니다.</p>
   */
  public Page<OrderDTO> getAdminOrders(String word, Integer payStatus, Integer deliveryStatus,
      int page, int size) {
    requireAdmin();

    Pageable pageable = PageRequest.of(page, size);
    Page<OrderDTO> orders = orderRepository.searchOrders(word, payStatus, deliveryStatus, pageable);

    attachItems(orders.getContent());
    return orders;
  }

  /**
   * 관리자 주문 상태 변경. (결제상태 / 배송상태)
   *
   * <p>null로 들어온 항목은 "변경하지 않음"으로 처리합니다
   * ({@link Order#changeStatus(Integer, Integer, String)}).
   * 배송상태만 바꾸려고 호출했는데 결제상태가 0으로 초기화되는 사고를 막기 위해서입니다.</p>
   *
   * <p><b>주의</b>: 여기서는 재고를 건드리지 않습니다.
   * 관리자가 상태를 '취소(2)'로 바꿔야 한다면 재고 원복이 함께 일어나야 하므로
   * 이 메서드가 아니라 {@link #cancelOrder(Long)} 를 호출해야 합니다.
   * 상태 변경과 재고 원복이 서로 다른 경로로 갈라지면 반드시 한쪽이 빠집니다.</p>
   */
  @Transactional
  public void changeStatus(Long no, Integer payStatus, Integer deliveryStatus) {
    requireAdmin();

    if (payStatus == null && deliveryStatus == null) {
      throw new IllegalArgumentException("변경할 상태 값이 없습니다.");
    }
    if (payStatus != null && payStatus == Order.PAY_CANCEL) {
      throw new IllegalStateException("취소 처리는 주문 취소 API(PUT /order/{no}/cancel)를 사용해야 재고가 원복됩니다.");
    }

    Order order = orderRepository.findById(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다. no=" + no));

    order.changeStatus(payStatus, deliveryStatus, Tool.getDate());
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /**
   * 주문 목록에 상세(스냅샷)를 붙입니다. — IN 절 한 번으로 N+1 제거
   *
   * <p>{@code LinkedHashMap}으로 그룹핑해 주문 안의 상품 순서가 항상 같게 유지합니다.
   * 순서가 흔들리면 새로고침할 때마다 화면이 들썩입니다.</p>
   */
  private void attachItems(List<OrderDTO> orders) {
    if (orders.isEmpty()) return;

    List<Long> onoList = orders.stream().map(OrderDTO::getNo).toList();

    Map<Long, List<OrderItemDTO>> itemMap = new LinkedHashMap<>();
    for (OrderItem item : orderItemRepository.findByOnoIn(onoList)) {
      itemMap.computeIfAbsent(item.getOno(), k -> new ArrayList<>())
          .add(OrderItemDTO.fromEntity(item));
    }

    for (OrderDTO order : orders) {
      order.setItems(itemMap.getOrDefault(order.getNo(), List.of()));
    }
  }

  /**
   * 장바구니를 주문 줄로 변환합니다.
   *
   * <p>여기서는 상품 정보를 채우지 않습니다. 검증과 스냅샷 생성이
   * {@link #createOrder(OrderDTO)} 한 곳에 모여 있어야
   * "장바구니 주문"과 "바로 구매"가 같은 규칙을 따르기 때문입니다.</p>
   */
  private List<OrderItemDTO> toLinesFromCart(Long mno) {
    List<Cart> carts = cartRepository.findByMno(mno);

    List<OrderItemDTO> lines = new ArrayList<>();
    for (Cart cart : carts) {
      lines.add(OrderItemDTO.builder()
          .pno(cart.getPno())
          .qty(cart.getQty())
          .optSize(cart.getOptSize())
          .build());
    }
    return lines;
  }

  /**
   * "바로 구매" 요청의 줄을 정규화합니다.
   *
   * <p>요청 바디의 {@code pname}, {@code price}는 <b>통째로 버립니다.</b>
   * 클라이언트가 보낸 가격을 그대로 저장하면 10만원짜리를 100원에 사는
   * 가격 조작이 가능해집니다. 서버가 신뢰하는 값은 {@code pno / qty / optSize} 뿐입니다.</p>
   */
  private List<OrderItemDTO> normalize(List<OrderItemDTO> items) {
    List<OrderItemDTO> lines = new ArrayList<>();

    for (OrderItemDTO item : items) {
      if (item.getPno() == null) {
        throw new IllegalArgumentException("상품번호(pno)는 필수입니다.");
      }
      if (item.getQty() == null || item.getQty() < 1) {
        throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
      }

      lines.add(OrderItemDTO.builder()
          .pno(item.getPno())
          .qty(item.getQty())
          // Oracle은 빈 문자열을 NULL로 저장하므로 자바 쪽에서도 null로 통일합니다.
          .optSize(Tool.isEmpty(item.getOptSize()) ? null : item.getOptSize().trim())
          .build());
    }
    return lines;
  }

  /**
   * 주문명을 만듭니다. ("스카르파 인스팅트 외 2건")
   *
   * <p>목록 화면에서 주문 한 건을 한 줄로 보여주기 위한 <b>요약 컬럼</b>입니다.
   * 매번 ORDER_ITEM을 조인해 만들 수도 있지만, 목록마다 조인이 하나 늘어나고
   * 주문명은 바뀔 일이 없는 값이라 저장 시점에 만들어 두는 편이 낫습니다(반정규화).</p>
   */
  private String buildOrderName(List<Product> products) {
    String first = Tool.cut(products.get(0).getPname(), ORDER_NAME_CUT);
    return products.size() == 1 ? first : first + " 외 " + (products.size() - 1) + "건";
  }

  /** 배송지 필수값 검증 — DB의 NOT NULL 제약에 걸려 500이 나기 전에 400으로 걸러냅니다. */
  private void validateDelivery(OrderDTO dto) {
    if (Tool.isEmpty(dto.getReceiver())) {
      throw new IllegalArgumentException("수령인은 필수입니다.");
    }
    if (Tool.isEmpty(dto.getPhone())) {
      throw new IllegalArgumentException("연락처는 필수입니다.");
    }
    if (Tool.isEmpty(dto.getAddr())) {
      throw new IllegalArgumentException("배송 주소는 필수입니다.");
    }
  }

  /** 로그인 회원번호를 얻고, 비로그인이면 예외를 던집니다. */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new IllegalStateException("로그인이 필요합니다.");
    }
    return mno;
  }

  /** 관리자 권한 확인 — 프론트에서 메뉴를 숨기는 것만으로는 API 호출을 막을 수 없습니다. */
  private void requireAdmin() {
    if (!SecurityUtil.isAdmin()) {
      throw new IllegalStateException("관리자 권한이 필요합니다.");
    }
  }
}
