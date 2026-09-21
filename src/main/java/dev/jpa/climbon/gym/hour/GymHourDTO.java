package dev.jpa.climbon.gym.hour;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 암장 영업시간 DTO.
 *
 * <p>프론트는 요일 7건을 배열로 한 번에 보내고 한 번에 받습니다.
 * ({@code PUT /gym/{gno}/hours} 요청 바디 = {@code GymHourDTO[]})</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymHourDTO {

  /** 영업시간번호 (신규 등록 시 null) */
  private Long no;

  /** 암장번호 */
  private Long gno;

  /** 요일 (0: 일 ~ 6: 토) */
  private Integer dayOfWeek;

  /** 오픈 시각 'HH:mm' */
  private String openTime;

  /** 마감 시각 'HH:mm' */
  private String closeTime;

  /** 휴무 여부 (Y/N) */
  private String closedYn;

  /** 비고 */
  private String note;

  /** 요일 한글 라벨 — 프론트에서 매번 매핑 테이블을 들고 있지 않아도 되게 서버가 같이 내려줍니다. */
  private String dayLabel;

  /** 요일 숫자를 한글로 변환합니다. (0=일 ~ 6=토) */
  public static String toDayLabel(Integer dayOfWeek) {
    if (dayOfWeek == null) return null;
    return switch (dayOfWeek) {
      case 0 -> "일";
      case 1 -> "월";
      case 2 -> "화";
      case 3 -> "수";
      case 4 -> "목";
      case 5 -> "금";
      case 6 -> "토";
      default -> null;
    };
  }

  /** DTO -> Entity */
  public GymHour toEntity() {
    return GymHour.builder()
        .no(this.no)
        .gno(this.gno)
        .dayOfWeek(this.dayOfWeek == null ? 0 : this.dayOfWeek)
        .openTime(this.openTime)
        .closeTime(this.closeTime)
        .closedYn("Y".equalsIgnoreCase(this.closedYn) ? "Y" : "N")
        .note(this.note)
        .build();
  }

  /** Entity -> DTO */
  public static GymHourDTO fromEntity(GymHour entity) {
    if (entity == null) return null;
    return GymHourDTO.builder()
        .no(entity.getNo())
        .gno(entity.getGno())
        .dayOfWeek(entity.getDayOfWeek())
        .openTime(entity.getOpenTime())
        .closeTime(entity.getCloseTime())
        .closedYn(entity.getClosedYn())
        .note(entity.getNote())
        .dayLabel(toDayLabel(entity.getDayOfWeek()))
        .build();
  }
}
