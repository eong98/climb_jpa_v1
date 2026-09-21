package dev.jpa.climbon.cart;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 장바구니 DTO.
 *
 * <p><b>[면접 포인트] 장바구니 응답에 상품 정보를 왜 조인해서 함께 내리나?</b><br>
 * 장바구니 행에는 PNO만 있습니다. 프론트가 화면을 그리려면 상품명·가격·썸네일·재고가 필요한데
 * 담은 상품이 5개면 {@code GET /product/{no}} 를 5번 더 호출해야 합니다(프론트에서의 N+1).
 * 네트워크 왕복 5번은 모바일에서 체감될 만큼 느리고, 그중 하나만 실패해도 화면이 깨집니다.
 * 그래서 서버가 {@code JOIN Product} 한 번으로 <b>화면에 필요한 모든 값</b>을 만들어 내려줍니다.</p>
 *
 * <p>합계 금액도 서버가 계산합니다. 프론트가 계산하면 할인가 적용 규칙·배송비 기준이
 * 서버와 미묘하게 달라질 수 있고, 그 차이는 곧 결제 금액 분쟁이 됩니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartDTO {

  /** 장바구니번호 (담기 요청 시 null) */
  private Long no;

  /** 회원번호 — 서버가 토큰 값으로 채웁니다. */
  private Long mno;

  /** 상품번호 */
  private Long pno;

  /** 수량 */
  private Integer qty;

  /** 선택 사이즈 */
  private String optSize;

  /** 담은 일시 */
  private String cdate;

  /* ---------------- PRODUCT 조인 값 (DB 컬럼 아님) ---------------- */

  /** 상품명 */
  private String pname;

  /** 브랜드 */
  private String brand;

  /** 정가 */
  private Integer price;

  /** 할인가 */
  private Integer salePrice;

  /** 실제 판매가 (= salePrice ?? price) — 서버가 계산해 내려주는 값 */
  private Integer realPrice;

  /** 이 줄의 금액 (= realPrice × qty) */
  private Integer lineTotal;

  /** 대표 이미지 파일명 */
  private String thumb;

  /** 현재 재고 — 수량 입력 상한과 "품절" 표시에 씁니다. */
  private Integer stock;

  /** 상품 상태 (0: 판매중지, 1: 판매중, 2: 품절) */
  private Integer status;

  /**
   * 주문 가능 여부.
   * <p>재고 부족/판매중지 상품이 담겨 있으면 프론트가 그 줄을 비활성화하고
   * "주문하기" 버튼을 막아야 합니다. 그 판단 기준을 서버가 계산해 내려줍니다.</p>
   */
  private Boolean orderable;

  /**
   * JPQL 생성자 표현식 전용 생성자. (장바구니 + 상품 정보 조인)
   *
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응합니다.
   * 조인해 온 값으로 realPrice/lineTotal/orderable 같은 파생값을 <b>생성자에서 바로</b> 계산해
   * 서비스가 따로 순회하지 않아도 되게 했습니다.</p>
   */
  public CartDTO(Long no, Long mno, Long pno, Integer qty, String optSize, String cdate,
      String pname, String brand, Integer price, Integer salePrice,
      String thumb, Integer stock, Integer status, String isdel) {
    this.no = no;
    this.mno = mno;
    this.pno = pno;
    this.qty = qty;
    this.optSize = optSize;
    this.cdate = cdate;
    this.pname = pname;
    this.brand = brand;
    this.price = price;
    this.salePrice = salePrice;
    this.thumb = thumb;
    this.stock = stock;
    this.status = status;

    int base = (price == null) ? 0 : price;
    this.realPrice = (salePrice != null) ? salePrice : base;
    this.lineTotal = this.realPrice * (qty == null ? 0 : qty);

    // 판매중(1) + 삭제 안 됨 + 재고가 담은 수량 이상일 때만 주문 가능
    this.orderable = "N".equals(isdel)
        && status != null && status == 1
        && stock != null && qty != null && stock >= qty;
  }

  /**
   * DTO -> Entity. (장바구니 담기)
   * <p>mno는 Service가 로그인 정보로 덮어씁니다.
   * 요청 바디의 mno를 믿으면 남의 장바구니에 물건을 넣을 수 있습니다.</p>
   */
  public Cart toEntity() {
    return Cart.builder()
        .no(this.no)
        .mno(this.mno)
        .pno(this.pno)
        .qty(this.qty == null || this.qty < 1 ? 1 : this.qty)
        // 빈 문자열은 Oracle에서 NULL로 저장되므로 자바 쪽에서도 null로 통일해 둡니다.
        // (이 정규화를 빼면 ""와 null이 섞여 "같은 상품+사이즈" 판정이 어긋납니다.)
        .optSize(Tool.isEmpty(this.optSize) ? null : this.optSize.trim())
        .cdate(Tool.getDate())
        .build();
  }

  /** Entity -> DTO (상품 조인 없이 단건 변환) */
  public static CartDTO fromEntity(Cart entity) {
    if (entity == null) return null;
    return CartDTO.builder()
        .no(entity.getNo())
        .mno(entity.getMno())
        .pno(entity.getPno())
        .qty(entity.getQty())
        .optSize(entity.getOptSize())
        .cdate(entity.getCdate())
        .build();
  }
}
