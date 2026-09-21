package dev.jpa.climbon.product;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 서비스.
 *
 * <p>검색/정렬 로직은 전부 Repository의 JPQL이 담당하고, 이 클래스는
 * <b>입력 정리 → 호출 → 권한 검사</b>만 합니다.
 * "정렬을 어떻게 하느냐"는 쿼리의 문제이지 서비스의 문제가 아니라고 보기 때문입니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

  private final ProductRepository productRepository;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 상품 통합 검색.
   *
   * <p>가격 필터는 {@code COALESCE(salePrice, price)} 기준입니다 —
   * 화면에 보이는 값(할인가)과 필터 기준이 달라지면 사용자는 버그로 인식합니다.
   * (자세한 이유는 {@link ProductRepository} 클래스 주석 참고)</p>
   *
   * <p><b>[실무 팁] priceMin/priceMax를 여기서 뒤집어 주는 이유</b><br>
   * 프론트가 슬라이더를 거꾸로 잡으면 min &gt; max가 들어오고, 그러면 조건이
   * 서로를 배제해 <b>결과가 항상 0건</b>이 됩니다. 사용자는 "검색이 고장났다"고 느끼죠.
   * 서버가 조용히 바로잡아 주는 편이 오류를 돌려주는 것보다 나은 경우입니다.</p>
   *
   * @param sort new(기본) | sell | low | high | rating
   */
  public Page<ProductDTO> getProducts(String word, Integer category, String brand,
      String levelTag, Integer priceMin, Integer priceMax, String sort, Pageable pageable) {

    if (priceMin != null && priceMax != null && priceMin > priceMax) {
      Integer tmp = priceMin;
      priceMin = priceMax;
      priceMax = tmp;
    }

    return productRepository.searchProducts(
        word, category, brand, levelTag, priceMin, priceMax,
        (sort == null ? "new" : sort), pageable);
  }

  /**
   * 상품 상세 — 조회수 +1 후 조회.
   *
   * <p><b>왜 "증가 → 조회" 순서인가?</b><br>
   * {@code increaseVcnt()}는 영속성 컨텍스트를 우회하는 벌크 UPDATE이고
   * {@code clearAutomatically = true}로 컨텍스트를 비웁니다.
   * UPDATE를 먼저 날리고 그다음 조회해야 <b>증가된 값이 화면에 반영</b>됩니다.
   * 순서를 바꾸면 방금 본 사람은 항상 1 적은 조회수를 보게 됩니다.</p>
   */
  @Transactional
  public ProductDTO getProduct(Long no) {
    productRepository.increaseVcnt(no);

    Product product = productRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 상품입니다. no=" + no));

    return ProductDTO.fromEntity(product);
  }

  /** 베스트 상품 — 판매 수량(SELL_CNT) 내림차순 상위 N건 */
  public List<ProductDTO> getBestProducts(int size) {
    List<ProductDTO> list = productRepository.findBestProducts(PageRequest.of(0, size));
    list.forEach(ProductDTO::fillDerived);
    return list;
  }

  /* ======================================================================
   * 관리자 — 등록 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 상품 등록. (관리자 전용)
   *
   * <p>권한 검사를 SecurityConfig의 URL 패턴에만 맡기지 않고 서비스에서도 하는 이유는,
   * 나중에 같은 서비스 메서드를 다른 경로(배치, 관리자 콘솔 등)에서 호출할 때
   * <b>URL 설정과 무관하게 규칙이 따라오게</b> 하기 위함입니다.</p>
   */
  @Transactional
  public Long createProduct(ProductDTO dto) {
    requireAdmin();
    validateProduct(dto);

    return productRepository.save(dto.toEntity()).getNo();
  }

  /** 상품 수정. (관리자 전용) */
  @Transactional
  public void updateProduct(Long no, ProductDTO dto) {
    requireAdmin();
    validateProduct(dto);

    Product product = productRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 상품입니다. no=" + no));

    dto.applyUpdateTo(product);
    // 변경감지(dirty checking)로 바뀐 컬럼만 UPDATE 됩니다.
  }

  /**
   * 상품 삭제 (논리 삭제). — 관리자 전용
   *
   * <p><b>[면접 포인트] 왜 물리삭제를 하지 않는가?</b><br>
   * ORDER_ITEM이 PNO로 이 상품을 참조하고 있어 실제로 지우면 FK 제약에 걸립니다.
   * 설령 FK가 없더라도 <b>과거 주문 내역의 상품 링크가 깨져</b> 고객이 주문 상세를 볼 수 없게 됩니다.
   * 논리삭제해 두면 주문 내역은 그대로 남고 판매 목록에서만 사라집니다.</p>
   */
  @Transactional
  public void deleteProduct(Long no) {
    requireAdmin();

    Product product = productRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 이미 삭제된 상품입니다. no=" + no));

    product.delete(Tool.getDate());
  }

  /**
   * 상품 대표 이미지 업로드/교체. (관리자)
   *
   * <p>상품 등록/수정 화면의 {@code AttachUploader}는 ATTACH 공통 테이블에 파일을 쌓을 뿐
   * PRODUCT.THUMB을 갱신하지 않습니다. 목록 카드·상세 대표 이미지는 THUMB 컬럼 하나만 보므로
   * 이 메서드로 실제 파일을 {@code C:/kd/deploy/climbon/PRODUCT/}에 저장하고 THUMB을 갱신합니다.</p>
   *
   * @return 저장된 파일명 (PRODUCT.THUMB에 들어간 값 그대로)
   */
  @Transactional
  public String updateThumb(Long no, MultipartFile file) {
    requireAdmin();

    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일이 비어 있습니다.");
    }
    String originalName = file.getOriginalFilename();
    if (!Tool.isImage(originalName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일(jpg/png/gif/webp)만 업로드할 수 있습니다.");
    }

    Product product = productRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 상품입니다. no=" + no));

    String oldThumb = product.getThumb();
    String serverDir = Tool.getServerDir("PRODUCT");
    String sname = Tool.getSaveFilename(originalName);

    try {
      file.transferTo(new java.io.File(serverDir + sname));
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장에 실패했습니다: " + e.getMessage());
    }

    product.setThumb(sname); // 변경감지로 UPDATE

    if (oldThumb != null && !oldThumb.isBlank()) {
      java.io.File old = new java.io.File(serverDir + oldThumb);
      if (old.exists() && !old.delete()) {
        log.warn("[PRODUCT] 기존 대표 이미지 삭제 실패: {}", old.getAbsolutePath());
      }
    }

    return sname;
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /** 등록/수정 공통 유효성 검사 */
  private void validateProduct(ProductDTO dto) {
    if (Tool.isEmpty(dto.getPname())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품명은 필수입니다.");
    }
    if (dto.getPrice() == null || dto.getPrice() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정가는 0원 이상이어야 합니다.");
    }
    // 할인가가 정가보다 비싸면 할인율이 음수가 되어 화면이 깨집니다.
    if (dto.getSalePrice() != null && dto.getSalePrice() > dto.getPrice()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "판매가는 정가보다 클 수 없습니다.");
    }
    if (dto.getStock() != null && dto.getStock() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "재고는 0개 이상이어야 합니다.");
    }
  }

  /** 관리자 권한 확인 — 아니면 403 */
  private void requireAdmin() {
    if (SecurityUtil.getMemberNo() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    if (!SecurityUtil.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있는 기능입니다.");
    }
  }
}
