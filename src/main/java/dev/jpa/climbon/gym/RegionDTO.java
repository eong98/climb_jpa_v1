package dev.jpa.climbon.gym;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 지역 코드 응답/요청 DTO.
 *
 * <p>{@code @JsonInclude(NON_NULL)} — 값이 null인 필드는 JSON에서 아예 빠집니다.
 * 프론트에서 {@code sigungu === undefined} 로 "시/도 전체" 항목을 구분할 수 있고
 * 응답 크기도 줄어듭니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionDTO {

  /** 지역번호 */
  private Long no;

  /** 시/도 */
  private String sido;

  /** 시/군/구 (null이면 시/도 전체) */
  private String sigungu;

  /** 정렬 순서 */
  private Integer sortOrder;

  /** 사용 여부 */
  private String useYn;

  /** DTO -> Entity (지역 코드 관리 화면에서 사용) */
  public Region toEntity() {
    return Region.builder()
        .no(this.no)
        .sido(this.sido)
        .sigungu(this.sigungu)
        .sortOrder(this.sortOrder == null ? 0 : this.sortOrder)
        .useYn(this.useYn == null ? "Y" : this.useYn)
        .build();
  }

  /** Entity -> DTO */
  public static RegionDTO fromEntity(Region entity) {
    if (entity == null) return null;
    return RegionDTO.builder()
        .no(entity.getNo())
        .sido(entity.getSido())
        .sigungu(entity.getSigungu())
        .sortOrder(entity.getSortOrder())
        .useYn(entity.getUseYn())
        .build();
  }
}
