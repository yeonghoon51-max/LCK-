# LCK 라이브 📱⌚

응원하는 LCK 팀의 경기를 **폰과 갤럭시워치**에서 실시간으로 확인하는 안드로이드 앱 (Java)

## 기능
| 어디서 | 기능 |
|---|---|
| 📱 폰 앱 | 응원 팀 선택 (LCK 팀 목록), 경기 화면, **경기 알림** |
| ⌚ 워치 앱 | 경기 화면, **타일** (시계 화면 옆으로 넘기기) |
| 🔔 알림 | 시작 10분 전 · 경기 시작 · 세트 승/패 · 경기 종료 → 폰 알림이 워치로 자동 전달 |

- 경기 중에는 세트 스코어와 킬, 골드 차이, 타워/용/바론까지 보여줘요.
- 폰에서 팀을 바꾸면 워치에도 자동으로 적용돼요 (Wearable Data Layer).
- LCK 팀이 아니어도 월즈, MSI 같은 국제대회 경기도 같이 잡혀요.

## 프로젝트 구조
```
LckLive/
├── shared/   순수 자바 — 폰·워치 공용 (안드로이드 코드 없음, JUnit 테스트)
│   ├── data/LolEsportsApi    API 호출 + JSON 파싱, LCK 팀 목록
│   ├── alert/AlertDecider    이전 상태 vs 지금 상태 → 어떤 알림 보낼지
│   ├── sync/TeamSyncContract 폰 → 워치 팀 전송 약속(경로, 키)
│   └── util/Formats          시간·골드 표시 형식
├── mobile/   📱 폰 앱
│   ├── ui/MainActivity          응원 팀 + 경기 카드
│   ├── ui/TeamPickerActivity    팀 고르기
│   ├── notify/MatchCheckWorker  백그라운드 확인 (경기 중 2분 간격)
│   ├── notify/MatchNotifier     알림 띄우기
│   └── sync/WatchSync           워치로 팀 보내기
└── wear/     ⌚ 워치 앱
    ├── ui/MainActivity                 경기 화면
    ├── tile/TeamTileService            타일
    └── sync/TeamSyncListenerService    폰에서 팀 받기
```

## 실행 방법
1. Android Studio → **Open** → `LckLive` 폴더 → Gradle 동기화
2. 위쪽 실행 설정에서 모듈을 골라 실행
   - **mobile** → 폰 (실제 폰 USB/무선 디버깅, 또는 폰 에뮬레이터)
   - **wear** → 갤럭시워치 또는 Wear OS 에뮬레이터
3. 폰 앱에서 **변경** → 응원 팀 선택 → 워치에도 자동 적용

> 폰↔워치 동기화는 **두 앱의 applicationId 가 같고, 같은 PC(같은 디버그 키)로 설치**해야 동작해요.

### 테스트
`shared/src/test` 의 JUnit 테스트는 안드로이드 기기 없이 PC에서 바로 돌아가요.
Android Studio에서 `shared/src/test/java` 우클릭 → **Run 'Tests in ...'**

## ⚠️ 알아둘 점
- **비공식 API**: lolesports.com 웹사이트가 내부적으로 쓰는 API예요. 라이엇이 바꾸면 고장날 수 있고, 개인 학습용으로만 쓰세요.
- **실시간 스탯은 약 1분 늦어요** (livestats 피드 특성).
- 워치에 알림이 안 오면 폰의 **Galaxy Wearable 앱 → 알림**에서 LCK 라이브가 켜져 있는지 확인하세요.
