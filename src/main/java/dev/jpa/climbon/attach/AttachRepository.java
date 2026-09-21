package dev.jpa.climbon.attach;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 공통 첨부파일 저장소.
 *
 * <p>조회 조건이 항상 {@code tname + bno} 한 쌍인 이유는
 * ATTACH가 모든 도메인이 공유하는 테이블이라 bno만으로는
 * "BOARD 10번 글"인지 "PRODUCT 10번 상품"인지 구분할 수 없기 때문입니다.
 * (schema.sql에도 IDX_ATTACH_TNAME_BNO 인덱스가 이 순서로 걸려 있습니다.)</p>
 */
@Repository
public interface AttachRepository extends JpaRepository<Attach, Long> {

  /**
   * 원글의 첨부 목록 조회.
   *
   * <p>이미지(type=0)를 먼저 보여주려고 type 오름차순, 등록순 유지를 위해 no 오름차순으로 정렬합니다.</p>
   */
  List<Attach> findByTnameAndBnoOrderByTypeAscNoAsc(String tname, Long bno);

  /** 원글의 첨부를 일괄 삭제합니다. (원글 삭제 시 호출) */
  void deleteByTnameAndBno(String tname, Long bno);

  /** 원글에 첨부가 하나라도 있는지 확인합니다. (게시글의 FILEYN 갱신용) */
  boolean existsByTnameAndBno(String tname, Long bno);

  /**
   * 관리자 첨부파일 검색 (파일명 + 테이블명 + 종류 + 페이징).
   *
   * <p>{@code (:param IS NULL OR ...)} 패턴으로 선택 조건을 동적으로 처리합니다.
   * 문자열 파라미터는 빈 문자열도 "조건 없음"으로 함께 취급해
   * 프론트가 {@code word=} 처럼 빈 값을 보내도 정상 동작합니다.</p>
   */
  @Query("SELECT a FROM Attach a WHERE "
      + "(:word IS NULL OR :word = '' OR a.name LIKE CONCAT('%', :word, '%')) "
      + "AND (:tname IS NULL OR :tname = '' OR a.tname = :tname) "
      + "AND (:type IS NULL OR a.type = :type)")
  Page<Attach> searchAttach(
      @Param("word") String word,
      @Param("tname") String tname,
      @Param("type") Integer type,
      Pageable pageable);
}
