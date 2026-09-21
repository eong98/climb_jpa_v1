package dev.jpa.climbon.gym;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 지역 코드 Repository.
 *
 * <p>검색 필터의 시/도 · 시/군/구 선택지를 제공합니다.</p>
 */
public interface RegionRepository extends JpaRepository<Region, Long> {

  /**
   * 사용중인 지역 전체를 정렬 순서대로 조회합니다.
   * <p>프론트는 이 목록을 한 번 받아 시/도 → 시/군/구 2단 셀렉트를 구성합니다.
   * 데이터가 수백 건 수준이라 통째로 내려도 부담이 없고, 셀렉트 변경마다
   * 서버를 다시 호출하지 않아도 되어 체감 속도가 좋습니다.</p>
   */
  List<Region> findByUseYnOrderBySortOrderAscSidoAscSigunguAsc(String useYn);

  /**
   * 시/도 목록만 중복 없이 조회합니다.
   * <p>REGION에는 (서울, null), (서울, 강남구) ... 처럼 같은 시/도가 여러 건 있으므로
   * DISTINCT가 필요합니다.</p>
   */
  @Query("""
      SELECT DISTINCT r.sido
      FROM Region r
      WHERE r.useYn = 'Y'
      ORDER BY r.sido ASC
      """)
  List<String> findSidoList();

  /** 특정 시/도의 시/군/구 목록 (sigungu가 NULL인 "전체" 행은 제외) */
  @Query("""
      SELECT r
      FROM Region r
      WHERE r.useYn = 'Y'
        AND r.sido = :sido
        AND r.sigungu IS NOT NULL
      ORDER BY r.sortOrder ASC, r.sigungu ASC
      """)
  List<Region> findSigunguList(@Param("sido") String sido);
}
