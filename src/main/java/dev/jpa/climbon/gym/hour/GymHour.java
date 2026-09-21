package dev.jpa.climbon.gym.hour;

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
 * 암장 요일별 영업시간 엔티티. — GYM_HOUR 테이블
 *
 * <p><b>[면접 포인트] 왜 시간을 DATE가 아닌 'HH:mm' 문자열로 저장했나?</b><br>
 * 영업시간은 "날짜 없는 시각"이라 DATE 타입에 넣으면 1970-01-01 같은 의미 없는 날짜가 붙고,
 * 타임존 변환에도 휘둘립니다. 반면 'HH:mm' 고정폭 문자열은
 * <b>사전식 비교가 곧 시각 비교</b>라서 {@code "09:00" <= "14:30" <= "23:00"} 처럼
 * 문자열 비교만으로 "지금 영업중"을 판정할 수 있습니다.
 * (고정폭이 핵심입니다. "9:00"처럼 한 자리로 저장하면 비교가 깨집니다.)</p>
 *
 * <p>요일은 0(일) ~ 6(토). {@code Tool.getTodayDayOfWeek()}가 같은 규칙으로 값을 돌려줍니다.</p>
 */
@Entity
@Table(name = "GYM_HOUR")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GymHour {

  /** 영업시간번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gym_hour_seq_use")
  @SequenceGenerator(name = "gym_hour_seq_use", sequenceName = "GYM_HOUR_SEQ", allocationSize = 1)
  private Long no;

  /** 암장번호 (FK -> GYM.NO) */
  private Long gno;

  /** 요일 (0: 일 ~ 6: 토) */
  private int dayOfWeek;

  /** 오픈 시각 'HH:mm' */
  private String openTime;

  /** 마감 시각 'HH:mm' */
  private String closeTime;

  /** 휴무 여부 (Y/N) */
  @Builder.Default
  private String closedYn = "N";

  /** 비고 (공휴일 단축운영 등) */
  private String note;

  /**
   * 주어진 시각('HH:mm')에 영업중인지 판정합니다.
   *
   * <p>자정을 넘겨 마감하는 암장(예: 10:00 ~ 02:00)이 실제로 존재하기 때문에
   * 단순히 {@code open <= now <= close}로만 보면 안 됩니다.
   * close가 open보다 작으면 "다음날로 넘어간 영업"으로 보고
   * {@code now >= open || now <= close} 로 판정합니다.</p>
   *
   * @param nowHm 현재 시각 'HH:mm' ({@code Tool.getNowHm()})
   */
  public boolean isOpenAt(String nowHm) {
    if ("Y".equals(this.closedYn)) return false;                 // 정기 휴무
    if (openTime == null || closeTime == null) return false;      // 시간 미등록이면 판정 불가 -> 제외
    if (nowHm == null) return false;

    if (openTime.compareTo(closeTime) <= 0) {
      // 일반적인 경우: 10:00 ~ 23:00
      return nowHm.compareTo(openTime) >= 0 && nowHm.compareTo(closeTime) <= 0;
    }
    // 자정 넘김: 10:00 ~ 02:00
    return nowHm.compareTo(openTime) >= 0 || nowHm.compareTo(closeTime) <= 0;
  }
}
