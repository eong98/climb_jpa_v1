package dev.jpa.climbon.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품 Repository.
 *
 * <p><b>[면접 포인트] 가격 조건과 가격 정렬에 왜 COALESCE(SALE_PRICE, PRICE)를 쓰나?</b><br>
 * 상품의 <b>실제 판매가</b>는 "할인가가 있으면 할인가, 없으면 정가"입니다.
 * 이 규칙을 SQL에 반영하지 않고 {@code p.price BETWEEN :min AND :max} 로 필터링하면
 * <b>10만원짜리를 5만원으로 할인한 상품이 "5만원 이하" 필터에서 빠집니다.</b>
 * 사용자는 화면에 보이는 5만원을 기준으로 필터를 걸었는데 결과가 다르니 버그로 인식합니다.
 * 정렬(낮은 가격순)도 마찬가지로 정가로 줄을 세우면 할인 상품의 순서가 뒤엉킵니다.</p>
 *
 * <p>Oracle의 {@code NVL}이 아니라 JPQL 표준 함수 {@code COALESCE}를 쓴 이유는
 * DB를 MySQL 등으로 바꿔도 쿼리를 고치지 않아도 되기 때문입니다.
 * (Hibernate가 각 DB의 함수로 알아서 변환합니다.)</p>
 *
 * <p><b>[실무 팁] 이 조건의 대가</b><br>
 * 컬럼에 함수가 씌워지면 일반 인덱스를 타지 못합니다.
 * 가격 필터가 성능 병목이 되면 (1) 함수 기반 인덱스
 * {@code CREATE INDEX IDX_PRODUCT_REAL_PRICE ON PRODUCT (NVL(SALE_PRICE, PRICE))} 를 만들거나
 * (2) REAL_PRICE 컬럼을 따로 두고 저장 시점에 채우는 방법이 있습니다.
 * 상품 테이블은 보통 수천~수만 건이라 지금 단계에서는 문제되지 않습니다.</p>
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

  /** 살아 있는 상품 단건 조회 */
  Optional<Product> findByNoAndIsdel(Long no, String isdel);

  /**
   * 상품 통합 검색 (페이징).
   *
   * <p>조건 설명
   * <ul>
   *   <li><b>word</b> : 상품명(PNAME) 또는 브랜드(BRAND) 부분일치</li>
   *   <li><b>category / brand / levelTag</b> : null이면 무시하는 일치 조건</li>
   *   <li><b>priceMin / priceMax</b> : {@code COALESCE(salePrice, price)} 기준 (위 클래스 주석 참고)</li>
   *   <li><b>isdel='N' AND status &lt;&gt; 0</b> : 삭제/판매중지 상품은 항상 제외
   *       (품절 2는 포함합니다 — "품절" 배지를 보여주는 편이 사용자에게 유용하기 때문)</li>
   * </ul>
   * </p>
   *
   * <p><b>정렬을 ORDER BY 안의 CASE로 처리한 이유</b><br>
   * 가격순 정렬은 {@code COALESCE(p.salePrice, p.price)} 라는 <b>식(expression)</b>이라
   * {@code Sort.by("price")} 같은 속성 기반 정렬로는 표현할 수 없습니다.
   * {@code JpaSort.unsafe("COALESCE(...)")} 를 쓸 수도 있지만 그러면 쿼리 별칭에 의존하는
   * 문자열이 Service 코드로 새어 나갑니다. 정렬 규칙은 쿼리가 알고 있는 편이 응집도가 높습니다.</p>
   *
   * <p>{@code CASE WHEN :sort = 'sell' THEN p.sellCnt ELSE 0 END DESC} 는
   * sort가 'sell'이 아니면 모든 행이 0이 되어 정렬에 영향을 주지 않는 관용구입니다.
   * 숫자 타입이 다른 컬럼(int sellCnt / Double ratingAvg)을 한 CASE에 섞으면
   * Hibernate가 결과 타입을 결정하지 못하므로 <b>정렬 기준마다 CASE를 하나씩</b> 두었습니다.</p>
   *
   * @param sort new(기본) | sell | low | high | rating
   */
  @Query(value = """
      SELECT new dev.jpa.climbon.product.ProductDTO(
             p.no, p.category, p.brand, p.pname, p.summary,
             p.price, p.salePrice, p.stock, p.sizeInfo, p.gender,
             p.levelTag, p.thumb, p.ratingAvg, p.reviewCnt,
             p.vcnt, p.sellCnt, p.status, p.cdate, p.udate)
      FROM Product p
      WHERE p.isdel = 'N'
        AND p.status <> 0
        AND (:word IS NULL OR :word = ''
             OR p.pname LIKE CONCAT('%', :word, '%')
             OR p.brand LIKE CONCAT('%', :word, '%'))
        AND (:category IS NULL OR p.category = :category)
        AND (:brand IS NULL OR :brand = '' OR p.brand = :brand)
        AND (:levelTag IS NULL OR :levelTag = '' OR p.levelTag = :levelTag)
        AND (:priceMin IS NULL OR COALESCE(p.salePrice, p.price) >= :priceMin)
        AND (:priceMax IS NULL OR COALESCE(p.salePrice, p.price) <= :priceMax)
      ORDER BY CASE WHEN :sort = 'sell'   THEN p.sellCnt   ELSE 0   END DESC,
               CASE WHEN :sort = 'rating' THEN p.ratingAvg ELSE 0.0 END DESC,
               CASE WHEN :sort = 'low'    THEN COALESCE(p.salePrice, p.price) ELSE 0 END ASC,
               CASE WHEN :sort = 'high'   THEN COALESCE(p.salePrice, p.price) ELSE 0 END DESC,
               p.no DESC
      """,
      countQuery = """
      SELECT COUNT(p.no)
      FROM Product p
      WHERE p.isdel = 'N'
        AND p.status <> 0
        AND (:word IS NULL OR :word = ''
             OR p.pname LIKE CONCAT('%', :word, '%')
             OR p.brand LIKE CONCAT('%', :word, '%'))
        AND (:category IS NULL OR p.category = :category)
        AND (:brand IS NULL OR :brand = '' OR p.brand = :brand)
        AND (:levelTag IS NULL OR :levelTag = '' OR p.levelTag = :levelTag)
        AND (:priceMin IS NULL OR COALESCE(p.salePrice, p.price) >= :priceMin)
        AND (:priceMax IS NULL OR COALESCE(p.salePrice, p.price) <= :priceMax)
      """)
  Page<ProductDTO> searchProducts(
      @Param("word") String word,
      @Param("category") Integer category,
      @Param("brand") String brand,
      @Param("levelTag") String levelTag,
      @Param("priceMin") Integer priceMin,
      @Param("priceMax") Integer priceMax,
      @Param("sort") String sort,
      Pageable pageable);

  /**
   * 베스트 상품 — 판매 수량(SELL_CNT) 내림차순.
   *
   * <p>판매중(status=1)인 상품만 뽑습니다. 품절 상품이 "베스트"에 올라가면
   * 클릭해도 살 수 없어 이탈로 이어지기 때문입니다.
   * 동점일 때는 평점 → 최신 등록 순으로 갈라 순서가 매번 바뀌지 않게 고정합니다.
   * (ORDER BY가 완전히 결정적이지 않으면 페이지를 넘길 때 같은 상품이 또 보일 수 있습니다.)</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.product.ProductDTO(
             p.no, p.category, p.brand, p.pname, p.summary,
             p.price, p.salePrice, p.stock, p.sizeInfo, p.gender,
             p.levelTag, p.thumb, p.ratingAvg, p.reviewCnt,
             p.vcnt, p.sellCnt, p.status, p.cdate, p.udate)
      FROM Product p
      WHERE p.isdel = 'N'
        AND p.status = 1
      ORDER BY p.sellCnt DESC, p.ratingAvg DESC, p.no DESC
      """)
  List<ProductDTO> findBestProducts(Pageable pageable);

  /**
   * AI 추천 후보 목록 — 레벨 태그/카테고리로 좁힌 판매중 상품.
   *
   * <p>LLM에게 "전체 상품 중에 골라 줘"라고 하려면 상품 수천 건을 프롬프트에 넣어야 하는데
   * 토큰 비용이 폭발하고 정확도도 떨어집니다. 그래서 <b>DB가 후보를 먼저 좁히고</b>
   * (레벨/카테고리로 필터 + 인기순 상위 N건) LLM은 그 안에서 고르고 이유를 설명하게 합니다.
   * 이것이 "검색은 DB, 판단은 LLM"이라는 역할 분담입니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.product.ProductDTO(
             p.no, p.category, p.brand, p.pname, p.summary,
             p.price, p.salePrice, p.stock, p.sizeInfo, p.gender,
             p.levelTag, p.thumb, p.ratingAvg, p.reviewCnt,
             p.vcnt, p.sellCnt, p.status, p.cdate, p.udate)
      FROM Product p
      WHERE p.isdel = 'N'
        AND p.status = 1
        AND (:levelTag IS NULL OR :levelTag = '' OR p.levelTag = :levelTag)
        AND (:category IS NULL OR p.category = :category)
      ORDER BY p.sellCnt DESC, p.ratingAvg DESC, p.no DESC
      """)
  List<ProductDTO> findRecommendCandidates(
      @Param("levelTag") String levelTag,
      @Param("category") Integer category,
      Pageable pageable);

  /**
   * 조회수 +1 (벌크 UPDATE).
   *
   * <p>엔티티를 읽어 {@code vcnt + 1}로 올리면 동시 요청 시 갱신 손실이 발생합니다.
   * {@code SET vcnt = vcnt + 1}은 DB가 현재 값 기준으로 증가시켜 정확하고,
   * SELECT 없이 UPDATE 한 방이라 더 빠릅니다.
   * {@code clearAutomatically = true}는 벌크 연산이 우회한 영속성 컨텍스트를 비워
   * 이후 조회가 최신 값을 다시 읽게 만듭니다.</p>
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Product p SET p.vcnt = p.vcnt + 1 WHERE p.no = :no AND p.isdel = 'N'")
  int increaseVcnt(@Param("no") Long no);
}
