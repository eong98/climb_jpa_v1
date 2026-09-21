package dev.jpa.climbon.attach;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 첨부파일 업로드/조회/삭제 비즈니스 로직.
 *
 * <p><b>저장 구조</b> (팀 프로젝트 AttachService의 방식을 그대로 따릅니다)</p>
 * <pre>
 *  이미지 : C:/kd/deploy/climbon/BOARD/images/{UUID}.jpg   (+ 썸네일 t_{UUID}.jpg)
 *  일반   : C:/kd/deploy/climbon/BOARD/files/{UUID}.pdf
 *  URL    : /attach/storage/BOARD/images/{UUID}.jpg
 * </pre>
 *
 * <p>[면접 포인트] <b>파일을 DB(BLOB)가 아니라 디스크에 저장하는 이유</b><br>
 * 1) DB 용량이 폭증하면 백업/복구 시간이 급격히 늘어납니다.<br>
 * 2) 이미지 전송은 웹서버(정적 리소스)가 처리하는 편이 훨씬 빠르고 캐시도 잘 먹습니다.<br>
 * 3) 나중에 S3 같은 오브젝트 스토리지로 옮기기도 쉽습니다.<br>
 * 대신 "DB 행은 있는데 파일이 없는" 불일치가 생길 수 있어 삭제 순서를 신경 써야 합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachService {

  private final AttachRepository attachRepository;

  /** 썸네일 파일명 접두사. 원본과 같은 폴더에 두므로 이름으로 구분합니다. */
  private static final String THUMB_PREFIX = "t_";

  /** 썸네일 가로 크기(px). 목록에서 원본을 그대로 쓰면 트래픽이 수십 배로 늘어납니다. */
  private static final int THUMB_WIDTH = 300;

  /* ======================================================================
   * 업로드
   * ====================================================================== */

  /**
   * 다중 파일 업로드 + DB 등록.
   *
   * <p>처리 순서: 파일 종류 판별 → 폴더 결정 → UUID 파일명으로 저장 →
   * (이미지면) 썸네일 생성 → ATTACH 행 저장</p>
   *
   * <p>[실무 팁] 썸네일 생성이 실패해도 <b>업로드 자체는 성공</b>으로 둡니다.
   * 썸네일은 보기 편하라고 만드는 부가 기능인데, 특수한 포맷(CMYK JPEG, 손상된 파일) 때문에
   * 전체 업로드를 실패시키면 사용자 입장에서는 이유를 알 수 없는 오류가 됩니다.</p>
   *
   * @param tname 등록 테이블명(대문자). 저장 폴더명으로 그대로 쓰입니다.
   * @param bno   원글 PK
   * @param mno   업로드한 회원번호 (비로그인이면 null)
   * @param files 업로드된 파일 목록
   * @return 저장된 첨부 DTO 목록
   */
  @Transactional
  public List<AttachDTO> saveAttachFiles(String tname, Long bno, Long mno, List<MultipartFile> files) {
    List<AttachDTO> resultList = new ArrayList<>();

    if (files == null || files.isEmpty() || Tool.isEmpty(tname) || bno == null) {
      return resultList;
    }

    // 경로 조작(../) 차단 — tname이 그대로 폴더명이 되므로 반드시 검증합니다.
    if (tname.contains("..") || tname.contains("/") || tname.contains("\\")) {
      throw new IllegalArgumentException("올바르지 않은 테이블명입니다: " + tname);
    }
    String folder = tname.toUpperCase();

    for (MultipartFile mf : files) {
      if (mf == null || mf.isEmpty()) {
        continue; // 빈 input이 섞여 들어오는 경우가 흔하므로 건너뜁니다
      }

      String name = mf.getOriginalFilename(); // 원본 파일명
      long fsize = mf.getSize();              // 파일 크기(byte)
      if (name == null || fsize <= 0) {
        continue;
      }

      boolean isImage = Tool.isImage(name);
      int type = isImage ? 0 : 1;

      // 이미지와 일반 파일을 폴더로 나눠 두면 썸네일 배치 처리나 용량 산정이 쉬워집니다.
      String subDir = isImage ? "/images" : "/files";
      String serverDir = Tool.getServerDir(folder + subDir); // 없으면 폴더까지 만들어 줌
      String purl = "/attach/storage/" + folder + subDir;

      // UUID 파일명으로 저장 (한글/공백/중복/덮어쓰기 문제를 한 번에 해결)
      String sname = Tool.getSaveFilename(name);
      String thumb = null;

      try {
        File target = new File(serverDir + sname);
        mf.transferTo(target);

        if (isImage) {
          thumb = createThumbnail(target, serverDir, sname);
        }
      } catch (Exception e) {
        // 한 파일이 실패해도 나머지 파일은 계속 처리합니다.
        log.error("[ATTACH] 파일 저장 실패: {} ({})", name, e.getMessage());
        continue;
      }

      AttachDTO dto = AttachDTO.builder()
          .tname(folder)
          .bno(bno)
          .type(type)
          .name(name)
          .fsize(fsize)
          .sname(sname)
          .thumb(thumb)
          .purl(purl)
          .mno(mno)
          .cdate(Tool.getDate())
          .build();

      Attach saved = attachRepository.save(dto.toEntity());
      resultList.add(AttachDTO.fromEntity(saved));
    }

    return resultList;
  }

  /**
   * 이미지 썸네일을 만듭니다. (가로 {@value #THUMB_WIDTH}px 기준 비율 유지 축소)
   *
   * <p>외부 라이브러리 없이 JDK 표준 {@code java.awt} + {@code ImageIO}만 사용합니다.
   * 세로 길이는 원본 비율에 맞춰 계산해 이미지가 찌그러지지 않게 합니다.</p>
   *
   * <p>[실무 팁] {@code SCALE_SMOOTH}는 품질이 좋지만 느립니다.
   * 업로드가 잦은 서비스라면 큐에 넣어 비동기로 만들거나 imgscalr 같은 라이브러리를 쓰세요.</p>
   *
   * @return 생성된 썸네일 파일명. 실패하면 null (호출부는 업로드를 계속 진행)
   */
  private String createThumbnail(File original, String serverDir, String sname) {
    try {
      BufferedImage src = ImageIO.read(original);
      if (src == null) {
        // webp 등 ImageIO가 기본 지원하지 않는 포맷
        log.warn("[ATTACH] 썸네일 생성 불가(지원하지 않는 이미지 포맷): {}", sname);
        return null;
      }

      // 원본이 이미 작으면 굳이 썸네일을 만들지 않고 원본을 그대로 씁니다.
      if (src.getWidth() <= THUMB_WIDTH) {
        return null;
      }

      int width = THUMB_WIDTH;
      int height = (int) Math.round(src.getHeight() * (THUMB_WIDTH / (double) src.getWidth()));

      Image scaled = src.getScaledInstance(width, height, Image.SCALE_SMOOTH);

      // PNG 투명도가 검게 변하지 않도록 TYPE_INT_RGB 대신 ARGB로 캔버스를 만듭니다.
      BufferedImage thumbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = thumbImage.createGraphics();
      g2d.drawImage(scaled, 0, 0, null);
      g2d.dispose(); // 네이티브 리소스 해제 — 빼먹으면 메모리 누수로 이어집니다

      String ext = Tool.getExtension(sname);
      // jpg는 알파 채널을 지원하지 않아 저장이 실패하므로 썸네일은 png로 통일합니다.
      String thumbName = THUMB_PREFIX + sname.replace("." + ext, "") + ".png";

      ImageIO.write(thumbImage, "png", new File(serverDir + thumbName));
      return thumbName;

    } catch (Exception e) {
      log.warn("[ATTACH] 썸네일 생성 실패(업로드는 정상 처리): {} - {}", sname, e.getMessage());
      return null;
    }
  }

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 원글의 첨부 목록 조회. — GET /attach/list/{tname}/{bno}
   */
  public List<AttachDTO> getAttachList(String tname, Long bno) {
    if (Tool.isEmpty(tname) || bno == null) {
      return List.of();
    }
    return attachRepository.findByTnameAndBnoOrderByTypeAscNoAsc(tname.toUpperCase(), bno)
        .stream()
        .map(AttachDTO::fromEntity)
        .toList();
  }

  /** 단건 조회. 없으면 null */
  public AttachDTO getAttach(Long no) {
    if (no == null) {
      return null;
    }
    return attachRepository.findById(no).map(AttachDTO::fromEntity).orElse(null);
  }

  /** 관리자 검색 + 페이징 */
  public Page<AttachDTO> searchAttach(String word, String tname, Integer type, Pageable pageable) {
    String upperTname = Tool.isEmpty(tname) ? null : tname.toUpperCase();
    return attachRepository.searchAttach(word, upperTname, type, pageable)
        .map(AttachDTO::fromEntity);
  }

  /* ======================================================================
   * 삭제
   * ====================================================================== */

  /**
   * 단건 삭제 (DB 행 + 실물 파일 + 썸네일).
   *
   * <p>[면접 포인트] <b>파일과 DB 중 무엇을 먼저 지워야 할까?</b><br>
   * 파일을 먼저 지우고 DB를 지우면, DB 삭제가 실패했을 때
   * "목록에는 보이는데 열면 깨지는" 유령 데이터가 남습니다.
   * 반대로 DB를 먼저 지우면 최악의 경우 디스크에 고아 파일이 남을 뿐
   * 사용자 화면은 정상입니다. 그래서 <b>DB 먼저, 파일 나중</b>이 안전합니다.</p>
   *
   * @return 처리 건수 (없으면 0)
   */
  @Transactional
  public int deleteAttach(Long no) {
    if (no == null) {
      return 0;
    }

    Attach attach = attachRepository.findById(no).orElse(null);
    if (attach == null) {
      return 0;
    }

    attachRepository.delete(attach);
    deletePhysicalFile(attach);
    return 1;
  }

  /**
   * 원글의 첨부 일괄 삭제. (게시글 삭제 시 함께 호출)
   *
   * <p>ATTACH에는 FK가 없어 DB가 자동으로 정리해 주지 않습니다.
   * 원글을 지우는 서비스가 이 메서드를 반드시 호출해야 고아 데이터가 남지 않습니다.</p>
   *
   * @return 삭제된 첨부 개수
   */
  @Transactional
  public int deleteByTnameAndBno(String tname, Long bno) {
    if (Tool.isEmpty(tname) || bno == null) {
      return 0;
    }

    String folder = tname.toUpperCase();
    List<Attach> list = attachRepository.findByTnameAndBnoOrderByTypeAscNoAsc(folder, bno);
    if (list.isEmpty()) {
      return 0;
    }

    attachRepository.deleteByTnameAndBno(folder, bno);
    for (Attach attach : list) {
      deletePhysicalFile(attach);
    }
    return list.size();
  }

  /**
   * 디스크의 실물 파일과 썸네일을 지웁니다.
   *
   * <p>파일이 이미 없더라도 예외를 던지지 않습니다.
   * "지우려는 파일이 없다"는 것은 이미 목표가 달성된 상태이기 때문입니다.</p>
   */
  private void deletePhysicalFile(Attach attach) {
    if (attach.getSname() == null || attach.getPurl() == null) {
      return;
    }

    // purl(/attach/storage/BOARD/images) -> 실제 폴더(BOARD/images)
    String dir = attach.getPurl().replace("/attach/storage/", "");
    String serverDir = Tool.getServerDir(dir);

    try {
      File file = new File(serverDir + attach.getSname());
      if (file.exists() && !file.delete()) {
        log.warn("[ATTACH] 파일 삭제 실패: {}", file.getAbsolutePath());
      }

      if (attach.getThumb() != null && !attach.getThumb().isBlank()) {
        File thumb = new File(serverDir + attach.getThumb());
        if (thumb.exists() && !thumb.delete()) {
          log.warn("[ATTACH] 썸네일 삭제 실패: {}", thumb.getAbsolutePath());
        }
      }
    } catch (Exception e) {
      log.error("[ATTACH] 파일 삭제 중 오류: {}", e.getMessage());
    }
  }
}
