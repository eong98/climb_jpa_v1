package dev.jpa.climbon.member;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 회원 저장소.
 *
 * <p>[실무 팁] 메서드 이름을 {@code findById(String)}처럼 지으면
 * JpaRepository가 이미 갖고 있는 {@code findById(Long)}(PK 조회)와 헷갈립니다.
 * 컴파일은 되지만 읽는 사람이 "PK로 찾는 건가? 로그인 아이디로 찾는 건가?" 오해하기 쉬워
 * 이 프로젝트에서는 <b>findByLoginId</b>처럼 이름을 분명히 구분했습니다.</p>
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

  /**
   * 로그인 아이디로 회원을 찾습니다. (로그인 / 중복확인용)
   *
   * <p>탈퇴(status=2) 회원도 함께 조회합니다. 탈퇴 회원의 아이디를 즉시 재사용하게 두면
   * 이전 회원의 게시글 작성자로 오인될 수 있어, 존재 여부 판단은 상태와 무관하게 합니다.</p>
   */
  @Query("SELECT m FROM Member m WHERE m.id = :id")
  Optional<Member> findByLoginId(@Param("id") String id);

  /** 로그인 아이디 중복 여부 */
  @Query("SELECT COUNT(m) FROM Member m WHERE m.id = :id")
  long countByLoginId(@Param("id") String id);

  /** 닉네임 중복 여부 — 커뮤니티 노출명이라 UNIQUE 제약이 걸려 있습니다. */
  boolean existsByNickname(String nickname);

  /**
   * 닉네임 중복 여부 (본인 제외).
   *
   * <p>내 정보 수정 화면에서 닉네임을 바꾸지 않고 저장하면
   * "내 닉네임이 이미 존재한다"는 엉뚱한 오류가 납니다. 그래서 본인(no)은 검사에서 뺍니다.</p>
   */
  @Query("SELECT COUNT(m) FROM Member m WHERE m.nickname = :nickname AND m.no <> :no")
  long countByNicknameExceptMe(@Param("nickname") String nickname, @Param("no") Long no);

  /**
   * 관리자 회원 목록 검색 (검색어 + 등급 + 상태 + 페이징).
   *
   * <p>[면접 포인트] 조건이 여러 개인 동적 검색을 JPQL 하나로 처리하는 방법:
   * {@code (:grade IS NULL OR m.grade = :grade)} 패턴을 씁니다.
   * 파라미터가 null이면 앞 조건이 참이 되어 해당 필터가 통째로 무시됩니다.
   * 덕분에 조건 조합마다 쿼리를 따로 만들 필요가 없습니다.
   * (조건이 더 복잡해지면 QueryDSL이나 Specification을 도입하는 편이 낫습니다.)</p>
   *
   * <p>정렬을 쿼리에 고정하지 않고 {@link Pageable}에 맡겨
   * 호출부에서 정렬 기준을 바꿀 수 있게 했습니다.</p>
   *
   * @param word   아이디 / 이름 / 닉네임 / 이메일 부분 일치 검색어
   * @param grade  등급 필터 (null이면 전체)
   * @param status 상태 필터 (null이면 전체)
   */
  @Query("SELECT m FROM Member m WHERE "
      + "(:word IS NULL OR :word = '' "
      + "  OR m.id LIKE CONCAT('%', :word, '%') "
      + "  OR m.mname LIKE CONCAT('%', :word, '%') "
      + "  OR m.nickname LIKE CONCAT('%', :word, '%') "
      + "  OR m.email LIKE CONCAT('%', :word, '%')) "
      + "AND (:grade IS NULL OR m.grade = :grade) "
      + "AND (:status IS NULL OR m.status = :status)")
  Page<Member> searchMembers(
      @Param("word") String word,
      @Param("grade") Integer grade,
      @Param("status") Integer status,
      Pageable pageable);
}
