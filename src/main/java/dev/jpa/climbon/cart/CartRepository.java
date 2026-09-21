package dev.jpa.climbon.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장바구니 Repository.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

  /**
   * 내 장바구니 목록 — 상품(PRODUCT) 정보 조인.
   *
   * <p>{@code JOIN Product p ON p.no = c.pno} 를 INNER JOIN으로 둔 이유:
   * 상품 자체가 DB에서 사라진(있을 수 없는) 경우라면 그 장바구니 줄은 그릴 수도 없습니다.
   * 반면 <b>논리삭제된 상품({@code isdel='Y'})은 일부러 포함</b>합니다 —
   * 목록에서 조용히 사라지면 사용자는 "내가 담은 게 없어졌다"고 느낍니다.
   * 대신 {@code orderable=false}로 내려보내 "판매 종료된 상품입니다"로 표시하게 합니다.</p>
   *
   * <p>정렬은 최근에 담은 것이 위로 오도록 {@code c.no DESC} 고정입니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.cart.CartDTO(
             c.no, c.mno, c.pno, c.qty, c.optSize, c.cdate,
             p.pname, p.brand, p.price, p.salePrice,
             p.thumb, p.stock, p.status, p.isdel)
      FROM Cart c
      JOIN Product p ON p.no = c.pno
      WHERE c.mno = :mno
      ORDER BY c.no DESC
      """)
  List<CartDTO> findMyCart(@Param("mno") Long mno);

  /**
   * "같은 상품 + 같은 사이즈"가 이미 담겨 있는지 조회합니다. (중복 행 방지의 핵심)
   *
   * <p><b>[실무 팁] 왜 메서드 이름 기반 쿼리({@code findByMnoAndPnoAndOptSize})를 쓰지 않았나?</b><br>
   * 사이즈 옵션이 없는 상품은 {@code optSize}가 <b>NULL</b>입니다.
   * 파라미터에 null을 넘기면 Spring Data가 {@code OPT_SIZE = NULL} 로 만드는데,
   * SQL에서 <b>NULL = NULL 은 참이 아니라 UNKNOWN</b>이라 아무 행도 찾지 못합니다.
   * 그러면 "사이즈 없는 상품"은 담을 때마다 새 행이 생겨 버립니다.
   * 그래서 {@code (:optSize IS NULL AND c.optSize IS NULL)} 분기를 명시적으로 적었습니다.
   * NULL 비교는 SQL 초심자가 가장 자주 밟는 지뢰입니다.</p>
   */
  @Query("""
      SELECT c
      FROM Cart c
      WHERE c.mno = :mno
        AND c.pno = :pno
        AND ((:optSize IS NULL AND c.optSize IS NULL) OR c.optSize = :optSize)
      """)
  Optional<Cart> findSameItem(
      @Param("mno") Long mno,
      @Param("pno") Long pno,
      @Param("optSize") String optSize);

  /** 내 장바구니 원본 엔티티 목록 (주문 생성 시 사용) */
  List<Cart> findByMno(Long mno);

  /** 장바구니 비우기 — 주문이 성사되면 담아 둔 상품을 모두 비웁니다. */
  void deleteByMno(Long mno);

  /**
   * 주문한 상품만 골라 장바구니에서 제거합니다. (부분 주문)
   * <p>장바구니에 5개가 있는데 2개만 주문했다면 나머지 3개는 남아 있어야 합니다.</p>
   */
  void deleteByMnoAndPnoIn(Long mno, List<Long> pnos);

  /** 내 장바구니 담긴 상품 수 (헤더 배지) */
  long countByMno(Long mno);
}
