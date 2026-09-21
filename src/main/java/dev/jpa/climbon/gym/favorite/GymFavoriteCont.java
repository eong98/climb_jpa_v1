package dev.jpa.climbon.gym.favorite;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jpa.climbon.gym.GymDTO;
import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 암장 찜 컨트롤러. — {@code /favorite}
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class GymFavoriteCont {

  private final GymFavoriteService gymFavoriteService;

  /**
   * 찜 토글.
   * <pre>POST /favorite/{gno}</pre>
   *
   * @return {@code {favorite: boolean, count: number}} — API 명세서에 고정된 응답 형태입니다.
   *         프론트는 이 값으로 하트 아이콘과 찜 수를 곧바로 다시 그립니다.
   *         (토글 후 목록을 다시 조회할 필요가 없어 화면이 즉각 반응합니다.)
   */
  @PostMapping("/{gno}")
  public ResponseEntity<Map<String, Object>> toggleFavorite(@PathVariable("gno") Long gno) {
    GymFavoriteDTO result = gymFavoriteService.toggleFavorite(gno);

    Map<String, Object> body = new HashMap<>();
    body.put("favorite", result.getFavorite());
    body.put("count", result.getCount());
    return ResponseEntity.ok(body);
  }

  /**
   * 내 찜 목록.
   * <pre>GET /favorite/my?page=0&amp;size=12</pre>
   *
   * <p>암장 카드를 그대로 그릴 수 있도록 {@link GymDTO} 형태로 내려줍니다.</p>
   */
  @GetMapping("/my")
  public ResponseEntity<PageResponse<GymDTO>> getMyFavorites(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "12") int size) {

    Page<GymDTO> result = gymFavoriteService.getMyFavorites(PageRequest.of(page, size));
    return ResponseEntity.ok(PageResponse.of(result));
  }
}
