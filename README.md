# 여긴얼마? (PriceHere)

여행지에서 "이거 얼마지?"를 바로 답하는 안드로이드 환율 앱입니다.
미국 달러 · 유로 · 체코 코루나를 원화로 환산합니다.

<img src="docs/screenshot-main.png" width="260" alt="환산 화면"> <img src="docs/screenshot-widget.png" width="260" alt="홈 화면 위젯">

## 기능

- **실시간 환율** — 하나은행 매매기준율을 고시회차마다 조회합니다.
- **3단 폴백** — 하나은행 → ECB 기준환율 → 마지막으로 받아둔 캐시. 비행기 모드에서도 계산이 멈추지 않습니다.
- **신선도 표시** — 고시 시각을 초 단위로 보여줘 은행 화면과 직접 대조할 수 있습니다.
- **결제 수단별 실부담** — 매매기준율 / 카드 결제 / 현찰 구입 추정치를 전환합니다.
- **세금 환급 계산** — 체코 · 스페인 부가세 환급 예상액과 최소 구매 금액 안내.
- **저장 목록** — 금액에 메모를 붙여 두고, 나중에 최신 환율로 다시 확인합니다. 저장 시점과의 차이도 함께 보여줍니다.
- **홈 화면 위젯 3종** — 4×2 환율 보기, 4×3 빠른 계산, 4×5 키패드 계산기.

## 기술

Kotlin · Jetpack Compose (Material 3) · AppWidget(RemoteViews)

의존성을 의도적으로 최소화했습니다. 네트워크는 `HttpURLConnection`, JSON은 안드로이드 내장 `org.json`,
저장은 `SharedPreferences`를 씁니다. Retrofit · OkHttp · Room · Hilt를 쓰지 않아
릴리스 APK가 약 1.2MB입니다. 권한은 `INTERNET` 하나뿐입니다.

위젯의 키패드는 `RemoteViews`가 `EditText`를 지원하지 않기 때문에,
버튼 16개와 `PendingIntent` 브로드캐스트로 입력을 구현했습니다.

## 빌드

```bash
./gradlew assembleDebug
```

릴리스 빌드는 서명 키가 필요합니다. `keystore.properties.example`을 `keystore.properties`로
복사해 값을 채우면 `assembleRelease`가 자동으로 서명합니다.
`keystore.properties`와 `*.jks`는 `.gitignore`에 등록되어 있어 커밋되지 않습니다.

```bash
./gradlew assembleRelease
```

## 환율 데이터 출처

매매기준율은 하나은행 고시 환율이며 네이버 마켓인덱스를 통해 조회합니다.
보조 환율은 유럽중앙은행(ECB) 기준환율을 [Frankfurter API](https://frankfurter.dev)로 받아옵니다.
두 출처 모두 이 앱과 제휴 관계가 없으며, 상표권은 각 권리자에게 있습니다.
표시되는 환율은 참고용이며, 실제 환전·결제 금액은 은행과 카드사의 고시 환율 및 수수료에 따라 달라집니다.

## 라이선스

[MIT](LICENSE) © 2026 JM

다른 프로젝트는 [프로젝트 대시보드](https://coldbrewventi.vercel.app)에 정리해 두었습니다.
