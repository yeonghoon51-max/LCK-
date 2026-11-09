# LCK 라이브 ⌚

응원하는 LCK 팀의 경기를 **갤럭시워치 타일**에서 실시간으로 확인하는 Wear OS 앱 (Java)

## 기능
| 어디서 | 기능 |
|---|---|
| 앱 화면 | **응원 팀 고르기** (LCK 팀 목록) |
| 타일 (시계 화면 옆으로 넘기기) | 다음 경기 · 지난 경기 결과 · **경기 중 세트 스코어, 킬, 골드** (탭하면 새로고침) |
| 알림 (진동) | 시작 10분 전 · 경기 시작 · 세트 승/패 · 경기 종료 |

- 월즈, MSI 같은 라이엇 국제대회 경기도 같이 잡혀요 (응원 팀이 나오는 경기면 다).
- 폰 앱 없이 워치 혼자 동작해요.

## 프로젝트 구조
```
LckLive/
├── shared/   순수 자바 (안드로이드 코드 없음, JUnit 테스트)
│   ├── data/LolEsportsApi    API 호출 + JSON 파싱, LCK 팀 목록
│   ├── alert/AlertDecider    이전 상태 vs 지금 상태 → 어떤 알림 보낼지
│   └── util/Formats          시간·골드 표시 형식
└── wear/     워치 앱
    ├── ui/MainActivity          응원 팀 고르기
    ├── tile/TeamTileService     타일 (protolayout)
    ├── notify/MatchCheckWorker  백그라운드 확인 (경기 중 2분 간격)
    ├── notify/MatchNotifier     알림 띄우기
    └── settings/TeamPrefs       응원 팀 저장
```

## 실행 방법
1. Android Studio → **Open** → `LckLive` 폴더 → Gradle 동기화
2. 실행 설정 **wear** + 기기(갤럭시워치 또는 Wear OS 에뮬레이터) → ▶ Run
3. 앱에서 응원 팀 선택 → 시계 화면을 왼쪽으로 넘기고 길게 누르기 → **+** → **LCK 라이브** 타일 추가

### 갤럭시워치에 설치
1. `설정 → 시계 정보 → 소프트웨어 정보 → 소프트웨어 버전` 7번 탭 → 개발자 모드
2. `개발자 옵션 → ADB 디버깅`, `무선 디버깅` 켜기 (PC와 같은 Wi-Fi, 2.4GHz)
3. Android Studio `Device Manager → Pair Devices Using Wi-Fi` → ▶ Run
4. 설치 후 무선 디버깅은 끄기

### 테스트
`shared/src/test` 의 JUnit 테스트는 기기 없이 PC에서 바로 돌아가요.
Android Studio에서 `shared/src/test/java` 우클릭 → **Run 'Tests in ...'**

## ⚠️ 알아둘 점
- **비공식 API**: lolesports.com 웹사이트가 내부적으로 쓰는 API예요. 라이엇이 바꾸면 고장날 수 있고, 개인 학습용으로만 쓰세요.
- **실시간 스탯은 약 1분 늦어요** (livestats 피드 특성).
- 배터리 절약 모드에서는 알림이 몇 분 늦을 수 있어요.
