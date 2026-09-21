package dev.jpa.climbon.board;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 커뮤니티 게시글 Repository.
 *
 * <p><b>[면접 포인트] 목록 조회를 전부 DTO 생성자 표현식으로 하는 이유</b><br>
 * 1) 작성자 정보를 얻기 위한 추가 쿼리(N+1)를 원천 차단합니다. (BoardDTO 주석 참고)<br>
 * 2) 목록에 필요 없는 <b>CLOB(content)을 읽지 않습니다.</b> Oracle에서 CLOB은
 *    LOB 로케이터를 따로 읽어오는 비용이 있어 20건짜리 목록에서도 무시할 수 없습니다.<br>
 * 3) 엔티티가 영속성 컨텍스트에 올라가지 않으므로 스냅샷 메모리와 flush 시 dirty check 비용이 없습니다.</p>
 *
 * <p><b>[실무 팁] 파라미터 IS NULL 관용구</b><br>
 * 조건이 선택 항목이면 {@code (:param IS NULL OR 컬럼 = :param)} 으로 무시 가능하게 만듭니다.
 * 문자열은 {@code :word IS NULL OR :word = ''} 처럼 <b>빈 문자열까지</b> 검사해야 합니다.
 * 프론트에서 검색어를 지우면 null이 아니라 ""가 오는 경우가 많고,
 * 그때 {@code LIKE '%%'}가 되어 인덱스를 못 타는 전체 스캔이 되기 때문입니다.</p>
 */
public interface BoardRepository extends JpaRepository<Board, Long> {

  /** 살아 있는 게시글 단건 조회 (수정/삭제/좋아요 전 검증용) */
  Optional<Board> findByNoAndIsdel(Long no, String isdel);

  /**
   * 게시글 목록 검색 (페이징).
   *
   * <p><b>[면접 포인트] 공지 상단 고정과 정렬을 왜 ORDER BY 안의 CASE로 처리했나?</b><br>
   * "공지는 항상 맨 위, 그 아래는 최신순/조회순/좋아요순" 이라는 요구를 만족시키는 방법은 세 가지입니다.
   * <ul>
   *   <li>(a) 공지 목록과 일반 목록을 따로 조회해 자바에서 합치기 → 페이징 계산이 어긋납니다.
   *       (2페이지에도 공지가 또 나오거나, 한 페이지 건수가 들쭉날쭉해집니다.)</li>
   *   <li>(b) {@code Pageable}의 Sort로 넘기기 → {@code CASE WHEN} 같은 식은 Sort로 표현할 수 없고
   *       {@code JpaSort.unsafe()}를 쓰면 쿼리 별칭에 의존하는 문자열이 서비스로 새어 나갑니다.</li>
   *   <li>(c) <b>ORDER BY를 쿼리 안에 고정하고, 정렬 기준만 파라미터로 받기</b> ← 채택</li>
   * </ul>
   * (c)는 DB가 정렬까지 끝낸 뒤 페이징하므로 결과가 항상 정확하고,
   * 정렬 옵션이 늘어도 쿼리를 복사하지 않아도 됩니다.</p>
   *
   * <p>{@code CASE WHEN :sort = 'view' THEN b.vcnt ELSE 0 END DESC} 는
   * sort가 'view'가 아니면 모든 행이 0이 되어 <b>정렬에 영향을 주지 않는</b> 관용구입니다.
   * (:sort가 null이면 비교 결과가 UNKNOWN이라 자연스럽게 ELSE로 떨어집니다.)
   * 타입이 다른 컬럼을 한 CASE에 섞으면 Hibernate가 타입을 결정하지 못하므로
   * <b>정렬 기준마다 CASE를 하나씩</b> 따로 두었습니다.</p>
   *
   * @param type       게시판 구분 (null이면 전체)
   * @param word       검색어
   * @param searchType title | content | writer (null이면 제목+내용)
   * @param sido       지역 태그 필터
   * @param sort       new(기본) | view | like
   */
  @Query(value = """
      SELECT new dev.jpa.climbon.board.BoardDTO(
             b.no, b.type, b.mno, b.title,
             b.gno, b.sido, b.meetDate, b.dealPrice, b.dealStatus,
             b.vcnt, b.likeCnt, b.replyCnt, b.fileyn, b.noticeYn,
             b.status, b.cdate, b.udate,
             m.nickname, m.profileImg, m.boulderLevel, g.gname)
      FROM Board b
      LEFT JOIN Member m ON m.no = b.mno
      LEFT JOIN Gym g ON g.no = b.gno
      WHERE b.isdel = 'N'
        AND b.status = 1
        AND (:type IS NULL OR b.type = :type)
        AND (:sido IS NULL OR :sido = '' OR b.sido = :sido)
        AND (:word IS NULL OR :word = ''
             OR (:searchType = 'title'   AND b.title      LIKE CONCAT('%', :word, '%'))
             OR (:searchType = 'content' AND b.content    LIKE CONCAT('%', :word, '%'))
             OR (:searchType = 'writer'  AND m.nickname   LIKE CONCAT('%', :word, '%'))
             OR ((:searchType IS NULL OR :searchType = '')
                 AND (b.title LIKE CONCAT('%', :word, '%')
                      OR b.content LIKE CONCAT('%', :word, '%'))))
      ORDER BY CASE WHEN b.noticeYn = 'Y' THEN 0 ELSE 1 END ASC,
               CASE WHEN :sort = 'view' THEN b.vcnt    ELSE 0 END DESC,
               CASE WHEN :sort = 'like' THEN b.likeCnt ELSE 0 END DESC,
               b.no DESC
      """,
      countQuery = """
      SELECT COUNT(b.no)
      FROM Board b
      LEFT JOIN Member m ON m.no = b.mno
      WHERE b.isdel = 'N'
        AND b.status = 1
        AND (:type IS NULL OR b.type = :type)
        AND (:sido IS NULL OR :sido = '' OR b.sido = :sido)
        AND (:word IS NULL OR :word = ''
             OR (:searchType = 'title'   AND b.title    LIKE CONCAT('%', :word, '%'))
             OR (:searchType = 'content' AND b.content  LIKE CONCAT('%', :word, '%'))
             OR (:searchType = 'writer'  AND m.nickname LIKE CONCAT('%', :word, '%'))
             OR ((:searchType IS NULL OR :searchType = '')
                 AND (b.title LIKE CONCAT('%', :word, '%')
                      OR b.content LIKE CONCAT('%', :word, '%'))))
      """)
  Page<BoardDTO> searchBoards(
      @Param("type") Integer type,
      @Param("word") String word,
      @Param("searchType") String searchType,
      @Param("sido") String sido,
      @Param("sort") String sort,
      Pageable pageable);

