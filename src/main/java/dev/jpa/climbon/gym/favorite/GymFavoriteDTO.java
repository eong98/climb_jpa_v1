package dev.jpa.climbon.gym.favorite;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 암장 찜 DTO.
 *
 * <p>토글 결과({@code {favorite, count}})는 컨트롤러가 Map으로 직접 만들어 내려주므로,
 * 이 DTO는 주로 "내 찜 목록"의 메타 정보(찜한 날짜 등)를 담는 데 씁니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymFavoriteDTO {

  /** 찜번호 */
  private Long no;

  /** 암장번호 */
  private Long gno;

  /** 회원번호 */
  private Long mno;

  /** 찜한 일시 */
  private String cdate;

  /** 암장명 — 조인해서 채우는 값 */
  private String gname;

  /** 찜 여부 (토글 응답용) */
  private Boolean favorite;

  /** 해당 암장의 총 찜 수 (토글 응답용) */
  private Integer count;

  /** DTO -> Entity */
  public GymFavorite toEntity() {
    return GymFavorite.builder()
        .no(this.no)
        .gno(this.gno)
        .mno(this.mno)
        .cdate(this.cdate == null ? Tool.getDate() : this.cdate)
        .build();
  }

  /** Entity -> DTO */
  public static GymFavoriteDTO fromEntity(GymFavorite entity) {
    if (entity == null) return null;
    return GymFavoriteDTO.builder()
        .no(entity.getNo())
        .gno(entity.getGno())
        .mno(entity.getMno())
        .cdate(entity.getCdate())
        .build();
  }
}
