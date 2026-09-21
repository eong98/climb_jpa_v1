package dev.jpa.climbon.board.like;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게시글 좋아요 Repository.
 *
 * <p>좋아요는 "행이 있으면 누른 것, 없으면 안 누른 것"이라는 단순한 모델이라
 * 메서드 이름만으로 만들어지는 <b>쿼리 메서드</b>로 충분합니다.
 * JPQL을 직접 쓰는 곳은 목록 화면에서 여러 글의 좋아요 여부를 한 번에 판정하는 부분뿐입니다.</p>
 */
public interface BoardLikeRepository extends JpaRepository<BoardLike, Long> {

  /** 내가 이 글에 누른 좋아요 행 (없으면 empty) — 토글의 기준이 됩니다. */
  Optional<BoardLike> findByBnoAndMno(Long bno, Long mno);

  /** 좋아요 여부만 빠르게 확인 (상세 화면의 하트 아이콘 상태) */
  boolean existsByBnoAndMno(Long bno, Long mno);

  /** 이 글의 실제 좋아요 수 — 토글 직후 BOARD.LIKE_CNT에 복사할 값입니다. */
  long countByBno(Long bno);

  /** 게시글이 삭제될 때 좋아요 행도 함께 정리합니다. */
  void deleteByBno(Long bno);

  /**
   * 목록에 보이는 글들 중 <b>내가 좋아요를 누른 글번호</b>만 한 번에 조회합니다.
   *
   * <p><b>[실무 팁] 왜 IN 절로 한 번에 가져오나?</b><br>
   * 목록 20건마다 {@code existsByBnoAndMno()}를 부르면 좋아요 확인에만 쿼리 20번이 추가됩니다.
   * 화면에 보이는 글번호를 모아 IN 절로 한 번에 조회한 뒤, 자바에서 {@code Set}으로 만들어
   * O(1)로 대조하면 <b>쿼리 1번</b>으로 끝납니다. (전형적인 N+1 제거 패턴)</p>
   */
  @Query("SELECT l.bno FROM BoardLike l WHERE l.mno = :mno AND l.bno IN :bnos")
  List<Long> findLikedBnos(@Param("mno") Long mno, @Param("bnos") List<Long> bnos);
}
