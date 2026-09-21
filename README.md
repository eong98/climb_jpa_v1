# climb_jpa_v1 — CLIMB:ON 백엔드 (Spring Boot)

Java 21 · Spring Boot 3.5.15 · Spring Data JPA · Spring Security + JWT · Oracle

## 실행

```bash
./gradlew bootRun        # 또는 IDE에서 ClimbonApplication 실행
# http://localhost:9200
```

실행 전 `src/main/resources/application.properties`에서 확인할 값:

| 설정 | 기본값 | 설명 |
|---|---|---|
| `spring.datasource.url` | `jdbc:oracle:thin:@localhost:1521:XE` | DB 접속 주소 |
| `spring.datasource.username/password` | `climbon` / `climbon1234` | DB 계정 |
| `climbon.upload.dir` | `C:/kd/deploy/climbon/` | 첨부파일 저장 루트 (OS에 맞게 수정) |
| `jwt.secret` | (개발용 문자열) | 운영에서는 환경변수로 주입 |
| `climbon.ai.base-url` | `http://localhost:11300` | FastAPI AI 서버 주소 |

> `spring.jpa.hibernate.ddl-auto=none` 입니다. 테이블은 `db/schema.sql`로 직접 만듭니다.
> (Entity가 운영 테이블을 덮어쓰는 사고를 막기 위한 실무 기본값)

## 패키지 구조

```
dev.jpa.climbon
├── ClimbonApplication.java     진입점 (업로드 경로 주입)
├── config/    SecurityConfig · WebMvcConfiguration · RestClientConfig
├── jwt/       JwtTokenProvider · JwtAuthenticationFilter · SecurityUtil · TokenCont
├── tool/      Tool(공통 유틸·난이도 정규화) · PageResponse · Download
├── member/    회원
├── attach/    공통 첨부파일 (모든 게시판이 공유)
├── notice/    공지사항
├── gym/       암장  ├─ hour/     요일별 영업시간
│                    ├─ grade/    난이도 구성  ★ 핵심
│                    ├─ review/   리뷰
│                    └─ favorite/ 찜
├── climblog/  등반일지 + 통계
├── board/     커뮤니티  ├─ comment/  댓글·대댓글
│                        └─ like/     좋아요
├── product/   상품  └─ review/ 상품 후기
├── cart/      장바구니
├── order/     주문 (ORDERS 테이블)
└── ai/        FastAPI 프록시 + AI 대화 로그
```

### 클래스 네이밍 규칙
| 역할 | 예시 |
|---|---|
| Entity | `Gym` |
| DTO | `GymDTO` (`toEntity()` / `static fromEntity()`) |
| Repository | `GymRepository extends JpaRepository<Gym, Long>` |
| Service | `GymService` (`@Transactional`) |
| Controller | `GymCont` (`@RestController`) ← **Cont 접미사** |

## API 명세
`../CONVENTIONS.md` 3장 참고. 응답은 목록이면 `PageResponse<T>`, 단건이면 `XxxDTO`입니다.

## 인증
- `Authorization: Bearer <accessToken>` 헤더
- 액세스 토큰 30분 / 리프레시 토큰 2주 (`MEMBER_REFRESH_TOKEN` 테이블에 화이트리스트 보관)
- `POST /auth/reissue`로 재발급. 재발급 때마다 기존 토큰을 폐기하는 **회전(RTR)** 방식
- 컨트롤러에서 로그인 회원번호는 `SecurityUtil.getMemberNo()`로 얻습니다
  (요청 파라미터로 받으면 남의 번호를 넣는 IDOR 취약점이 생기므로)

## 핵심 로직: 난이도 정규화
`Tool.toSortOrder(system, code)` — V등급 / YDS(5.10a) / French(6b+) / 색상 난이도를
0~100 점수로 변환합니다. 프론트(`utils/Tool.ts`)에도 **동일한 규칙**이 구현되어 있지만,
DB에 저장되는 값은 항상 서버 계산값입니다(클라이언트 값을 신뢰하지 않음).
