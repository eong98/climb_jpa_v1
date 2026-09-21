package dev.jpa.climbon.gym.favorite;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.jpa.climbon.gym.Gym;

/**
 * 암장 찜 Repository.
 */
public interface GymFavoriteRepository extends JpaRepository<GymFavorite, Long> {

  /** 특정 회원이 특정 암장을 찜했는지 (토글 시 현재 상태 확인) */
  Optional<GymFavorite> findByGnoAndMno(Long gno, Long mno);

  /** 특정 암장의 찜 수 — GYM.FAVORITE_CNT 동기화에 사용 */
  long countByGno(Long gno);

  /**
   * 내 찜 목록 — 암장 정보를 조인해서 Gym 엔티티로 반환합니다.
   *
   * <p>찜 테이블만 조회한 뒤 암장을 하나씩 findById 하면 N+1이 됩니다.
   * 조인 한 번으로 암장 엔티티를 바로 받아 서비스에서 GymDTO로 변환합니다.</p>
   *
   * <p>정렬은 {@code f.no DESC} = 최근에 찜한 순. 암장 등록순이 아니라
   * "내가 담은 순"이 사용자 기대에 맞습니다.</p>
   */
  @Query("""
      SELECT g
      FROM GymFavorite f
      JOIN Gym g ON g.no = f.gno
      WHERE f.mno = :mno
        AND g.isdel = 'N'
      ORDER BY f.no DESC
      """)
  Page<Gym> findMyFavoriteGyms(@Param("mno") Long mno, Pageable pageable);

  /**
   * 목록에 뜬 암장들 중 내가 찜한 것의 암장번호만 한 번에 조회합니다.
   *
   * <p><b>[면접 포인트]</b> 목록 20건마다 "찜했나?"를 확인하면 쿼리가 20번 나갑니다.
   * 암장번호를 IN 절로 묶어 <b>한 번에</b> 가져와 Set으로 만든 뒤 메모리에서 대조하면
   * 추가 쿼리는 1회로 끝납니다.</p>
   */
  @Query("""
      SELECT f.gno
      FROM GymFavorite f
      WHERE f.mno = :mno
        AND f.gno IN :gnoList
      """)
  List<Long> findFavoriteGnoList(@Param("mno") Long mno, @Param("gnoList") List<Long> gnoList);
}
