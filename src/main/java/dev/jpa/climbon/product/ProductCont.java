package dev.jpa.climbon.product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.jpa.climbon.product.review.ProductReviewDTO;
import dev.jpa.climbon.product.review.ProductReviewService;
import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 상품 컨트롤러. — {@code /product}
 *
 * <p>상품 후기 API도 명세상 {@code /product} 아래에 있어 이 컨트롤러에 함께 둡니다.
 * (후기는 상품 없이 존재할 수 없는 <b>종속 리소스</b>라 경로를 분리할 이유가 없습니다.
 *  로직은 {@code ProductReviewService}에 따로 있으므로 이 클래스가 비대해지지는 않습니다.)</p>
 *
 * <p><b>경로 충돌에 대해</b>: {@code /product/best}와 {@code /product/{no}}는 조각 수가 같지만
 * Spring은 <b>리터럴 경로를 경로 변수보다 우선</b>해 매칭하므로 {@code /product/best}가
 * no=best로 해석되는 일은 없습니다.
 * {@code /product/review/{no}}(3조각)와 {@code /product/{no}}(2조각)도 서로 다릅니다.</p>
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductCont {

  private final ProductService productService;
  private final ProductReviewService productReviewService;

  /**
   * 상품 목록.
   * <pre>GET /product/list?word=암벽화&amp;category=0&amp;brand=스카르파&amp;levelTag=입문
   *        &amp;priceMin=50000&amp;priceMax=200000&amp;sort=low&amp;page=0&amp;size=12</pre>
   *
   * @param sort new(신상품) | sell(판매순) | low(낮은가격) | high(높은가격) | rating(평점)
   */
  @GetMapping("/list")
  public ResponseEntity<PageResponse<ProductDTO>> getProducts(
      @RequestParam(name = "word", required = false) String word,
      @RequestParam(name = "category", required = false) Integer category,
      @RequestParam(name = "brand", required = false) String brand,
      @RequestParam(name = "levelTag", required = false) String levelTag,
      @RequestParam(name = "priceMin", required = false) Integer priceMin,
      @RequestParam(name = "priceMax", required = false) Integer priceMax,
      @RequestParam(name = "sort", defaultValue = "new") String sort,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "12") int size) {

    // 정렬은 쿼리 안의 ORDER BY(CASE)가 전담하므로 Pageable에는 Sort를 싣지 않습니다.
    Page<ProductDTO> result = productService.getProducts(
        word, category, brand, levelTag, priceMin, priceMax, sort, PageRequest.of(page, size));

    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 베스트 상품.
   * <pre>GET /product/best?size=8</pre>
   */
  @GetMapping("/best")
  public ResponseEntity<List<ProductDTO>> getBestProducts(
      @RequestParam(name = "size", defaultValue = "8") int size) {
    return ResponseEntity.ok(productService.getBestProducts(size));
  }

  /**
   * 상품 상세. (조회수 +1)
   * <pre>GET /product/7</pre>
   */
  @GetMapping("/{no}")
  public ResponseEntity<ProductDTO> getProduct(@PathVariable("no") Long no) {
    return ResponseEntity.ok(productService.getProduct(no));
  }

  /* ======================================================================
   * 관리자 전용
   * ====================================================================== */

  /**
   * 상품 등록. (관리자)
   * <pre>POST /product</pre>
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> createProduct(@RequestBody ProductDTO dto) {
    Long no = productService.createProduct(dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "상품이 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 상품 수정. (관리자)
   * <pre>PUT /product/7</pre>
   */
  @PutMapping("/{no}")
  public ResponseEntity<Map<String, Object>> updateProduct(
      @PathVariable("no") Long no,
      @RequestBody ProductDTO dto) {

    productService.updateProduct(no, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "상품이 수정되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 상품 삭제(논리 삭제). (관리자)
   * <pre>DELETE /product/7</pre>
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<Map<String, Object>> deleteProduct(@PathVariable("no") Long no) {
    productService.deleteProduct(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "상품이 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 상품 대표 이미지 업로드/교체. (관리자)
   * <pre>POST /product/7/thumb  (multipart/form-data, key: file)</pre>
   *
   * <p>저장 즉시 PRODUCT.THUMB이 갱신되어 목록 카드·상세 대표 이미지가 바로 바뀝니다.</p>
   */
  @PostMapping(value = "/{no}/thumb", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadThumb(
      @PathVariable("no") Long no,
      @RequestParam("file") MultipartFile file) {

    String thumb = productService.updateThumb(no, file);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("thumb", thumb);
    body.put("message", "대표 이미지가 저장되었습니다.");
    return ResponseEntity.ok(body);
  }

  /* ======================================================================
   * 상품 후기
   * ====================================================================== */

  /**
   * 상품 후기 목록.
   * <pre>GET /product/7/review?page=0&amp;size=10</pre>
   */
  @GetMapping("/{pno}/review")
  public ResponseEntity<PageResponse<ProductReviewDTO>> getReviews(
      @PathVariable("pno") Long pno,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    Page<ProductReviewDTO> result =
        productReviewService.getReviewsByPno(pno, PageRequest.of(page, size));
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 상품 후기 등록. (1인 1후기)
   * <pre>POST /product/7/review  {"rating":4.5, "content":"...", "optSize":"240"}</pre>
   */
  @PostMapping("/{pno}/review")
  public ResponseEntity<Map<String, Object>> createReview(
      @PathVariable("pno") Long pno,
      @RequestBody ProductReviewDTO dto) {

    Long no = productReviewService.createReview(pno, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "후기가 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 상품 후기 삭제. (작성자 본인 또는 관리자)
   * <pre>DELETE /product/review/15</pre>
   */
  @DeleteMapping("/review/{no}")
  public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable("no") Long no) {
    productReviewService.deleteReview(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "후기가 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }
}
