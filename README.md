# MPS Builder

PLC 기반 실습장비(MPS: Modular Production System)를 가상 작업대 위에 자유 배치하고,
개별 테스트 동작 및 공작물 흐름을 시뮬레이션하는 **드래그&드롭 빌더** Android 앱입니다.

---

## 개요

- 실습장비(실린더, 모터, 컨베이어, 센서 등)를 작업대 캔버스에 자유 배치
- 각 장비에 PLC IO 주소(X/Y)를 할당하고 래더 프로그램과 연동
- 테스트 모드에서 장비를 개별 조작하거나, 래더 프로그램을 로드하여 자동 시뮬레이션
- 공작물(금속/비금속)의 투입, 이송, 감지, 적재까지 전체 공정 흐름을 시각적으로 확인
- PLCSimul JSON 및 MELSEC CSV(GX-Works2) 래더 파일 읽기 지원

---

## 주요 기능

### 장비 배치 (편집 모드)
- **11종 위젯**: 실린더, 모터, 램프, 스위치, 센서, 밸브, 컨베이어, 공급기, 부저, 적재함, 경광등
- 드래그로 위치 이동, 회전 바로 자유 회전(0~360도), 모서리 핸들로 크기 조절
- 복수 선택(탭+홀드) 및 블록 선택(빈 영역 드래그)
- 복사/잘라내기/붙여넣기, Undo/Redo
- zOrder(앞으로/뒤로) 조절

### 장비 속성
- **실린더**: 단동/복동 선택, 전진출력·후진출력·전진LS·후진LS IO 할당
- **컨베이어**: 소/중/대 크기, 단방향/양방향, 방향(상하좌우) 선택
- **센서**: 근접센서(금속만 감지) / 광전센서(전체 감지) 선택
- **스위치**: 푸시/토글/선택 타입
- **경광등**: 2단/3단/4단, 각 단 개별 IO
- **램프/스위치/부저**: 색상 선택
- **공급기**: 실린더 연결, 공작물 스택 관리

### 공작물 시스템
- 금속(은회색) / 비금속(주황색) 공작물
- 공급기에 순서대로 적재 → 연결 실린더 전진 시 투입
- 컨베이어 위에서 벨트 속도와 동기화하여 이동
- 센서 위치 통과 시 자동 감지 (근접: 금속만, 광전: 전체)
- 실린더 전진으로 공작물을 적재함으로 밀어냄

### 테스트 모드
- 위젯 클릭으로 개별 동작 (실린더 전후진, 모터 정역전, 스위치 누름 등)
- 좌측 IO 모니터 패널에서 전체 IO 상태 실시간 확인
- 래더 연동 시 자동 스캔 루프(20ms) → 래더 출력이 장비에 반영
- 부저 ON 시 1kHz 비프음 출력

### 래더 연동
- **PLCSimul JSON** 프로젝트 파일 로드
- **MELSEC CSV** (GX-Works2 내보내기) 파일 로드
- 래더 다이어그램 실시간 표시 (전류 흐름 하이라이트)
- 확대/축소 + 수직 스크롤, 수평 자동 fit
- 래더 스캔 엔진: 접점(NO/NC/상승에지/하강에지), 코일(OUT/SET/RST/PLS/PLF), 타이머, 카운터, 펑션블록(MOV/ADD/CMP 등) 지원

### 래더 편집기 (내장)
앱 내에서 풀스크린 팝업으로 래더 프로그램을 직접 편집할 수 있습니다.

