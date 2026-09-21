package dev.jpa.climbon.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 Repository.
 *
 * <p><b>[면접 포인트] 왜 {@code FROM Orders o} 인가? (클래스명은 Order인데)</b><br>
 * {@code ORDER}는 SQL 예약어라 테이블명을 ORDERS로 만들었고,
 * JPQL에서도 {@code FROM Order o}는 {@code ORDER BY} 파서와 충돌할 수 있어
 * {@link Order} 엔티티가 {@code @Entity(name = "Orders")}로 <b>엔티티 이름</b>을 따로 지정했습니다.
 * JPQL은 테이블명이 아니라 이 엔티티 이름을 쓰므로 여기서는 반드시 {@code Orders}라고 적어야 합니다.
 * (생성자 표현식의 {@code dev.jpa.climbon.order.OrderDTO}는 자바 클래스 경로라 영향이 없습니다.)</p>
 *
 * <p><b>[면접 포인트] 목록 조회에 왜 엔티티가 아니라 DTO 생성자 표현식을 쓰나?</b><br>
 * 주문 목록 화면에는 <b>주문자 닉네임</b>이 필요한데(관리자 화면),
 * 엔티티로 읽어 오면 주문 20건마다 MEMBER를 한 번씩 더 조회하게 됩니다(N+1).
 * ORDERS와 MEMBER는 연관관계를 매핑하지 않았으므로
 * {@code LEFT JOIN Member m ON m.no = o.mno} 로 조인 조건을 직접 적습니다
 * (Hibernate 6의 ad-hoc entity join). LEFT로 둔 이유는 탈퇴·삭제된 회원의 주문이
 * 목록에서 통째로 사라지지 않게 하기 위함입니다 — 주문 기록은 보관 의무가 있는 데이터입니다.</p>
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

  /**
   * 내 주문 목록. (페이징)
   *
   * <p>정렬을 {@code Pageable}에 맡기지 않고 쿼리에 {@code o.no DESC}로 고정한 이유:
   * 주문 목록은 "최근 주문이 위"가 유일하게 자연스러운 순서라 정렬 옵션이 필요 없고,
   * 정렬이 결정적이어야 페이지를 넘길 때 같은 주문이 두 번 보이지 않습니다.</p>
   *
   * <p>회원번호를 파라미터로 받지만 <b>컨트롤러가 아니라 Service가 토큰에서 꺼낸 값</b>을 넘깁니다.
   * 프론트가 보낸 mno를 그대로 쓰면 번호만 바꿔 남의 주문 내역을 열람할 수 있습니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.order.OrderDTO(
             o.no, o.orderCode, o.mno, o.orderName,
             o.totalPrice, o.deliveryFee, o.payMethod,
             o.payStatus, o.deliveryStatus,
             o.receiver, o.phone, o.zipcode, o.addr, o.addrDetail,
             o.memo, o.cdate, o.udate, m.nickname)
      FROM Orders o
      LEFT JOIN Member m ON m.no = o.mno
      WHERE o.mno = :mno
      ORDER BY o.no DESC
      """)
  Page<OrderDTO> findMyOrders(@Param("mno") Long mno, Pageable pageable);

  /**
   * 관리자 주문 검색. — 주문코드 / 수령인 + 결제상태 + 배송상태 필터 (페이징)
   *
   * <p><b>[면접 포인트] 조건이 여러 개인 검색을 JPQL 하나로 처리하는 방법</b><br>
   * {@code (:payStatus IS NULL OR o.payStatus = :payStatus)} 관용구를 씁니다.
   * 파라미터가 null이면 앞 조건이 참이 되어 그 필터가 통째로 무시되므로
   * 조건 조합(2^3가지)마다 쿼리를 따로 만들 필요가 없습니다.</p>
   *
   * <p>문자열 조건은 {@code :word IS NULL OR :word = ''} 처럼 <b>빈 문자열까지</b> 검사합니다.
   * 프론트가 검색어를 지우면 null이 아니라 ""를 보내는 경우가 많은데,
   * 이걸 빼먹으면 {@code LIKE '%%'} 가 되어 인덱스를 못 타는 전체 스캔이 됩니다.</p>
   *
   * @param word         주문코드 또는 수령인 부분일치 (null/빈값이면 전체)
   * @param payStatus    결제상태 (0: 대기, 1: 완료, 2: 취소, 3: 환불) — null이면 전체
   * @param deliveryStatus 배송상태 (0: 준비, 1: 출고, 2: 배송중, 3: 완료) — null이면 전체
   */
  @Query(value = """
      SELECT new dev.jpa.climbon.order.OrderDTO(
             o.no, o.orderCode, o.mno, o.orderName,
             o.totalPrice, o.deliveryFee, o.payMethod,
             o.payStatus, o.deliveryStatus,
             o.receiver, o.phone, o.zipcode, o.addr, o.addrDetail,
             o.memo, o.cdate, o.udate, m.nickname)
      FROM Orders o
      LEFT JOIN Member m ON m.no = o.mno
      WHERE (:word IS NULL OR :word = ''
             OR o.orderCode LIKE CONCAT('%', :word, '%')
             OR o.receiver  LIKE CONCAT('%', :word, '%'))
        AND (:payStatus IS NULL OR o.payStatus = :payStatus)
        AND (:deliveryStatus IS NULL OR o.deliveryStatus = :deliveryStatus)
      ORDER BY o.no DESC
      """,
      countQuery = """
      SELECT COUNT(o.no)
      FROM Orders o
      WHERE (:word IS NULL OR :word = ''
             OR o.orderCode LIKE CONCAT('%', :word, '%')
             OR o.receiver  LIKE CONCAT('%', :word, '%'))
        AND (:payStatus IS NULL OR o.payStatus = :payStatus)
        AND (:deliveryStatus IS NULL OR o.deliveryStatus = :deliveryStatus)
      """)
  Page<OrderDTO> searchOrders(
      @Param("word") String word,
      @Param("payStatus") Integer payStatus,
      @Param("deliveryStatus") Integer deliveryStatus,
      Pageable pageable);

  /**
   * 주문코드로 주문을 찾습니다. (고객센터 문의 / 결제 완료 콜백)
   *
   * <p>결제 PG사 콜백은 우리 PK(NO)를 모르고 <b>주문코드</b>만 알고 있는 경우가 많습니다.
   * ORDER_CODE에는 UNIQUE 제약(UK_ORDERS_CODE)이 걸려 있어 단건 조회가 보장됩니다.</p>
   */
  Optional<Order> findByOrderCode(String orderCode);

  /**
   * 주문 상세 1건 (주문자 닉네임 포함).
   *
   * <p>상세 화면도 관리자/본인 모두가 쓰므로 목록과 같은 DTO를 돌려줍니다.
   * 소유자 검증은 Service가 이 결과의 {@code mno}로 수행합니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.order.OrderDTO(
             o.no, o.orderCode, o.mno, o.orderName,
             o.totalPrice, o.deliveryFee, o.payMethod,
             o.payStatus, o.deliveryStatus,
             o.receiver, o.phone, o.zipcode, o.addr, o.addrDetail,
             o.memo, o.cdate, o.udate, m.nickname)
      FROM Orders o
      LEFT JOIN Member m ON m.no = o.mno
      WHERE o.no = :no
      """)
  Optional<OrderDTO> findOrderDetail(@Param("no") Long no);
}
