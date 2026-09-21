package dev.jpa.climbon.gym.favorite;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.gym.Gym;
import dev.jpa.climbon.gym.GymDTO;
import dev.jpa.climbon.gym.GymRepository;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 암장 찜 서비스.
 *
 * <p>찜은 "있으면 지우고 없으면 넣는" 토글 하나로 동작합니다.
 * 등록/삭제 API를 따로 두면 프론트가 현재 상태를 정확히 알아야만 올바른 API를 고를 수 있고,
 * 상태가 어긋나면 409/404가 나서 UX가 나빠집니다.
 * <b>서버가 현재 상태를 보고 뒤집어 주는 편</b>이 훨씬 견고합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymFavoriteService {

  private final GymFavoriteRepository gymFavoriteRepository;
  private final GymRepository gymRepository;

  /**
   * 찜 토글.
   *
   * @return {@link GymFavoriteDTO} — favorite(토글 후 상태), count(해당 암장 총 찜 수)
   */
  @Transactional
  public GymFavoriteDTO toggleFavorite(Long gno) {
    Long mno = requireLogin();

    Gym gym = gymRepository.findByNoAndIsdel(gno, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 암장입니다. gno=" + gno));

    Optional<GymFavorite> exists = gymFavoriteRepository.findByGnoAndMno(gno, mno);

    boolean favorite;
    if (exists.isPresent()) {
      gymFavoriteRepository.delete(exists.get()); // 찜 취소 (물리 삭제)
      favorite = false;
    } else {
      gymFavoriteRepository.save(GymFavorite.builder()
          .gno(gno)
          .mno(mno)
          .cdate(Tool.getDate())
          .build());
      favorite = true;
    }

    // GYM.FAVORITE_CNT 동기화.
    // favoriteCnt +-1 로 계산하지 않고 COUNT로 다시 세는 이유는 리뷰 통계와 같습니다.
    // 증감식은 한 번만 어긋나도 오차가 영구히 남습니다.
    long count = gymFavoriteRepository.countByGno(gno);
    gym.applyFavoriteCnt(count);

    return GymFavoriteDTO.builder()
        .gno(gno)
        .mno(mno)
        .favorite(favorite)
        .count((int) count)
        .build();
  }

  /**
   * 내 찜 목록 (암장 정보 형태로 반환).
   */
  public Page<GymDTO> getMyFavorites(Pageable pageable) {
    Long mno = requireLogin();
    Page<Gym> gyms = gymFavoriteRepository.findMyFavoriteGyms(mno, pageable);

    // 내 찜 목록이므로 favorite은 전부 true입니다. (프론트에서 하트를 채워 그릴 수 있게 명시)
    return gyms.map(gym -> {
      GymDTO dto = GymDTO.fromEntityForList(gym);
      dto.setFavorite(true);
      return dto;
    });
  }

  /**
   * 목록에 뜬 암장들에 대한 내 찜 여부를 Set으로 돌려줍니다.
   *
   * <p>비로그인이면 빈 Set을 반환하고, 호출부는 favorite 필드를 null로 남겨
   * JSON에서 아예 빠지게 합니다.</p>
   */
  public Set<Long> getMyFavoriteGnoSet(List<Long> gnoList) {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null || gnoList == null || gnoList.isEmpty()) {
      return Collections.emptySet();
    }
    return new HashSet<>(gymFavoriteRepository.findFavoriteGnoList(mno, gnoList));
  }

  /** 단일 암장에 대한 내 찜 여부 (상세 화면용). 비로그인이면 false. */
  public boolean isFavorite(Long gno) {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) return false;
    return gymFavoriteRepository.findByGnoAndMno(gno, mno).isPresent();
  }

  /** 로그인 회원번호를 얻고, 비로그인이면 예외를 던집니다. */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new IllegalStateException("로그인이 필요합니다.");
    }
    return mno;
  }
}
