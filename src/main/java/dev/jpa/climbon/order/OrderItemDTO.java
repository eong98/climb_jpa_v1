package dev.jpa.climbon.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 주문 상세(주문 상품) DTO.
 *
 * <p>주문 <b>생성 요청</b>과 <b>조회 응답</b>에 모두 쓰입니다.
 * <ul>
 *   <li>요청일 때 채워지는 값: {@code pno}, {@code qty}, {@code optSize}
 *       — 상품명/가격은 <b>서버가 DB에서 직접 읽습니다.</b>
 *         클라이언트가 보낸 가격을 그대로 쓰면 1원짜리 주문이 들어옵니다.</li>
 *   <li>응답일 때 채워지는 값: 주문 시점 스냅샷 전체 ({@code pname}, {@code price}, {@code thumb} ...)</li>
 * </ul>
 * </p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItemDTO {

  /** 주문상세번호 (요청 시 null) */
  private Long no;

  /** 주문번호 */
  private Long ono;

  /** 상품번호 — 요청에서 <b>반드시</b> 채워야 하는 값 */
  private Long pno;

  /** 주문 시점 상품명 (응답 전용 — 요청 값은 무시됩니다) */
  private String pname;

  /** 주문 시점 단가 (응답 전용 — 요청 값은 무시됩니다) */
  private Integer price;

  /** 수량 */
  private Integer qty;

  /** 선택 사이즈 */
  private String optSize;

  /** 주문 시점 썸네일 (응답 전용) */
  private String thumb;

  /** 이 줄의 금액 (단가 × 수량) — 서버가 계산해 내려줍니다. */
  private Integer lineTotal;

  /**
   * JPQL 생성자 표현식 전용 생성자.
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응합니다.</p>
   */
  public OrderItemDTO(Long no, Long ono, Long pno, String pname,
      Integer price, Integer qty, String optSize, String thumb) {
    this.no = no;
    this.ono = ono;
    this.pno = pno;
    this.pname = pname;
    this.price = price;
    this.qty = qty;
    this.optSize = optSize;
    this.thumb = thumb;
    this.lineTotal = (price == null ? 0 : price) * (qty == null ? 0 : qty);
  }

  /**
   * DTO -> Entity.
   *
   * <p><b>주의</b>: 이 메서드는 <b>서버가 상품 정보를 채워 넣은 뒤</b>에만 호출해야 합니다.
   * 요청 바디의 pname/price를 그대로 엔티티에 담으면
   * "10만원짜리를 100원으로" 주문하는 가격 조작이 가능합니다.
   * 그래서 OrderService는 이 메서드를 쓰지 않고 <b>Product 엔티티에서 직접</b> 스냅샷을 만듭니다.</p>
   */
  public OrderItem toEntity() {
    return OrderItem.builder()
        .no(this.no)
        .ono(this.ono)
        .pno(this.pno)
        .pname(this.pname)
        .price(this.price)
        .qty(this.qty)
        .optSize(this.optSize)
        .thumb(this.thumb)
        .build();
  }

  /** Entity -> DTO */
  public static OrderItemDTO fromEntity(OrderItem entity) {
    if (entity == null) return null;
    return OrderItemDTO.builder()
        .no(entity.getNo())
        .ono(entity.getOno())
        .pno(entity.getPno())
        .pname(entity.getPname())
        .price(entity.getPrice())
        .qty(entity.getQty())
        .optSize(entity.getOptSize())
        .thumb(entity.getThumb())
        .lineTotal(entity.getLineTotal())
        .build();
  }
}
