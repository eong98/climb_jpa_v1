package dev.jpa.climbon.attach;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 첨부파일 REST 컨트롤러. (CONVENTIONS.md의 /attach 명세 그대로 구현)
 *
 * <table border="1">
 *   <caption>API 목록</caption>
 *   <tr><td>GET</td><td>/attach/list/{tname}/{bno}</td><td>원글의 첨부 목록</td></tr>
 *   <tr><td>POST</td><td>/attach/create</td><td>multipart 업로드</td></tr>
 *   <tr><td>DELETE</td><td>/attach/{no}</td><td>단건 삭제</td></tr>
 *   <tr><td>DELETE</td><td>/attach/{tname}/{bno}</td><td>원글 첨부 일괄 삭제</td></tr>
 *   <tr><td>GET</td><td>/attach/list/admin</td><td>관리자 검색</td></tr>
 * </table>
 *
 * <p>[실무 팁] {@code /attach/list/admin}(2단)과 {@code /attach/list/{tname}/{bno}}(3단)는
 * 경로 <b>깊이가 달라</b> 서로 충돌하지 않습니다.
 * 만약 깊이가 같았다면 구체적인 경로가 먼저 매칭되도록 순서를 신경 써야 합니다.</p>
 */
@RestController
@RequestMapping("/attach")
@RequiredArgsConstructor
public class AttachCont {

  private final AttachService attachService;

  /**
   * 원글의 첨부 목록 — GET /attach/list/BOARD/10
   *
   * <p>비로그인도 볼 수 있어야 하므로 SecurityConfig에서 GET만 permitAll 되어 있습니다.</p>
   */
  @GetMapping("/list/{tname}/{bno}")
  public ResponseEntity<List<AttachDTO>> list(
      @PathVariable("tname") String tname,
      @PathVariable("bno") Long bno) {

    return ResponseEntity.ok(attachService.getAttachList(tname, bno));
  }

  /**
   * 다중 파일 업로드 — POST /attach/create
   *
   * <p>요청 형식은 {@code multipart/form-data}이며 {@code tname}, {@code bno}, {@code files}를 보냅니다.
   * JSON이 아니라 multipart인 이유는 파일 바이너리를 그대로 실어 보내야 하기 때문입니다.
   * (JSON에 담으려면 Base64로 인코딩해야 해서 용량이 약 33% 늘어납니다.)</p>
   *
   * <p>업로더 회원번호는 요청 값이 아니라 <b>토큰에서</b> 꺼냅니다.
   * 요청 파라미터로 받으면 남의 번호로 올린 것처럼 위조할 수 있습니다.</p>
   */
  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> create(
      @RequestParam("tname") String tname,
      @RequestParam("bno") Long bno,
      @RequestParam(name = "files", required = false) List<MultipartFile> files) {

    try {
      Long mno = SecurityUtil.getMemberNo();
      List<AttachDTO> saved = attachService.saveAttachFiles(tname, bno, mno, files);
      return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * 단건 첨부 조회 — GET /attach/read/{no}
   *
   * <p>명세에는 없지만 관리자 화면에서 상세를 확인할 때 쓰도록 함께 제공합니다.</p>
   */
  @GetMapping("/read/{no}")
  public ResponseEntity<AttachDTO> read(@PathVariable("no") Long no) {
    AttachDTO dto = attachService.getAttach(no);
    if (dto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(dto);
  }

  /**
   * 관리자 첨부 검색 — GET /attach/list/admin?word=&tname=&type=&page=0&size=10
   */
  @GetMapping("/list/admin")
  public ResponseEntity<PageResponse<AttachDTO>> listAdmin(
      @RequestParam(name = "word", defaultValue = "") String word,
      @RequestParam(name = "tname", defaultValue = "") String tname,
      @RequestParam(name = "type", required = false) Integer type,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    if (!SecurityUtil.isAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Pageable pageable = PageRequest.of(page, size, Sort.by("no").descending());
    Page<AttachDTO> result = attachService.searchAttach(word, tname, type, pageable);
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 단건 삭제 — DELETE /attach/{no}
   *
   * @return 처리 건수 (0이면 이미 없는 파일)
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<Integer> delete(@PathVariable("no") Long no) {
    return ResponseEntity.ok(attachService.deleteAttach(no));
  }

  /**
   * 원글 첨부 일괄 삭제 — DELETE /attach/BOARD/10
   *
   * <p>게시글을 지울 때 프론트가 직접 부르거나, 백엔드의 게시글 서비스가
   * {@link AttachService#deleteByTnameAndBno(String, Long)}를 호출합니다.</p>
   *
   * @return 삭제된 첨부 개수
   */
  @DeleteMapping("/{tname}/{bno}")
  public ResponseEntity<Integer> deleteByBno(
      @PathVariable("tname") String tname,
      @PathVariable("bno") Long bno) {

    return ResponseEntity.ok(attachService.deleteByTnameAndBno(tname, bno));
  }
}
