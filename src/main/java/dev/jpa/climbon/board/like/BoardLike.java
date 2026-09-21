package dev.jpa.climbon.board.like;

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
 * 게시글 좋아요 엔티티. — BOARD_LIKE 테이블
 *
 * <p><b>[면접 포인트] 왜 BOARD.LIKE_CNT 숫자만 올리지 않고 별도 테이블을 두나?</b><br>
 * 카운트만 증감하면 <b>같은 사람이 여러 번 누를 수 있고</b>, "내가 이미 눌렀는지"를
 * 화면에 표시할 수도 없습니다. (누가 눌렀는지 기록이 없기 때문입니다.)
 * (BNO, MNO) UNIQUE 제약이 걸린 테이블을 두면
 * <b>행의 존재 여부 = 좋아요 여부</b>가 되어 토글이 자연스럽게 구현되고,
 * DB 제약이 중복 좋아요를 마지막으로 한 번 더 막아 줍니다.</p>
 *
 * <p>대신 목록 화면에서 매번 COUNT 하면 느리므로, 토글 직후에 COUNT 결과를
 * BOARD.LIKE_CNT에 복사해 두는 <b>반정규화</b>를 함께 씁니다.
 * (정확한 원본 = BOARD_LIKE, 빠른 조회용 사본 = BOARD.LIKE_CNT)</p>
 */
@Entity
@Table(name = "BOARD_LIKE")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardLike {

  /** 좋아요번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "board_like_seq_use")
  @SequenceGenerator(name = "board_like_seq_use", sequenceName = "BOARD_LIKE_SEQ", allocationSize = 1)
  private Long no;

  /** 게시글번호 (FK -> BOARD.NO) */
  private Long bno;

  /** 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 등록일시 'yyyy-MM-dd HH:mm:ss' */
  private String cdate;
}
