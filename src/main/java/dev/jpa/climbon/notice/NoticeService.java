package dev.jpa.climbon.notice;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.attach.AttachService;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 공지사항 비즈니스 로직.
 *
 * <p>조회 전용 메서드가 대부분이라 클래스 기본값을 {@code readOnly = true}로 두고,
 * 데이터를 바꾸는 메서드에만 {@code @Transactional}을 덧붙였습니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

  private final NoticeRepository noticeRepository;

  /** 공지 삭제 시 첨부파일까지 함께 정리하기 위해 주입받습니다. */
  private final AttachService attachService;

  /** ATTACH.TNAME에 저장할 테이블명(= 업로드 폴더명) */
  private static final String TNAME = "NOTICE";

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 공지 목록 검색 + 페이징.
   *
   * <p>정렬 규칙(상단고정 우선 + 최신순)이 이미 리포지토리 쿼리에 고정되어 있으므로
   * 여기서는 <b>정렬 정보가 없는</b> Pageable을 만들어 넘깁니다.
   * Sort를 같이 넘기면 ORDER BY가 뒤에 덧붙어 고정 규칙이 흐트러집니다.</p>
   *
   * @param word 제목/내용 검색어
   * @param type 구분 (null이면 전체)
   */
  public Page<NoticeDTO> getList(String word, Integer type, int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    return noticeRepository.searchNotice(word, type, pageable)
        .map(NoticeDTO::fromEntityForList); // 목록에서는 본문(CLOB) 제외
  }

  /**
   * 메인 화면용 최신 공지 N건.
   */
  public List<NoticeDTO> getLatest(int size) {
    return noticeRepository.findLatest(PageRequest.of(0, size))
        .map(NoticeDTO::fromEntityForList)
        .getContent();
  }

  /**
   * 공지 상세 조회 (조회수 +1).
   *
   * <p>[실무 팁] 조회수는 새로고침할 때마다 올라가므로 엄밀한 통계가 아닙니다.
   * 정확도가 필요하면 IP/회원 단위로 중복 조회를 걸러야 하지만,
   * 그만큼 저장소와 로직이 복잡해지므로 공지사항 수준에서는 단순 증가로 충분합니다.</p>
   *
   * @return 공지 DTO. 없거나 삭제된 글이면 null (컨트롤러가 404로 변환)
   */
  @Transactional
  public NoticeDTO read(Long no) {
    Notice notice = noticeRepository.findActiveByNo(no).orElse(null);
    if (notice == null) {
      return null;
    }

    // DB에서 직접 +1 (동시 접속 시 조회수가 누락되지 않음)
    noticeRepository.increaseVcnt(no);

    // 벌크 UPDATE로 영속성 컨텍스트가 비워졌으므로, 응답 값을 맞추려고 메모리에서도 +1 해 둡니다.
    notice.increaseVcnt();
    return NoticeDTO.fromEntity(notice);
  }

  /* ======================================================================
   * 등록 / 수정 / 삭제 (관리자)
   * ====================================================================== */

  /**
   * 공지 등록.
   *
   * @param noticeDTO 등록할 내용
   * @param mno       작성 관리자 번호 (토큰에서 꺼낸 값)
   * @return 저장된 공지 DTO
   */
  @Transactional
  public NoticeDTO create(NoticeDTO noticeDTO, Long mno) {
    if (Tool.isEmpty(noticeDTO.getTitle())) {
      throw new IllegalArgumentException("제목을 입력해 주세요.");
    }
    if (Tool.isEmpty(noticeDTO.getContent())) {
      throw new IllegalArgumentException("내용을 입력해 주세요.");
    }

    noticeDTO.setMno(mno);
    Notice saved = noticeRepository.save(noticeDTO.toEntity());
    return NoticeDTO.fromEntity(saved);
  }

  /**
   * 공지 수정.
   *
   * <p>JPA의 변경 감지를 이용하므로 {@code save()}를 부르지 않습니다.
   * 트랜잭션이 끝나는 시점에 스냅샷과 비교해 바뀐 컬럼만 UPDATE가 나갑니다.</p>
   *
   * @return 수정된 공지 DTO. 대상이 없으면 null
   */
  @Transactional
  public NoticeDTO update(Long no, NoticeDTO noticeDTO) {
    Notice notice = noticeRepository.findActiveByNo(no).orElse(null);
    if (notice == null) {
      return null;
    }

    notice.update(
        noticeDTO.getType(),
        Tool.isEmpty(noticeDTO.getTitle()) ? null : Tool.escapeHtml(noticeDTO.getTitle()),
        noticeDTO.getContent(),
        noticeDTO.getTopYn(),
        noticeDTO.getFileyn(),
        Tool.getDate());

    return NoticeDTO.fromEntity(notice);
  }

  /**
   * 공지 삭제 (논리삭제).
   *
   * <p>글은 ISDEL='Y'로 남기지만, <b>첨부파일은 실제로 지웁니다.</b>
   * 글 자체는 복구 가치가 있어도 디스크를 차지하는 파일까지 남겨두면
   * 저장 공간이 계속 새기 때문입니다. (복구가 필요한 서비스라면
   * Attach.reserveDelete()로 유예 기간을 두는 방식으로 바꾸면 됩니다.)</p>
   *
   * @return 처리 건수 (대상이 없으면 0)
   */
  @Transactional
  public int delete(Long no) {
    Notice notice = noticeRepository.findActiveByNo(no).orElse(null);
    if (notice == null) {
      return 0;
    }

    notice.delete();
    attachService.deleteByTnameAndBno(TNAME, no);
    notice.updateFileyn("N");
    return 1;
  }
}
