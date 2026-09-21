package dev.jpa.climbon.tool;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 페이징 응답 공통 포맷.
 *
 * <p>Spring Data의 {@code Page<T>}를 그대로 JSON으로 내보내면 pageable, sort 등
 * 프론트에서 쓰지 않는 필드가 잔뜩 붙고 구조도 버전에 따라 바뀝니다.
 * 그래서 필요한 5개 값만 담은 공통 DTO로 감싸서 내려줍니다.</p>
 *
 * <p>프론트(React)의 {@code PageResponse<T>} 타입과 1:1로 대응됩니다.</p>
 */
@Getter
@Setter
@AllArgsConstructor
public class PageResponse<T> {

  /** 현재 페이지의 데이터 목록 */
  private List<T> content;

  /** 현재 페이지 번호 (0부터 시작) */
  private int page;

  /** 한 페이지당 데이터 개수 */
  private int size;

  /** 조건에 맞는 전체 데이터 수 */
  private long totalElements;

  /** 전체 페이지 수 */
  private int totalPages;

  /** Spring Data의 Page<T>를 PageResponse<T>로 변환합니다. */
  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages()
    );
  }

  /** 빈 결과를 만들 때 사용합니다. */
  public static <T> PageResponse<T> empty(int size) {
    return new PageResponse<>(List.of(), 0, size, 0L, 0);
  }
}
