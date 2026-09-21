package dev.jpa.climbon.product.review;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 상품 후기 DTO.
 *
 * <p>목록은 {@code LEFT JOIN Member} 생성자 표현식으로 조회해 작성자 정보를 함께 가져옵니다.
 * 후기 10건에 회원 조회 10번이 추가로 나가는 N+1을 막기 위한 이 프로젝트의 공통 패턴입니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductReviewDTO {

  /** 후기번호 (등록 시 null) */
  private Long no;

  /** 상품번호 */
  private Long pno;

  /** 작성 회원번호 */
  private Long mno;

  /** 평점 (1.0 ~ 5.0) */
  private Double rating;

  /** 내용 */
  private String content;

  /** 구매 사이즈 */
  private String optSize;

  /** 첨부 이미지 보유 여부 */
  private String fileyn;

  /** 등록일시 */
  private String cdate;

  /* ---------------- 조인 값 (DB 컬럼 아님) ---------------- */

  /** 작성자 닉네임 */
  private String nickname;

  /** 작성자 프로필 이미지 */
  private String profileImg;

  /** 상품명 — 내가 쓴 후기 목록에서만 채워집니다. */
  private String pname;

  /* ---------------- 응답 전용 파생값 ---------------- */

  /** 삭제 버튼 노출 여부 (작성자 본인 또는 관리자) */
  private Boolean editable;

  /**
   * JPQL 생성자 표현식 전용 생성자. (후기 + 작성자 정보)
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응합니다.</p>
   */
  public ProductReviewDTO(Long no, Long pno, Long mno, Double rating, String content,
      String optSize, String fileyn, String cdate, String nickname, String profileImg) {
    this.no = no;
    this.pno = pno;
    this.mno = mno;
    this.rating = rating;
    this.content = content;
    this.optSize = optSize;
    this.fileyn = fileyn;
    this.cdate = cdate;
    this.nickname = nickname;
    this.profileImg = profileImg;
  }

  /**
   * DTO -> Entity. (후기 등록)
   * <p>mno는 Service가 로그인 정보로 덮어씁니다. 요청 바디의 mno를 그대로 믿으면
   * 남의 이름으로 후기를 쓰고 평점을 조작할 수 있습니다.</p>
   */
  public ProductReview toEntity() {
    return ProductReview.builder()
        .no(this.no)
        .pno(this.pno)
        .mno(this.mno)
        .rating(this.rating)
        // 후기 본문은 화면에 그대로 출력되므로 저장 전에 이스케이프합니다(XSS 방어).
        .content(Tool.escapeHtml(this.content))
        .optSize(this.optSize)
        .fileyn("Y".equalsIgnoreCase(this.fileyn) ? "Y" : "N")
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /** Entity -> DTO (작성자 조인 없이 단건 변환) */
  public static ProductReviewDTO fromEntity(ProductReview entity) {
    if (entity == null) return null;
    return ProductReviewDTO.builder()
        .no(entity.getNo())
        .pno(entity.getPno())
        .mno(entity.getMno())
        .rating(entity.getRating())
        .content(entity.getContent())
        .optSize(entity.getOptSize())
        .fileyn(entity.getFileyn())
        .cdate(entity.getCdate())
        .build();
  }
}
