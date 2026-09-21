package dev.jpa.climbon.notice;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 공지사항 저장소.
 *
 * <p>모든 조회 쿼리에 {@code n.isdel = 'N'} 조건이 고정으로 들어갑니다.
 * 논리삭제를 쓰는 이상 이 조건이 한 곳이라도 빠지면 지운 글이 화면에 다시 나타나므로,
 * 조건을 서비스가 아니라 <b>쿼리 안에 박아</b> 실수를 구조적으로 막았습니다.</p>
 */
@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

  /**
   * 공지 목록 검색 (제목/내용 + 구분 + 페이징).
   *
   * <p>[면접 포인트] <b>정렬을 Pageable이 아니라 쿼리에 고정한 이유</b><br>
   * 공지는 "상단 고정 글이 항상 맨 위, 그다음 최신순"이라는 규칙이 화면 요구사항입니다.
   * 이건 사용자가 바꿀 수 있는 정렬이 아니라 <b>비즈니스 규칙</b>이므로
   * ORDER BY를 쿼리에 못 박아 어느 호출부에서든 같은 순서가 나오게 했습니다.
   * {@code topYn}은 'Y'/'N' 문자열이라 DESC로 정렬하면 'Y'가 먼저 옵니다.</p>
   *
   * <p>이때 호출부에서 {@code Pageable}에 정렬을 같이 넘기면
   * ORDER BY가 뒤에 덧붙어 의도와 달라지므로, 서비스에서 정렬 없는 Pageable을 만듭니다.</p>
   *
   * @param word 제목 또는 내용 부분 일치 검색어
   * @param type 구분 필터 (null이면 전체)
   */
  @Query("SELECT n FROM Notice n WHERE n.isdel = 'N' "
      + "AND (:word IS NULL OR :word = '' "
      + "  OR n.title LIKE CONCAT('%', :word, '%') "
      + "  OR n.content LIKE CONCAT('%', :word, '%')) "
      + "AND (:type IS NULL OR n.type = :type) "
      + "ORDER BY n.topYn DESC, n.no DESC")
  Page<Notice> searchNotice(
      @Param("word") String word,
      @Param("type") Integer type,
      Pageable pageable);

  /** 삭제되지 않은 공지 단건 조회 */
  @Query("SELECT n FROM Notice n WHERE n.no = :no AND n.isdel = 'N'")
  Optional<Notice> findActiveByNo(@Param("no") Long no);

  /**
   * 조회수 1 증가 (벌크 UPDATE).
   *
   * <p>[면접 포인트] 변경 감지(엔티티 필드 수정)로도 조회수를 올릴 수 있지만,
   * 그 방식은 <b>읽은 값 + 1</b>을 쓰기 때문에 동시에 두 명이 열면
   * 한 번의 증가가 사라질 수 있습니다(lost update).
   * {@code SET vcnt = vcnt + 1}은 DB가 계산하므로 동시성 문제에서 자유롭습니다.</p>
   *
   * <p>{@code clearAutomatically = true}는 벌크 연산 후 영속성 컨텍스트를 비워
   * 1차 캐시에 남은 옛 조회수를 그대로 응답하는 사고를 막아 줍니다.</p>
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE Notice n SET n.vcnt = n.vcnt + 1 WHERE n.no = :no")
  int increaseVcnt(@Param("no") Long no);

  /** 최신 공지 N건 (메인 화면 위젯용). 상단 고정 우선 정렬은 동일합니다. */
  @Query("SELECT n FROM Notice n WHERE n.isdel = 'N' ORDER BY n.topYn DESC, n.no DESC")
  Page<Notice> findLatest(Pageable pageable);
}