- **요소 배치**: 접점 4종(NO/NC/상승에지/하강에지), 코일 5종(OUT/SET/RST/PLS/PLF), 타이머, 카운터
- **펑션블록**: 28종 명령어 팔레트 (MOV/ADD/SUB/CMP/MUL/DIV/WAND/WOR 등)
- **특수 릴레이**: SM400~SM431 (항상ON, 클록, RUN전환 등) 14종 드롭다운
- **그리드 편집**: 셀 탭으로 선택, 더블탭으로 속성 편집 (IO 주소/라벨/프리셋)
- **구조 편집**: 행 추가/삭제, 런그 추가/삭제, 수직선(OR 분기) 토글
- **다중 선택**: 여러 셀 동시 선택 후 일괄 삭제
- **Undo/Redo**: 30단계 실행취소/다시실행
- **IO 자동 채번**: 접점 배치 시 X/Y/M/T/C 주소 자동 할당
- **파일 I/O**: JSON 열기/저장, CSV 불러오기/내보내기 (GX-Works2 UTF-16LE 호환)
- **MPS Builder 연동**: [적용] 버튼으로 편집한 래더를 즉시 시뮬레이션에 반영

### 저장/불러오기
- `.mps` 파일 형식 (JSON 기반)
- 저장/다른 이름으로 저장
- 자동 저장 (앱 백그라운드 시 상태 유지)
- 레이아웃 목록 관리 (불러오기/삭제)

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| 언어 | Kotlin 2.0 |
| UI | Jetpack Compose + Canvas API |
| 아키텍처 | MVVM (ViewModel + StateFlow) |
| DI | Hilt (Dagger) |
| 직렬화 | kotlinx.serialization |
| 저장 | 내부 파일 저장소 (filesDir/mpsbuilder/) |
| 빌드 | Gradle 8.9, AGP 8.7.3, Version Catalog |
| 최소 SDK | 26 (Android 8.0) |
| 타겟 SDK | 35 (Android 15) |

---

## 프로그램 구조

```
app/src/main/java/com/example/mpsbuilder/
├── MPSBuilderApplication.kt          # Hilt Application
├── MainActivity.kt                    # @AndroidEntryPoint, Compose 진입점
│
├── data/
│   ├── ladder/                        # 래더 데이터 모델 + 시뮬레이터
│   │   ├── IOAddress.kt               #   IO 주소 (X/Y/M/T/C/D/SM)
│   │   ├── LadderElement.kt           #   래더 요소 (접점/코일/타이머/카운터/FB)
│   │   ├── LadderRung.kt              #   래더 런그 (12열 그리드)
│   │   ├── LadderProject.kt           #   프로젝트 모델 (런그 + IO라벨)
│   │   ├── LadderSimulator.kt         #   래더 스캔 엔진 (순수 함수)
│   │   ├── GxWorks2CsvImporter.kt     #   MELSEC CSV 파서
│   │   └── GxWorks2CsvExporter.kt     #   GX-Works2 CSV 내보내기
│   └── repository/
│       └── WorkbenchLayoutRepository.kt # 레이아웃 저장/불러오기
│
├── domain/usecase/
│   └── AutoNumberingUseCase.kt        # IO 주소 자동 채번
│
├── di/
│   └── WorkbenchModule.kt            # Hilt DI 모듈
│
└── ui/
    ├── theme/Theme.kt                 # Material3 테마
    ├── help/HelpScreen.kt             # 앱 내 도움말 (5탭)
    │
    ├── ladder/                        # 래더 뷰어 + 편집기
    │   ├── LadderViewerCanvas.kt      #   래더 다이어그램 뷰어 (읽기 전용)
    │   ├── LadderElementRenderer.kt   #   접점/코일/타이머 그리기
    │   ├── LadderEditorScreen.kt      #   래더 편집기 풀스크린 Dialog
    │   ├── LadderEditorViewModel.kt   #   편집 상태 관리 (Undo/Redo)
    │   ├── LadderEditorCanvas.kt      #   편집용 캔버스 (셀 선택/더블탭)
    │   ├── AddressEditDialog.kt       #   요소 속성 편집 다이얼로그
    │   └── toolbar/
    │       ├── ElementToolbar.kt      #   2단 도구바 (요소 배치/편집)
    │       └── CommandPalette.kt      #   펑션블록 28종 선택
    │
    └── workbench/                     # 작업대 빌더
        ├── WorkbenchBuilderScreen.kt  #   메인 화면 (3단 레이아웃)
        ├── WorkbenchBuilderViewModel.kt # ViewModel (상태 관리 + 시뮬레이션)
        │
        ├── canvas/                    # 캔버스
        │   ├── WorkbenchCanvas.kt     #   작업대 캔버스 (Pan/Zoom/제스처)
        │   ├── WorkbenchRenderer.kt   #   배경 격자 렌더러
        │   └── WidgetOverlay.kt       #   선택 핸들 오버레이 (회전/크기)
        │
        ├── widgets/                   # 위젯
        │   ├── WidgetRenderer.kt      #   11종 위젯 Canvas 렌더러
        │   ├── WidgetPalette.kt       #   좌측 위젯 팔레트
        │   ├── PropertyPanel.kt       #   우측 속성 패널
        │   └── ConveyorWidget.kt      #   컨베이어 벨트 애니메이션
        │
        ├── workpiece/                 # 공작물
        │   ├── WorkpieceRenderer.kt   #   공작물 렌더러
        │   └── WorkpieceSupplier.kt   #   공작물 공급 로직
        │
        ├── test/                      # 테스트 모드
        │   ├── WidgetTestPanel.kt     #   개별 위젯 테스트 BottomSheet
        │   └── IOMonitorPanel.kt      #   IO 주소 모니터 패널
        │
        └── model/                     # 데이터 모델
            ├── WidgetType.kt          #   위젯 타입 열거형 (11종)
            ├── PlacedWidgetState.kt   #   배치 위젯 상태 + IO슬롯
            ├── ConveyorConfig.kt      #   컨베이어 설정 (크기/방향)
            ├── WorkbenchLayout.kt     #   전체 레이아웃 직렬화 모델
            └── WorkpieceModel.kt      #   공작물 모델 (금속/비금속)
```

