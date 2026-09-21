package dev.jpa.climbon.product;

import java.util.ArrayList;
import java.util.Arrays;
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
 * 상품 DTO. (목록 / 상세 / 관리자 등록·수정 공용)
 *
 * <p><b>왜 요청 DTO와 응답 DTO를 나누지 않았나?</b><br>
 * 관리자 등록 폼과 목록 카드가 다루는 필드가 거의 같습니다.
 * {@code ProductCreateRequest / ProductResponse}로 쪼개면 20여 개 필드를 두 번 적게 되고
 * 컬럼이 추가될 때마다 두 곳을 고쳐야 해서 누락이 생깁니다.
 * 대신 응답 전용 파생값({@code realPrice}, {@code discountRate}, {@code sizeOptions})은
 * null로 두고 {@code @JsonInclude(NON_NULL)}로 필요할 때만 실어 보냅니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDTO {

  /** 상품번호 (등록 시 null) */
  private Long no;

  /** 카테고리 (0: 암벽화 ~ 6: 기타) */
  private Integer category;

  /** 브랜드 */
  private String brand;

  /** 상품명 */
  private String pname;

  /** 한줄 설명 */
  private String summary;

  /** 상세 설명 — 목록에서는 null (CLOB이라 상세에서만 채웁니다) */
  private String content;

  /** 정가 */
  private Integer price;

  /** 판매가 (할인가) */
  private Integer salePrice;

  /** 재고 수량 */
  private Integer stock;

  /** 사이즈 옵션 원본 문자열 ("230,235,240") */
  private String sizeInfo;

  /** 성별 (0: 공용, 1: 남성, 2: 여성) */
  private Integer gender;

  /** 추천 레벨 태그 */
  private String levelTag;

  /** 대표 이미지 파일명 */
  private String thumb;

  /** 평균 평점 */
  private Double ratingAvg;

  /** 리뷰 수 */
  private Integer reviewCnt;

  /** 조회수 */
  private Integer vcnt;

  /** 판매 수량 */
  private Integer sellCnt;

  /** 상태 (0: 판매중지, 1: 판매중, 2: 품절) */
  private Integer status;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /* ---------------- 응답 전용 파생값 (DB 컬럼 아님) ---------------- */

  /**
   * 실제 판매가 (= salePrice ?? price).
   * <p>프론트가 매번 {@code salePrice ?? price} 를 계산하면 화면마다 규칙이 어긋날 수 있어
   * <b>서버가 정답을 내려줍니다.</b> "판매가는 어떻게 정해지는가"는 비즈니스 규칙이고,
   * 비즈니스 규칙은 서버에 한 벌만 있어야 합니다.</p>
   */
  private Integer realPrice;

  /** 할인율 (%) — 할인 중일 때만 채워집니다. */
  private Integer discountRate;

  /**
   * 사이즈 옵션 배열.
   * <p>DB에는 "230,235,240" 한 문자열로 저장되어 있습니다(정규화하면 테이블이 하나 더 늘고
   * 조회 때마다 조인이 붙는데, 옵션이 몇 개 없는 도메인이라 얻는 것이 적습니다).
   * 대신 <b>프론트가 파싱하지 않도록 서버가 배열로 만들어</b> 내려줍니다.</p>
   */
  private List<String> sizeOptions;

  /**
   * JPQL 생성자 표현식 전용 — <b>목록</b>용. (CLOB인 content 제외)
   *
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응하므로 함부로 바꾸면 안 됩니다.
   * 목록에서 상세 설명(CLOB)까지 읽으면 20건 조회에 LOB 로케이터 20개를 끌고 오게 됩니다.</p>
   */
  public ProductDTO(Long no, Integer category, String brand, String pname, String summary,
      Integer price, Integer salePrice, Integer stock, String sizeInfo, Integer gender,
      String levelTag, String thumb, Double ratingAvg, Integer reviewCnt,
      Integer vcnt, Integer sellCnt, Integer status, String cdate, String udate) {
    this.no = no;
    this.category = category;
    this.brand = brand;
    this.pname = pname;
    this.summary = summary;
    this.price = price;
    this.salePrice = salePrice;
    this.stock = stock;
    this.sizeInfo = sizeInfo;
    this.gender = gender;
    this.levelTag = levelTag;
    this.thumb = thumb;
    this.ratingAvg = ratingAvg;
    this.reviewCnt = reviewCnt;
    this.vcnt = vcnt;
    this.sellCnt = sellCnt;
    this.status = status;
    this.cdate = cdate;
    this.udate = udate;
    fillDerived();
  }

  /**
   * DTO -> Entity. (관리자 상품 등록)
   *
   * <p>통계 컬럼(vcnt/ratingAvg/reviewCnt/sellCnt)은 <b>클라이언트 값을 신뢰하지 않고</b>
   * 항상 0으로 시작합니다. 프론트가 ratingAvg=5.0, sellCnt=9999를 보내도 조작되지 않습니다.</p>
   */
  public Product toEntity() {
    return Product.builder()
        .no(this.no)
        .category(this.category == null ? Product.CATEGORY_SHOES : this.category)
        .brand(this.brand)
        .pname(this.pname)
        .summary(this.summary)
        // 상세 설명은 관리자가 쓰는 HTML이지만, 외부 입력을 그대로 믿지 않는 편이 안전합니다.
        .content(Tool.escapeHtml(this.content))
        .price(this.price)
        .salePrice(this.salePrice)
        .stock(this.stock == null ? 0 : this.stock)
        .sizeInfo(this.sizeInfo)
        .gender(this.gender == null ? 0 : this.gender)
        .levelTag(this.levelTag)
        .thumb(this.thumb)
        .ratingAvg(0.0)
        .reviewCnt(0)
        .vcnt(0)
        .sellCnt(0)
        .status(this.status == null ? Product.STATUS_ON_SALE : this.status)
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /**
   * 수정 요청 값을 기존 엔티티에 반영합니다.
   *
   * <p>새 엔티티를 만들어 save()로 덮어쓰지 않는 이유: 그렇게 하면 요청에 없던
   * 통계 컬럼(조회수·판매수량·평점)이 전부 초기화됩니다.
   * 영속 상태 엔티티의 필드만 바꾸면 JPA 변경감지가 <b>바뀐 컬럼만</b> UPDATE 합니다.</p>
   */
  public void applyUpdateTo(Product product) {
    if (this.category != null) product.setCategory(this.category);
    product.setBrand(this.brand);
    product.setPname(this.pname);
    product.setSummary(this.summary);
    product.setContent(Tool.escapeHtml(this.content));
    product.setPrice(this.price);
    product.setSalePrice(this.salePrice);
    if (this.stock != null) product.setStock(this.stock);
    product.setSizeInfo(this.sizeInfo);
    if (this.gender != null) product.setGender(this.gender);
    product.setLevelTag(this.levelTag);
    if (this.thumb != null) product.setThumb(this.thumb);
    if (this.status != null) product.setStatus(this.status);
    product.setUdate(Tool.getDate());
  }

  /** Entity -> DTO (상세 조회용 — content 포함) */
  public static ProductDTO fromEntity(Product entity) {
    if (entity == null) return null;

    ProductDTO dto = ProductDTO.builder()
        .no(entity.getNo())
        .category(entity.getCategory())
        .brand(entity.getBrand())
        .pname(entity.getPname())
        .summary(entity.getSummary())
        .content(entity.getContent())
        .price(entity.getPrice())
        .salePrice(entity.getSalePrice())
        .stock(entity.getStock())
        .sizeInfo(entity.getSizeInfo())
        .gender(entity.getGender())
        .levelTag(entity.getLevelTag())
        .thumb(entity.getThumb())
        .ratingAvg(entity.getRatingAvg())
        .reviewCnt(entity.getReviewCnt())
        .vcnt(entity.getVcnt())
        .sellCnt(entity.getSellCnt())
        .status(entity.getStatus())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .build();

    dto.fillDerived();
    return dto;
  }

  /**
   * 파생값(realPrice / discountRate / sizeOptions)을 계산해 채웁니다.
   *
   * <p>DTO가 스스로 계산하게 두면 어느 경로로 만들어지든(생성자 표현식/빌더)
   * 같은 규칙이 적용되어 화면마다 값이 달라지는 사고를 막을 수 있습니다.</p>
   */
  public void fillDerived() {
    int base = (this.price == null) ? 0 : this.price;
    this.realPrice = (this.salePrice != null) ? this.salePrice : base;

    // 할인 중일 때만 할인율을 계산합니다. (정가가 0이면 나눗셈이 불가능하므로 방어)
    if (this.salePrice != null && base > 0 && this.salePrice < base) {
      this.discountRate = (int) Math.round((base - this.salePrice) * 100.0 / base);
    }

    if (!Tool.isEmpty(this.sizeInfo)) {
      List<String> options = new ArrayList<>();
      for (String size : Arrays.asList(this.sizeInfo.split(","))) {
        String trimmed = size.trim();
        if (!trimmed.isEmpty()) options.add(trimmed);
      }
      this.sizeOptions = options.isEmpty() ? null : options;
    }
  }
}
