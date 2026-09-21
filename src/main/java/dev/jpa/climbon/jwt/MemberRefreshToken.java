package dev.jpa.climbon.jwt;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 발급된 RefreshToken을 DB에 보관하는 엔티티. (테이블: MEMBER_REFRESH_TOKEN)
 *
 * <p><b>왜 토큰을 서버에 저장하는가?</b><br>
 * JWT는 원래 "서버가 상태를 갖지 않는" 방식이라 한 번 발급하면 만료 전까지 취소할 수 없습니다.
 * 그런데 로그아웃·비밀번호 변경·탈퇴 시에는 즉시 무효화가 필요합니다.
 * 그래서 수명이 긴 RefreshToken만 DB에 저장해 두고
 * "저장된 값과 똑같을 때만 재발급"하는 화이트리스트 방식으로 통제합니다.</p>
 *
 * <p>[면접 포인트] 팀 프로젝트는 같은 역할을 Redis(RefreshTokenRedisRepository)로 구현했습니다.
 * Redis는 TTL로 자동 만료되어 편하지만 별도 서버가 필요합니다.
 * 이 프로젝트는 Redis 없이도 돌아가야 하므로 <b>DB 테이블 방식</b>을 택했고,
 * 대신 만료 토큰은 재발급/로그인 시점에 직접 정리합니다.</p>
 *
 * <p>[실무 팁] 엔티티 이름(MemberRefreshToken)과 테이블 이름(MEMBER_REFRESH_TOKEN)의
 * 매핑은 Spring Boot 기본 네이밍 전략(CamelCase -> UPPER_SNAKE_CASE)으로 자동 처리되지만,
 * 이름이 길어 오해 소지가 있어 {@code @Table}로 명시해 두었습니다.</p>
 */
@Entity
@Table(name = "MEMBER_REFRESH_TOKEN")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberRefreshToken {

  /** 토큰번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "member_refresh_token_seq_use")
  @SequenceGenerator(name = "member_refresh_token_seq_use",
      sequenceName = "MEMBER_REFRESH_TOKEN_SEQ", allocationSize = 1)
  private Long no;

  /**
   * 회원번호 (FK -> MEMBER.NO)
   *
   * <p>@ManyToOne 연관관계 대신 <b>단순 Long</b>으로 둔 이유:
   * 이 테이블은 토큰 검증에만 쓰이고 회원 정보를 함께 꺼낼 일이 없어서
   * 굳이 조인 비용과 지연로딩(LAZY) 관리 부담을 질 필요가 없기 때문입니다.</p>
   */
  private Long mno;

  /** 리프레시 토큰 값 (VARCHAR2(500)) */
  private String refreshToken;

  /** 만료 일시 'yyyy-MM-dd HH:mm:ss' */
  private String expireDate;

  /** 발급 일시 'yyyy-MM-dd HH:mm:ss' */
  private String cdate;
}