---

## 화면 레이아웃

```
┌───────────────────────────────────────────────────────────────────┐
│  TopAppBar: MPS Builder │ [테스트] [래더] [Undo][Redo] [저장] ... │
├──────────┬──────────────────────────────┬─────────────────────────┤
│ 위젯     │                              │ 속성 패널 / 래더 뷰어  │
│ 팔레트   │      작업대 캔버스            │                        │
│          │   (Pan/Zoom + 격자 배경)      │ ID: w-002              │
│ [실린더] │                              │ 타입: 컨베이어          │
│ [모터]   │  ┌─────────────────┐          │ IO: Y000               │
│ [램프]   │  │ 컨베이어 →→→→  │          │ [테스트] [삭제]        │
│ [스위치] │  └─────────────────┘          │                        │
│ [센서]   │     [CYL1] [LAMP1]           │ ── 또는 ──             │
│ [밸브]   │                              │                        │
│ [컨베이어]│                              │ 래더 다이어그램         │
│ [공급기] │                              │ ├[X000]──[M0]──(Y000)│
│ [부저]   │                              │ ├[X001]────(Y001)    │
│ [적재함] │                              │ ├[T0 K30]──(M1)     │
│ [경광등] │                              │                        │
│          │                              │ [축소][49%][확대][맞춤] │
│ 금속/비금속│                              │                        │
└──────────┴──────────────────────────────┴─────────────────────────┘
```

---

## 빌드 및 실행

```bash
# 빌드
./gradlew assembleDebug

# 설치
./gradlew installDebug
```

**요구사항**: Android Studio Hedgehog 이상, JDK 17

---

## 파일 형식

### .mps (레이아웃 저장)
```json
{
  "version": 1,
  "name": "공압 실습 라인",
  "workbenchWidthDp": 1200.0,
  "workbenchHeightDp": 800.0,
  "placedWidgets": [...],
  "workpieces": [],
  "ioLabelMap": {"X000": "기동 PB", "Y000": "컨베이어"}
}
```

### 래더 파일 지원
- **PLCSimul JSON**: `{"name":"...", "rungs":[...], "ioLabels":{...}}`
- **MELSEC CSV**: GX-Works2 내보내기 (UTF-16LE/UTF-8), 간단 CSV, 텍스트 니모닉

---

## 라이선스

이 프로젝트는 교육 및 실습 목적으로 제작되었습니다.
