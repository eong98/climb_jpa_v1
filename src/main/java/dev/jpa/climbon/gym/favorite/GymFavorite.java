package dev.jpa.climbon.gym.favorite;

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
 * 암장 찜(즐겨찾기) 엔티티. — GYM_FAVORITE 테이블
 *
 * <p>(GNO, MNO)에 UNIQUE 제약이 걸려 있습니다.
 * 애플리케이션에서 "이미 찜했는지" 먼저 확인하더라도, 따닥 클릭이나 동시 요청으로
 * 중복 INSERT가 들어올 수 있습니다. <b>최종 방어선은 DB 제약</b>이어야 합니다.</p>
 *
 * <p>찜은 논리삭제가 아니라 <b>물리삭제</b>입니다. 찜 취소는 되돌릴 이유도, 이력을 남길 이유도
 * 없고, ISDEL을 두면 UNIQUE 제약과 충돌해(취소한 행이 남아 재찜이 막힘) 오히려 복잡해집니다.</p>
 */
@Entity
@Table(name = "GYM_FAVORITE")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GymFavorite {

  /** 찜번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gym_favorite_seq_use")
  @SequenceGenerator(name = "gym_favorite_seq_use", sequenceName = "GYM_FAVORITE_SEQ", allocationSize = 1)
  private Long no;

  /** 암장번호 (FK -> GYM.NO) */
  private Long gno;

  /** 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 등록일시 */
  private String cdate;
}
