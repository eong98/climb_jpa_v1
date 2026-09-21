package dev.jpa.climbon.jwt;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * RefreshToken 저장소.
 *
 * <p>메서드 이름만으로 쿼리가 자동 생성되는 Spring Data JPA의 쿼리 메서드를 주로 사용하고,
 * 삭제처럼 벌크 연산이 필요한 곳만 {@code @Modifying @Query}로 직접 작성했습니다.</p>
 */
@Repository
public interface MemberRefreshTokenRepository extends JpaRepository<MemberRefreshToken, Long> {

  /**
   * 토큰 값으로 저장 이력을 찾습니다.
   *
   * <p>재발급 요청이 들어오면 "서명은 맞지만 서버가 발급한 적 없는(또는 이미 폐기된) 토큰"을
   * 걸러내야 합니다. 그 검증을 이 조회 하나로 처리합니다.</p>
   */
  Optional<MemberRefreshToken> findByRefreshToken(String refreshToken);

  /**
   * 특정 회원의 토큰을 모두 삭제합니다. (로그아웃 / 재발급 시 회전)
   *
   * <p>[면접 포인트] <b>Refresh Token Rotation(RTR)</b><br>
   * 재발급할 때마다 기존 토큰을 지우고 새 토큰을 저장하면,
   * 탈취범이 예전 토큰으로 다시 요청했을 때 DB에 없으므로 즉시 거절됩니다.
   * "한 번 쓴 리프레시 토큰은 두 번 쓰지 못하게" 만드는 것이 핵심입니다.</p>
   *
   * <p>{@code clearAutomatically = true}는 벌크 삭제 후 영속성 컨텍스트를 비워
   * 1차 캐시에 남은 옛 데이터를 다시 읽는 사고를 막아 줍니다.</p>
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM MemberRefreshToken t WHERE t.mno = :mno")
  int deleteByMno(@Param("mno") Long mno);

  /**
   * 만료 일시가 지난 토큰을 한꺼번에 정리합니다.
   *
   * <p>EXPIRE_DATE가 'yyyy-MM-dd HH:mm:ss' 고정 길이 문자열이라
   * 문자열 비교만으로도 시간 순서 비교가 성립합니다. (사전순 = 시간순)
   * 별도 배치 서버 없이 로그인/재발급 시점에 호출해 쓰레기 데이터를 줄입니다.</p>
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM MemberRefreshToken t WHERE t.expireDate < :now")
  int deleteExpired(@Param("now") String now);
}