  /**
   * 게시글 상세 — 작성자 + 암장명 조인. (content 포함)
   */
  @Query("""
      SELECT new dev.jpa.climbon.board.BoardDTO(
             b.no, b.type, b.mno, b.title, b.content,
             b.gno, b.sido, b.meetDate, b.dealPrice, b.dealStatus,
             b.vcnt, b.likeCnt, b.replyCnt, b.fileyn, b.noticeYn,
             b.status, b.cdate, b.udate,
             m.nickname, m.profileImg, m.boulderLevel, g.gname)
      FROM Board b
      LEFT JOIN Member m ON m.no = b.mno
      LEFT JOIN Gym g ON g.no = b.gno
      WHERE b.no = :no
        AND b.isdel = 'N'
      """)
  Optional<BoardDTO> findBoardDetail(@Param("no") Long no);

  /**
   * 인기글 — 최근 N일 내 작성된 글을 (조회수 + 좋아요 가중치) 기준으로 정렬합니다.
   *
   * <p><b>왜 좋아요에 가중치(×5)를 주는가?</b><br>
   * 조회수는 스쳐 지나간 클릭까지 포함되지만 좋아요는 <b>의도적인 행동</b>입니다.
   * 조회수만으로 줄을 세우면 자극적인 제목의 글이 상위를 독점하므로
   * "읽은 사람 중 몇 명이 좋아했는가"를 반영하도록 좋아요에 가중치를 둡니다.
   * (가중치 5는 운영하며 조정할 수 있는 정책값이라 쿼리에 고정하지 않고 상수로 뽑아도 됩니다.)</p>
   *
   * <p>기간 조건이 문자열 비교인 이유: CDATE는 고정폭 'yyyy-MM-dd HH:mm:ss' 문자열이라
   * 사전식 비교가 곧 날짜 비교입니다. {@code TO_DATE(CDATE, ...)} 로 감싸면
   * 컬럼에 함수가 씌워져 인덱스를 못 쓰게 됩니다.</p>
   *
   * @param fromDate {@code Tool.getDateBefore(7)} 결과 ('yyyy-MM-dd')
   */
  @Query("""
      SELECT new dev.jpa.climbon.board.BoardDTO(
             b.no, b.type, b.mno, b.title,
             b.gno, b.sido, b.meetDate, b.dealPrice, b.dealStatus,
             b.vcnt, b.likeCnt, b.replyCnt, b.fileyn, b.noticeYn,
             b.status, b.cdate, b.udate,
             m.nickname, m.profileImg, m.boulderLevel, g.gname)
      FROM Board b
      LEFT JOIN Member m ON m.no = b.mno
      LEFT JOIN Gym g ON g.no = b.gno
      WHERE b.isdel = 'N'
        AND b.status = 1
        AND b.noticeYn = 'N'
        AND b.cdate >= :fromDate
      ORDER BY (b.vcnt + b.likeCnt * 5) DESC, b.no DESC
      """)
  List<BoardDTO> findPopularBoards(@Param("fromDate") String fromDate, Pageable pageable);

  /**
   * 조회수 +1 (벌크 UPDATE).
   *
   * <p><b>[면접 포인트] 왜 엔티티 변경감지가 아니라 벌크 UPDATE인가?</b><br>
   * 엔티티를 읽어 {@code board.setVcnt(board.getVcnt() + 1)} 로 올리면
   * <b>읽은 시점의 값 + 1</b>로 UPDATE가 나갑니다. 두 사용자가 동시에 들어오면
   * 둘 다 100을 읽고 둘 다 101로 덮어써서 조회수가 1만 오릅니다(lost update).
   * {@code SET vcnt = vcnt + 1} 은 <b>DB가 현재 값 기준으로 증가</b>시키므로
   * 동시성 문제가 없고, SELECT 없이 UPDATE 한 방이라 더 빠릅니다.</p>
   *
   * <p>{@code clearAutomatically = true} 를 준 이유: 벌크 연산은 영속성 컨텍스트를 <b>우회</b>해
   * DB만 바꿉니다. 같은 트랜잭션에 이미 로딩된 Board 엔티티가 있으면 그 엔티티의 vcnt는
   * 옛날 값이라 화면에 1 적게 표시되거나, flush 때 옛 값으로 되돌려 쓸 수 있습니다.
   * 컨텍스트를 비워 이후 조회가 DB를 다시 읽게 만듭니다.
   * (그래서 이 프로젝트는 <b>조회수 증가 → 상세 조회</b> 순서로 호출합니다.)</p>
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Board b SET b.vcnt = b.vcnt + 1 WHERE b.no = :no AND b.isdel = 'N'")
  int increaseVcnt(@Param("no") Long no);
}
