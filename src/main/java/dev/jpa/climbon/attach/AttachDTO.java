package dev.jpa.climbon.attach;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 첨부파일 요청/응답 DTO.
 *
 * <p>업로드 요청({@code multipart/form-data})과 목록 응답(JSON)을 한 클래스로 처리합니다.
 * 요청에만 쓰이는 {@code files} 필드는 응답 시 null이라
 * {@code @JsonInclude(NON_NULL)} 덕분에 JSON에서 자동으로 빠집니다.</p>
 *
 * <p>[실무 팁] 프론트는 파일명만으로는 이미지를 표시할 수 없습니다.
 * 그래서 {@code purl + "/" + sname}을 미리 합친 {@code url}, {@code thumbUrl}을 만들어 내려줍니다.
 * 경로 조립 규칙이 바뀌어도 프론트 코드를 고칠 필요가 없어집니다.</p>
 */
@Getter
@Setter
@ToString(exclude = "files") // MultipartFile을 toString에 넣으면 로그가 폭발합니다
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachDTO {

  /** 첨부파일번호 (PK) */
  private Long no;

  /** 등록 테이블명(대문자) = 저장 폴더명 */
  private String tname;

  /** 등록 게시글/원글 PK 번호 */
  private Long bno;

  /** 파일 종류 (0: IMAGE, 1: FILE) */
  @Builder.Default
  private Integer type = 1;

  /** 원본 파일명 */
  private String name;

  /** 파일 크기 (BYTE) */
  @Builder.Default
  private Long fsize = 0L;

  /** 서버 저장 파일명 (UUID) */
  private String sname;

  /** 썸네일 파일명 */
  private String thumb;

  /** 상대 저장 경로 */
  private String purl;

  /** 업로드 회원번호 */
  private Long mno;

  /** 등록일시 */
  private String cdate;

  /** 삭제 예정일시 */
  private String ddate;

  /* ----------------------------------------------------------------------
   * 화면 전용 / 요청 전용 필드
   * ---------------------------------------------------------------------- */

  /** 원본 파일 접근 URL (purl + / + sname) */
  private String url;

  /** 썸네일 접근 URL (purl + / + thumb) — 썸네일이 없으면 null */
  private String thumbUrl;

  /**
   * 업로드 파일 목록 (요청 전용).
   *
   * <p>HTML의 {@code <input type="file" name="files" multiple>} 또는
   * React의 {@code formData.append("files", file)} 과 이름이 맞아야 바인딩됩니다.</p>
   */
  private List<MultipartFile> files;

  /**
   * DTO -> Entity
   *
   * <p>파일 저장이 끝난 뒤 DB에 넣을 때 사용합니다.</p>
   */
  public Attach toEntity() {
    return Attach.builder()
        .no(this.no)
        .tname(this.tname)
        .bno(this.bno)
        .type(this.type)
        .name(this.name)
        .fsize(this.fsize)
        .sname(this.sname)
        .thumb(this.thumb)
        .purl(this.purl)
        .mno(this.mno)
        .cdate(this.cdate)
        .ddate(this.ddate)
        .build();
  }

  /**
   * Entity -> DTO
   *
   * <p>화면에서 바로 쓸 수 있도록 url / thumbUrl까지 조립해 줍니다.</p>
   */
  public static AttachDTO fromEntity(Attach attach) {
    if (attach == null) {
      return null;
    }

    String purl = attach.getPurl();
    String url = (purl != null && attach.getSname() != null) ? purl + "/" + attach.getSname() : null;
    String thumbUrl = (purl != null && attach.getThumb() != null && !attach.getThumb().isBlank())
        ? purl + "/" + attach.getThumb() : null;

    return AttachDTO.builder()
        .no(attach.getNo())
        .tname(attach.getTname())
        .bno(attach.getBno())
        .type(attach.getType())
        .name(attach.getName())
        .fsize(attach.getFsize())
        .sname(attach.getSname())
        .thumb(attach.getThumb())
        .purl(purl)
        .mno(attach.getMno())
        .cdate(attach.getCdate())
        .ddate(attach.getDdate())
        .url(url)
        .thumbUrl(thumbUrl)
        .build();
  }
}
