package dev.jpa.climbon.cart;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 장바구니 컨트롤러. — {@code /cart}
 *
 * <p>장바구니는 전부 <b>내 데이터</b>라 모든 엔드포인트가 로그인을 요구합니다.
 * 회원번호는 경로/바디로 받지 않고 서비스가 토큰에서 직접 꺼냅니다 —
 * 파라미터로 받으면 남의 번호를 넣어 조회·수정할 수 있기 때문입니다.</p>
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartCont {

  private final CartService cartService;

  /**
   * 내 장바구니 조회.
   * <pre>GET /cart</pre>
   *
   * <p>응답에는 상품 정보(상품명/가격/썸네일/재고)가 조인되어 함께 내려가고
   * 합계 금액·배송비·결제예정금액도 서버가 계산해 담습니다.</p>
   *
   * @return {@code {items:[...], totalQty, totalPrice, deliveryFee, payAmount}}
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> getMyCart() {
    return ResponseEntity.ok(cartService.getMyCart());
  }

  /**
   * 장바구니 담기.
   * <pre>POST /cart  {"pno":7, "qty":1, "optSize":"240"}</pre>
   *
   * <p>같은 상품+같은 사이즈가 이미 담겨 있으면 새 줄을 만들지 않고 <b>수량만 증가</b>합니다.
   * 그래서 응답의 {@code no}는 새로 생긴 번호일 수도, 기존 줄의 번호일 수도 있습니다.</p>
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> addToCart(@RequestBody CartDTO dto) {
    Long no = cartService.addToCart(dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "장바구니에 담았습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 수량 변경.
   * <pre>PUT /cart/3  {"qty":2}</pre>
   */
  @PutMapping("/{no}")
  public ResponseEntity<Map<String, Object>> changeQty(
      @PathVariable("no") Long no,
      @RequestBody CartDTO dto) {

    cartService.changeQty(no, dto.getQty());

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "수량이 변경되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 장바구니 개별 삭제.
   * <pre>DELETE /cart/3</pre>
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<Map<String, Object>> deleteCart(@PathVariable("no") Long no) {
    cartService.deleteCart(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "삭제되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 장바구니 전체 비우기.
   * <pre>DELETE /cart</pre>
   */
  @DeleteMapping
  public ResponseEntity<Map<String, Object>> clearCart() {
    cartService.clearCart();

    Map<String, Object> body = new HashMap<>();
    body.put("message", "장바구니를 비웠습니다.");
    return ResponseEntity.ok(body);
  }
}
