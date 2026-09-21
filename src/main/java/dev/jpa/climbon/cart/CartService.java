package dev.jpa.climbon.cart;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.product.Product;
import dev.jpa.climbon.product.ProductRepository;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 장바구니 서비스.
 *
 * <p>장바구니는 "아직 확정되지 않은 목록"이라 <b>항상 현재 상품 정보를 보여줘야</b> 합니다.
 * 그래서 담을 때 가격을 복사하지 않고, 조회할 때마다 PRODUCT를 조인해 최신 값을 만듭니다.
 * (반대로 주문서는 그때의 값을 복사해 보관합니다 — OrderService 주석 참고)</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

  private final CartRepository cartRepository;
  private final ProductRepository productRepository;

  /** 무료배송 기준 금액 (원) — 정책값이라 상수로 뽑아 한 곳에서 관리합니다. */
  public static final int FREE_DELIVERY_AMOUNT = 50_000;

  /** 기본 배송비 (원) */
  public static final int DELIVERY_FEE = 3_000;

  /** 1회 주문 가능한 최대 수량 — 이상 주문(재판매 목적 사재기)을 막는 최소한의 방어선 */
  private static final int MAX_QTY = 99;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 내 장바구니 조회. — 상품 정보 조인 + 합계 금액 계산
   *
   * <p><b>왜 합계를 서버가 계산하나?</b><br>
   * 프론트가 계산하면 "할인가를 쓰는가", "주문 불가 줄도 합계에 넣는가",
   * "무료배송 기준은 얼마인가" 같은 규칙이 서버와 미묘하게 어긋날 수 있고,
   * 그 차이는 곧 <b>결제 금액 분쟁</b>이 됩니다. 금액 규칙은 서버에 한 벌만 둡니다.</p>
   *
   * <p>합계에는 <b>주문 가능한 줄만</b> 넣습니다. 품절 상품 금액까지 합치면
   * 결제 화면으로 넘어갈 때 금액이 줄어들어 사용자가 혼란스러워합니다.</p>
   *
   * @return {@code {items:[], totalQty, totalPrice, deliveryFee, payAmount}}
   */
  public Map<String, Object> getMyCart() {
    Long mno = requireLogin();

    List<CartDTO> items = cartRepository.findMyCart(mno);

    int totalQty = 0;
    int totalPrice = 0;
    for (CartDTO item : items) {
      if (Boolean.TRUE.equals(item.getOrderable())) {
        totalQty += item.getQty();
        totalPrice += item.getLineTotal();
      }
    }

    int deliveryFee = calcDeliveryFee(totalPrice);

    // LinkedHashMap을 쓰면 JSON 필드 순서가 아래 순서 그대로 유지되어 응답을 읽기 쉽습니다.
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", items);
    result.put("totalQty", totalQty);
    result.put("totalPrice", totalPrice);
    result.put("deliveryFee", deliveryFee);
    result.put("payAmount", totalPrice + deliveryFee);
    return result;
  }

  /**
   * 배송비 계산. — 일정 금액 이상이면 무료
   * <p>주문 금액이 0원(장바구니가 비었거나 전부 품절)이면 배송비도 0원이어야
   * "0원 상품 + 3,000원 배송비" 같은 이상한 화면이 나오지 않습니다.</p>
   */
  public int calcDeliveryFee(int totalPrice) {
    if (totalPrice <= 0) return 0;
    return totalPrice >= FREE_DELIVERY_AMOUNT ? 0 : DELIVERY_FEE;
  }

  /* ======================================================================
   * 담기 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 장바구니 담기.
   *
   * <p><b>[면접 포인트] 같은 상품+같은 사이즈면 왜 행을 늘리지 않고 수량만 올리나?</b><br>
   * 행이 늘어나면 장바구니에 "스카르파 240 × 1"이 세 줄로 보입니다.
   * 사용자는 이를 명백한 버그로 인식하고, 수량 변경/삭제 UI도 어느 줄을 눌러야 할지 모호해집니다.
   * 주문 생성 때도 같은 상품에 대해 재고 차감이 여러 번 일어나 로직이 복잡해집니다.
   * <b>(회원, 상품, 사이즈)를 논리적 유일키</b>로 보고 수량만 증가시키는 것이 정답입니다.</p>
   *
   * <p>사이즈는 빈 문자열과 null이 섞이지 않도록 <b>null로 정규화</b>합니다.
   * Oracle은 빈 문자열을 NULL로 저장하므로, 자바 쪽에서 ""로 들고 있으면
   * "이미 담겼는지" 판정이 DB 값과 어긋나 중복 행이 생깁니다.</p>
   *
   * @return 장바구니 행 번호 (기존 행이면 그 번호)
   */
  @Transactional
  public Long addToCart(CartDTO dto) {
    Long mno = requireLogin();

    if (dto.getPno() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품번호(pno)는 필수입니다.");
    }

    Product product = productRepository.findByNoAndIsdel(dto.getPno(), "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 판매 종료된 상품입니다. pno=" + dto.getPno()));

    if (!product.isOrderable()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 구매할 수 없는 상품입니다.");
    }
    if (product.getStock() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "품절된 상품입니다.");
    }

    int qty = (dto.getQty() == null || dto.getQty() < 1) ? 1 : Math.min(dto.getQty(), MAX_QTY);
    String optSize = Tool.isEmpty(dto.getOptSize()) ? null : dto.getOptSize().trim();

    Optional<Cart> existing = cartRepository.findSameItem(mno, product.getNo(), optSize);
    if (existing.isPresent()) {
      // 이미 담긴 줄 -> 수량만 증가 (재고를 넘지 않도록 상한을 함께 넘깁니다)
      Cart cart = existing.get();
      cart.addQty(qty, Math.min(product.getStock(), MAX_QTY));
      return cart.getNo();
    }

    Cart entity = dto.toEntity();
    entity.setMno(mno); // 소유자는 요청 바디가 아니라 토큰에서 가져옵니다.
    entity.setPno(product.getNo());
    entity.setOptSize(optSize);
    entity.changeQty(qty, Math.min(product.getStock(), MAX_QTY));

    return cartRepository.save(entity).getNo();
  }

  /**
   * 수량 변경.
   *
   * <p>남의 장바구니 행 번호를 찍어 보낼 수 있으므로 <b>반드시 소유자를 확인</b>합니다.
   * 경로 변수(no)만 믿으면 다른 사람의 장바구니를 마음대로 바꿀 수 있습니다.</p>
   */
  @Transactional
  public void changeQty(Long no, Integer qty) {
    Long mno = requireLogin();

    if (qty == null || qty < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
    }

    Cart cart = cartRepository.findById(no)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "장바구니에 없는 항목입니다. no=" + no));

    if (!cart.isOwner(mno)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 장바구니만 수정할 수 있습니다.");
    }

    int stock = productRepository.findByNoAndIsdel(cart.getPno(), "N")
        .map(Product::getStock)
        .orElse(0);

    cart.changeQty(Math.min(qty, MAX_QTY), Math.min(stock, MAX_QTY));
  }

  /** 장바구니 개별 삭제. (본인 것만) */
  @Transactional
  public void deleteCart(Long no) {
    Long mno = requireLogin();

    Cart cart = cartRepository.findById(no)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "장바구니에 없는 항목입니다. no=" + no));

    if (!cart.isOwner(mno)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 장바구니만 삭제할 수 있습니다.");
    }

    cartRepository.delete(cart);
  }

  /**
   * 장바구니 전체 비우기. (본인 것만)
   * <p>장바구니는 "임시 목록"이라 논리삭제할 이유가 없어 물리삭제합니다.
   * 보존해도 분석 가치가 크지 않고, 남겨 두면 중복 판정만 복잡해집니다.</p>
   */
  @Transactional
  public void clearCart() {
    Long mno = requireLogin();
    cartRepository.deleteByMno(mno);
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /** 로그인 회원번호를 얻고, 비로그인이면 401을 던집니다. */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return mno;
  }
}
