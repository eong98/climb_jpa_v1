package dev.jpa.climbon.order;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 주문 컨트롤러. — {@code /order}
 *
 * <p><b>[실무 팁] 왜 여기서만 try-catch로 예외를 잡아 400으로 내리나?</b><br>
 * 주문 실패 사유("재고가 부족합니다", "이미 배송이 시작되어 취소할 수 없습니다")는
 * <b>사용자가 읽고 행동을 바꿔야 하는 정보</b>입니다.
 * 예외를 그대로 던지면 Spring이 500을 내려보내고 프론트는 "서버 오류"만 표시하게 됩니다.
 * 사용자는 자기 잘못(재고 부족)인데 서비스가 고장 난 것으로 오해하고,
 * 운영팀에는 실제 장애와 구분되지 않는 500 로그가 쌓입니다.
 * 그래서 <b>예상 가능한 실패는 400 + 메시지</b>로 내려 프론트가 그대로 보여줄 수 있게 합니다.</p>
 *
 * <p>모든 엔드포인트는 <b>내 데이터</b>를 다루므로 회원번호를 경로/바디로 받지 않습니다.
 * Service가 토큰에서 직접 꺼냅니다 — 파라미터로 받으면 남의 번호를 넣어 조회·취소할 수 있습니다.</p>
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderCont {

  private final OrderService orderService;

  /**
   * 주문 생성.
   * <pre>
   * POST /order
   * {
   *   "receiver":"홍길동", "phone":"010-1234-5678",
   *   "zipcode":"06236", "addr":"서울 강남구 ...", "addrDetail":"301호",
   *   "payMethod":"CARD", "memo":"부재시 경비실",
   *   "items":[{"pno":7,"qty":1,"optSize":"240"}]   // 생략하면 장바구니 전체 주문
   * }
   * </pre>
   *
   * <p>{@code totalPrice}, {@code deliveryFee}, {@code orderCode}를 보내도 <b>무시</b>합니다.
   * 금액은 서버가 DB의 실제 판매가로 다시 계산합니다(1원 결제 방지).</p>
   */
  @PostMapping
  public ResponseEntity<?> createOrder(@RequestBody OrderDTO dto) {
    try {
      OrderDTO order = orderService.createOrder(dto);

      Map<String, Object> body = new HashMap<>();
      body.put("no", order.getNo());
      body.put("orderCode", order.getOrderCode());
      body.put("totalPrice", order.getTotalPrice());
      body.put("order", order);
      body.put("message", "주문이 완료되었습니다.");
      return ResponseEntity.status(HttpStatus.CREATED).body(body);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 내 주문 목록.
   * <pre>GET /order/my?page=0&amp;size=10</pre>
   *
   * <p>각 주문에 주문 상세(스냅샷)가 함께 담겨 내려가므로
   * 프론트는 목록에서 바로 "스카르파 인스팅트 외 2건"과 썸네일을 그릴 수 있습니다.</p>
   */
  @GetMapping("/my")
  public ResponseEntity<?> getMyOrders(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    try {
      Page<OrderDTO> result = orderService.getMyOrders(page, size);
      return ResponseEntity.ok(PageResponse.of(result));

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 관리자 주문 목록.
   * <pre>GET /order/list/admin?word=20260918&amp;payStatus=1&amp;deliveryStatus=0&amp;page=0&amp;size=10</pre>
   *
   * <p><b>주의</b>: 이 매핑은 {@code /order/{no}} 보다 <b>위에</b> 있어야 합니다.
   * 아래에 두면 "list"가 {@code {no}}로 해석돼 숫자 변환 오류(400)가 납니다.</p>
   *
   * @param word 주문코드 또는 수령인 부분일치
   */
  @GetMapping("/list/admin")
  public ResponseEntity<?> getAdminOrders(
      @RequestParam(name = "word", required = false) String word,
      @RequestParam(name = "payStatus", required = false) Integer payStatus,
      @RequestParam(name = "deliveryStatus", required = false) Integer deliveryStatus,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    try {
      Page<OrderDTO> result = orderService.getAdminOrders(word, payStatus, deliveryStatus, page, size);
      return ResponseEntity.ok(PageResponse.of(result));

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 주문 상세.
   * <pre>GET /order/12</pre>
   *
   * <p>본인 또는 관리자만 조회할 수 있습니다. 수령인·연락처·주소가 들어 있어
   * 번호만 바꿔 조회되면 그대로 개인정보 유출이기 때문입니다.</p>
   */
  @GetMapping("/{no}")
  public ResponseEntity<?> getOrder(@PathVariable("no") Long no) {
    try {
      return ResponseEntity.ok(orderService.getOrder(no));

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 토스페이먼츠 결제 승인.
   * <pre>
   * POST /order/toss/confirm
   * { "paymentKey":"...", "orderId":"20260918-000001", "amount": 133000 }
   * </pre>
   *
   * <p>토스 결제창(SDK)이 successUrl로 돌려준 값을 프론트가 그대로 전달합니다.
   * 서버는 이 값을 그대로 믿지 않고 토스 서버에 다시 승인을 요청해 확인한 뒤에만
   * 결제상태를 완료로 바꿉니다 (자세한 이유는 {@link OrderService#confirmTossPayment} 참고).</p>
   */
  @PostMapping("/toss/confirm")
  public ResponseEntity<?> confirmTossPayment(@RequestBody OrderTossConfirmDTO dto) {
    try {
      OrderDTO order = orderService.confirmTossPayment(dto);

      Map<String, Object> body = new HashMap<>();
      body.put("order", order);
      body.put("message", "결제가 완료되었습니다.");
      return ResponseEntity.ok(body);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 주문 취소.
   * <pre>PUT /order/12/cancel</pre>
   *
   * <p>결제상태를 2(취소)로 바꾸고 재고를 원복합니다.
   * 배송중(2) 이상이면 취소할 수 없고 반품 절차로 넘어갑니다.</p>
   */
  @PutMapping("/{no}/cancel")
  public ResponseEntity<?> cancelOrder(@PathVariable("no") Long no) {
    try {
      orderService.cancelOrder(no);

      Map<String, Object> body = new HashMap<>();
      body.put("no", no);
      body.put("message", "주문이 취소되었습니다.");
      return ResponseEntity.ok(body);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 관리자 주문 상태 변경.
   * <pre>PUT /order/12/status  {"payStatus":1, "deliveryStatus":1}</pre>
   *
   * <p>null인 항목은 변경하지 않습니다(배송상태만 바꾸는 호출이 가장 흔합니다).
   * 재고 원복이 필요한 '취소'는 이 API가 아니라 {@code PUT /order/{no}/cancel} 을 써야 합니다.</p>
   */
  @PutMapping("/{no}/status")
  public ResponseEntity<?> changeStatus(
      @PathVariable("no") Long no,
      @RequestBody OrderDTO dto) {

    try {
      orderService.changeStatus(no, dto.getPayStatus(), dto.getDeliveryStatus());

      Map<String, Object> body = new HashMap<>();
      body.put("no", no);
      body.put("message", "주문 상태가 변경되었습니다.");
      return ResponseEntity.ok(body);

    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }
}
