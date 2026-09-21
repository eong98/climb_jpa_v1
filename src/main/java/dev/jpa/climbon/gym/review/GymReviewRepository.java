package dev.jpa.climbon.gym.review;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 암장 리뷰 Repository.
 *
 * <p>목록 조회는 전부 <b>DTO 생성자 표현식</b>을 씁니다.
 * 엔티티를 조회해 서비스에서 변환하면 (1) 작성자 정보를 얻기 위한 추가 쿼리(N+1)가 생기고
 * (2) 목록에 필요 없는 CLOB/AI 컬럼까지 읽게 됩니다.</p>
 */
public interface GymReviewRepository extends JpaRepository<GymReview, Long> {

  /**
   * 암장별 리뷰 목록 — 작성자(MEMBER) 정보 조인.
   *
   * <p>{@code LEFT JOIN Member m ON m.no = r.mno} : 엔티티 간 연관관계를 매핑하지 않았기 때문에
   * ON 절로 조인 조건을 직접 적습니다(Hibernate 6의 ad-hoc entity join).
   * LEFT로 둔 이유는 탈퇴 회원의 리뷰가 목록에서 통째로 사라지지 않게 하기 위함입니다.</p>
   *
   * <p>정렬은 {@code Pageable}의 Sort로 주입되므로 ORDER BY를 쿼리에 박지 않습니다.
   * (최신순/도움순/평점순을 같은 쿼리로 재사용)</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.gym.review.GymReviewDTO(
             r.no, r.gno, r.mno, r.rating,
             r.scoreFacility, r.scoreRoute, r.scoreClean,
             r.title, r.content, r.visitDate,
             r.likeCnt, r.fileyn, r.cdate, r.udate,
             m.nickname, m.profileImg, m.boulderLevel)
      FROM GymReview r
      LEFT JOIN Member m ON m.no = r.mno
      WHERE r.gno = :gno
        AND r.isdel = 'N'
      """)
  Page<GymReviewDTO> findReviewsByGno(@Param("gno") Long gno, Pageable pageable);

  /**
   * 암장 상세에 붙일 최신 리뷰 N건. (페이징 객체로 건수 제한)
   * <p>상세 화면은 "요약 5건 + 더보기"라서 전체 페이징이 필요 없습니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.gym.review.GymReviewDTO(
             r.no, r.gno, r.mno, r.rating,
             r.scoreFacility, r.scoreRoute, r.scoreClean,
             r.title, r.content, r.visitDate,
             r.likeCnt, r.fileyn, r.cdate, r.udate,
             m.nickname, m.profileImg, m.boulderLevel)
      FROM GymReview r
      LEFT JOIN Member m ON m.no = r.mno
      WHERE r.gno = :gno
        AND r.isdel = 'N'
      ORDER BY r.no DESC
      """)
  List<GymReviewDTO> findRecentReviews(@Param("gno") Long gno, Pageable pageable);

  /**
   * 내가 쓴 리뷰 목록 — 암장명(GYM) 조인.
   * <p>마이페이지에서는 작성자가 나 자신이므로 회원 정보 대신 암장명이 필요합니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.gym.review.GymReviewDTO(
             r.no, r.gno, r.mno, r.rating,
             r.title, r.content, r.visitDate,
             r.likeCnt, r.cdate, g.gname)
      FROM GymReview r
      LEFT JOIN Gym g ON g.no = r.gno
      WHERE r.mno = :mno
        AND r.isdel = 'N'
      ORDER BY r.no DESC
      """)
  Page<GymReviewDTO> findMyReviews(@Param("mno") Long mno, Pageable pageable);

  /**
   * 특정 암장의 평균 평점과 리뷰 수를 한 번에 집계합니다.
   *
   * <p>반환: {@code List<Object[]>} 의 첫 행 = {@code {Double avg, Long cnt}}.
   * 리뷰가 0건이면 avg는 null, cnt는 0입니다.
   * 리뷰 등록/수정/삭제 직후 GYM의 반정규화 컬럼을 갱신할 때만 호출되는 쿼리라
   * 조회 트래픽에는 영향을 주지 않습니다.</p>
   */
  @Query("""
      SELECT AVG(r.rating), COUNT(r.no)
      FROM GymReview r
      WHERE r.gno = :gno
        AND r.isdel = 'N'
      """)
  List<Object[]> findRatingStats(@Param("gno") Long gno);

  /** 살아 있는 리뷰 단건 조회 (수정/삭제 전 검증용) */
  Optional<GymReview> findByNoAndIsdel(Long no, String isdel);

  /**
   * 같은 회원이 같은 암장에 이미 리뷰를 썼는지 확인합니다.
   * <p>암장 리뷰는 1인 1건 정책입니다. 한 사람이 여러 건을 남기면
   * 평균 평점이 손쉽게 조작되기 때문입니다.</p>
   */
  boolean existsByGnoAndMnoAndIsdel(Long gno, Long mno, String isdel);
}
