package dev.jpa.climbon.tool;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 첨부파일 다운로드 컨트롤러.
 *
 * <p>정적 리소스 매핑(WebMvcConfiguration)은 "브라우저에서 열기"용이고,
 * 이 컨트롤러는 "원본 파일명으로 저장하기"용입니다.
 * Content-Disposition: attachment 헤더를 붙여 다운로드 창을 띄웁니다.</p>
 *
 * <pre>
 *  GET /download?dir=BOARD/files&filename=abc123.pdf&downname=등반계획서.pdf
 * </pre>
 */
@Controller
public class Download {

  /**
   * @param dir      저장 폴더 (예: BOARD/files)
   * @param filename 서버에 저장된 실제 파일명 (UUID)
   * @param downname 사용자가 받게 될 원본 파일명 (한글 가능)
   */
  @GetMapping("/download")
  public ResponseEntity<Resource> download(
      @RequestParam(name = "dir", defaultValue = "") String dir,
      @RequestParam(name = "filename", defaultValue = "") String filename,
      @RequestParam(name = "downname", defaultValue = "") String downname) {

    // 경로 조작 공격(../../etc/passwd) 차단
    if (filename.contains("..") || dir.contains("..")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    }

    File file = new File(Tool.getServerDir(dir) + filename);
    if (!file.exists()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }

    // 한글 파일명이 깨지지 않도록 UTF-8 인코딩 (공백은 %20으로)
    String encoded = URLEncoder.encode(
        downname.isBlank() ? filename : downname, StandardCharsets.UTF_8
    ).replaceAll("\\+", "%20");

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new FileSystemResource(file));
  }
}
