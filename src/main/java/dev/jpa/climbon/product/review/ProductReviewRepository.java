package dev.jpa.climbon.product.review;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품 후기 Repository.
 */
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

  /** 살아 있는 후기 단건 조회 (삭제 전 검증용) */
  Optional<ProductReview> findByNoAndIsdel(Long no, String isdel);

  /**
   * 상품별 후기 목록 — 작성자(MEMBER) 정보 조인.
   *
   * <p>{@code LEFT JOIN Member m ON m.no = r.mno} : 엔티티 간 연관관계를 매핑하지 않았으므로
   * ON 절에 조인 조건을 직접 적습니다(Hibernate 6의 ad-hoc entity join).
   * LEFT로 둔 이유는 탈퇴 회원의 후기가 목록에서 통째로 사라지지 않게 하기 위함입니다.</p>
   *
   * <p>ORDER BY를 쿼리에 고정한 이유: 상품 후기는 "최신순"만 쓰므로
   * 정렬 옵션을 Pageable로 열어 둘 이유가 없습니다.
   * 정렬을 외부에서 받으면 프론트가 존재하지 않는 필드명을 보낼 때 500이 납니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.product.review.ProductReviewDTO(
             r.no, r.pno, r.mno, r.rating, r.content,
             r.optSize, r.fileyn, r.cdate,
             m.nickname, m.profileImg)
      FROM ProductReview r
      LEFT JOIN Member m ON m.no = r.mno
      WHERE r.pno = :pno
        AND r.isdel = 'N'
      ORDER BY r.no DESC
      """)
  Page<ProductReviewDTO> findReviewsByPno(@Param("pno") Long pno, Pageable pageable);

  /**
   * 특정 상품의 평균 평점과 후기 수를 한 번에 집계합니다.
   *
   * <p>반환: {@code List<Object[]>} 의 첫 행 = {@code {Double avg, Long cnt}}.
   * 후기가 0건이면 avg는 null, cnt는 0입니다.
   * 후기 등록/삭제 직후 PRODUCT의 반정규화 컬럼을 갱신할 때만 호출되므로
   * 조회 트래픽에는 영향을 주지 않습니다.</p>
   *
   * <p>AVG와 COUNT를 쿼리 하나에 담은 이유는 단순합니다 —
   * <b>같은 조건을 두 번 스캔할 이유가 없기 때문</b>입니다.</p>
   */
  @Query("""
      SELECT AVG(r.rating), COUNT(r.no)
      FROM ProductReview r
      WHERE r.pno = :pno
        AND r.isdel = 'N'
      """)
  List<Object[]> findRatingStats(@Param("pno") Long pno);

  /**
   * 같은 회원이 같은 상품에 이미 후기를 썼는지 확인합니다.
   * <p>1인 1후기 정책입니다. 한 사람이 여러 건을 남기면 평균 평점이 손쉽게 조작됩니다.</p>
   */
  boolean existsByPnoAndMnoAndIsdel(Long pno, Long mno, String isdel);
}
