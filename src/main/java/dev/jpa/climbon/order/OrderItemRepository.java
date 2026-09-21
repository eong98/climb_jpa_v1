package dev.jpa.climbon.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 상세 Repository.
 *
 * <p>ORDER_ITEM은 <b>주문 시점 스냅샷</b>이라 PRODUCT를 조인할 필요가 없습니다.
 * 상품명·가격·썸네일이 이미 이 테이블에 복사되어 있기 때문입니다.
 * (조인하면 오히려 "지금 가격"이 섞여 들어와 과거 주문서가 오염됩니다.)</p>
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

  /** 주문 상세 목록 (주문 상세 화면) */
  List<OrderItem> findByOnoOrderByNoAsc(Long ono);

  /**
   * 여러 주문의 상세를 <b>한 번에</b> 조회합니다. (주문 목록 화면)
   *
   * <p><b>[면접 포인트] 왜 IN 절로 모아서 조회하나?</b><br>
   * 주문 목록 10건을 그리면서 주문마다 {@code findByOno()}를 부르면
   * 쿼리가 1 + 10 = 11번 나갑니다(전형적인 <b>N+1</b>).
   * 화면에 보이는 주문번호를 모아 IN 절로 한 번에 읽고
   * 자바에서 {@code Map<주문번호, List<상세>>}로 묶으면 <b>쿼리 2번</b>으로 끝납니다.</p>
   *
   * <p>ORDER BY를 함께 준 이유: 그룹핑 후에도 주문 안의 상품 순서가 항상 같아야
   * 화면이 새로고침할 때마다 들썩이지 않습니다.</p>
   */
  @Query("SELECT i FROM OrderItem i WHERE i.ono IN :onos ORDER BY i.ono ASC, i.no ASC")
  List<OrderItem> findByOnoIn(@Param("onos") List<Long> onos);

  /**
   * 주문 상세를 DTO로 바로 조회합니다. (상세 화면 전용)
   * <p>엔티티를 영속화하지 않아 메모리를 덜 쓰고, 화면에 필요한 값만 선택합니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.order.OrderItemDTO(
             i.no, i.ono, i.pno, i.pname, i.price, i.qty, i.optSize, i.thumb)
      FROM OrderItem i
      WHERE i.ono = :ono
      ORDER BY i.no ASC
      """)
  List<OrderItemDTO> findItemDTOs(@Param("ono") Long ono);
}
