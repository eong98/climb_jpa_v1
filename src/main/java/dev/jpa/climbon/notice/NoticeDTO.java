package dev.jpa.climbon.notice;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 공지사항 요청/응답 DTO.
 *
 * <p>팀 프로젝트(allimio)는 요청/응답/검색조건을 내부 static 클래스로 잘게 나눴지만,
 * 이 프로젝트는 CONVENTIONS.md의 규약(<b>DTO 하나에 toEntity() / fromEntity()</b>)에 맞춰
 * 평평한 단일 DTO로 통일했습니다. 클래스 수가 줄어 읽기 쉽고,
 * 프론트의 {@code Notice.ts} 타입과 필드가 1:1로 대응됩니다.</p>
 *
 * <p>[실무 팁] 목록 응답에는 본문(content)이 필요 없습니다.
 * 공지 20건의 CLOB을 전부 실어 보내면 응답이 수백 KB가 되므로
 * {@link #fromEntityForList(Notice)}로 content를 뺀 가벼운 DTO를 따로 만듭니다.
 * {@code @JsonInclude(NON_NULL)} 덕분에 content 키 자체가 JSON에서 사라집니다.</p>
 */
@Getter
@Setter
@ToString(exclude = "content")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoticeDTO {

  /** 공지번호 (PK) */
  private Long no;

  /** 구분 (0: 일반, 1: 이벤트, 2: 점검, 3: 업데이트) */
  @Builder.Default
  private Integer type = 0;

  /** 제목 */
  private String title;

  /** 내용 (상세 조회에서만 채워집니다) */
  private String content;

  /** 작성 관리자 번호 */
  private Long mno;

  /** 상단 고정 여부 (Y/N) */
  @Builder.Default
  private String topYn = "N";

  /** 첨부파일 보유 여부 (Y/N) */
  @Builder.Default
  private String fileyn = "N";

  /** 조회수 */
  private Integer vcnt;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) */
  private String isdel;

  /** 구분 라벨 — 프론트가 매번 switch 문을 쓰지 않도록 서버에서 만들어 줍니다. */
  private String typeLabel;

  /**
   * DTO -> Entity (등록용)
   *
   * <p>조회수/삭제여부/등록일시는 클라이언트 값을 믿지 않고 서버가 정합니다.
   * XSS 방지를 위해 제목은 이스케이프 처리합니다.</p>
   *
   * <p>[실무 팁] 본문(content)은 관리자만 작성하고 에디터의 HTML 태그를 살려야 하므로
   * 여기서는 이스케이프하지 않습니다. 일반 사용자가 쓰는 게시판이라면
   * 반드시 {@code Tool.escapeHtml()}을 통과시키거나 화이트리스트 필터를 적용해야 합니다.</p>
   */
  public Notice toEntity() {
    return Notice.builder()
        .type(this.type != null ? this.type : 0)
        .title(Tool.escapeHtml(this.title))
        .content(this.content)
        .mno(this.mno)
        .topYn(this.topYn != null ? this.topYn : "N")
        .fileyn(this.fileyn != null ? this.fileyn : "N")
        .vcnt(0)
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /** Entity -> DTO (상세 조회용 — 본문 포함) */
  public static NoticeDTO fromEntity(Notice notice) {
    if (notice == null) {
      return null;
    }

    return NoticeDTO.builder()
        .no(notice.getNo())
        .type(notice.getType())
        .title(notice.getTitle())
        .content(notice.getContent())
        .mno(notice.getMno())
        .topYn(notice.getTopYn())
        .fileyn(notice.getFileyn())
        .vcnt(notice.getVcnt())
        .cdate(notice.getCdate())
        .udate(notice.getUdate())
        .isdel(notice.getIsdel())
        .typeLabel(toTypeLabel(notice.getType()))
        .build();
  }

  /** Entity -> DTO (목록 조회용 — 본문 제외로 응답 크기를 줄임) */
  public static NoticeDTO fromEntityForList(Notice notice) {
    NoticeDTO dto = fromEntity(notice);
    if (dto != null) {
      dto.setContent(null); // NON_NULL 설정으로 JSON에서 키 자체가 빠집니다
    }
    return dto;
  }

  /** 구분 코드를 사람이 읽는 라벨로 변환합니다. */
  private static String toTypeLabel(Integer type) {
    if (type == null) {
      return "일반";
    }
    return switch (type) {
      case 1 -> "이벤트";
      case 2 -> "점검";
      case 3 -> "업데이트";
      default -> "일반";
    };
  }
}
