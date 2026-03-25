# CLAUDE.md — 실습장비 빌더 (Equipment Workbench Builder)

## 프로젝트 개요

PLC 래더 편집기 앱(`CLAUDE.md` 참조)의 서브 모듈.  
실습 장비를 가상 작업대(Workbench) 위에 자유 배치하고, 개별 테스트 동작 및  
공작물(워크피스) 흐름을 시뮬레이션하는 **드래그&드롭 빌더**.  
구성은 JSON으로 저장/불러오기 가능하며 래더 편집기 시뮬레이션과 연동됨.

---

## 기술 스택

| 영역 | 채택 기술 |
|---|---|
| 언어 | Kotlin 1.9+ |
| UI | Jetpack Compose + Canvas API |
| 제스처 | `detectDragGestures`, `detectTransformGestures` |
| 애니메이션 | `Animatable`, `AnimationSpec`, `infiniteRepeatable` |
| 아키텍처 | MVVM (기존 앱과 동일) |
| 상태 관리 | StateFlow / MutableStateFlow |
| 직렬화 | kotlinx.serialization |
| 저장 | DataStore + SAF (JSON 파일 내보내기/불러오기) |

---

## 디렉토리 구조

```
ui/workbench/
├── WorkbenchBuilderScreen.kt       # 빌더 메인 화면
├── WorkbenchBuilderViewModel.kt    # 빌더 상태 관리
├── canvas/
│   ├── WorkbenchCanvas.kt          # 작업대 캔버스 (드래그/회전/확대)
│   ├── WorkbenchRenderer.kt        # 작업대 배경/격자 렌더러
│   └── WidgetOverlay.kt            # 위젯 오버레이 (선택/핸들)
├── widgets/
│   ├── PlacedWidget.kt             # 배치된 위젯 래퍼 모델
│   ├── WidgetRenderer.kt           # 위젯 공통 렌더러 (Compose Canvas)
│   ├── CylinderWidget.kt           # 실린더 (기존 SimulationObject 연동)
│   ├── LampWidget.kt
│   ├── SwitchWidget.kt
│   ├── MotorWidget.kt
│   ├── SensorWidget.kt
│   ├── ValveWidget.kt
│   └── ConveyorWidget.kt           # 컨베이어 벨트 (신규)
├── workpiece/
│   ├── WorkpieceModel.kt           # 공작물 모델 (금속/비금속)
│   ├── WorkpieceRenderer.kt        # 공작물 렌더러
│   └── WorkpieceSupplier.kt        # 공작물 공급 로직
├── test/
│   ├── WidgetTestPanel.kt          # 개별 위젯 테스트 패널
│   └── WidgetTestViewModel.kt      # 테스트 상태 관리
└── model/
    ├── WorkbenchLayout.kt          # 전체 배치 직렬화 모델
    ├── PlacedWidgetState.kt        # 배치 위젯 상태 (위치/각도/크기)
    └── WorkbenchProject.kt         # 저장/불러오기 루트 모델
```

---

## 핵심 데이터 모델

### PlacedWidget.kt

```kotlin
// 작업대에 배치된 위젯 1개를 나타내는 상태
@Serializable
data class PlacedWidgetState(
    val id: String,                         // UUID
    val widgetType: WidgetType,
    val positionX: Float,                   // 작업대 좌표계 (dp)
    val positionY: Float,
    val rotationDeg: Float = 0f,            // 0 ~ 360 자유 회전
    val scaleFactor: Float = 1f,            // 0.5 ~ 2.0
    val linkedIOAddress: String? = null,    // 연결된 IOAddress (예: "Y000")
    val label: String = "",
    val conveyorConfig: ConveyorConfig? = null,  // WidgetType.CONVEYOR 전용
    val linkedMotorId: String? = null,      // 컨베이어 ↔ 모터 연결
    val isSelected: Boolean = false,
    val zOrder: Int = 0                     // 위젯 겹침 순서
)

enum class WidgetType {
    CYLINDER,           // 단동/복동 실린더
    LAMP,               // 표시 램프
    PUSH_BUTTON,        // 푸시버튼 스위치
    MOTOR,              // 모터
    SENSOR,             // 센서 (광전/근접)
    VALVE,              // 전자 밸브
    CONVEYOR,           // 컨베이어 벨트
    WORKPIECE_SUPPLIER  // 공작물 공급기
}
```

### ConveyorConfig.kt

```kotlin
@Serializable
data class ConveyorConfig(
    val size: ConveyorSize = ConveyorSize.MEDIUM,
    val direction: ConveyorDirection = ConveyorDirection.RIGHT,
    val beltColor: Long = 0xFF555555,
    val linkedMotorId: String? = null       // 연결된 모터 위젯 ID
) {
    enum class ConveyorSize(
        val lengthDp: Float,
        val widthDp: Float,
        val label: String
    ) {
        SMALL(160f, 48f, "소"),
        MEDIUM(280f, 64f, "중"),
        LARGE(420f, 80f, "대")
    }

    enum class ConveyorDirection { LEFT, RIGHT, UP, DOWN }
}
```

### WorkpieceModel.kt

```kotlin
@Serializable
data class Workpiece(
    val id: String,
    val type: WorkpieceType,
    val positionX: Float,
    val positionY: Float,
    val onConveyorId: String? = null,       // 탑재된 컨베이어 ID
    val conveyorProgress: Float = 0f        // 0.0 ~ 1.0 (컨베이어 위 위치)
)

enum class WorkpieceType(
    val label: String,
    val color: Long,
    val shape: WorkpieceShape
) {
    METAL(    "금속",   0xFFB0BEC5, WorkpieceShape.RECTANGLE),  // 은회색 사각
    NON_METAL("비금속", 0xFFFFCC80, WorkpieceShape.RECTANGLE)   // 주황색 사각
}

enum class WorkpieceShape { RECTANGLE, CYLINDER_TOP, CIRCLE }
```

### WorkbenchLayout.kt (저장/불러오기 루트)

```kotlin
@Serializable
data class WorkbenchLayout(
    val version: Int = 1,
    val name: String = "NewLayout",
    val workbenchWidthDp: Float = 1200f,
    val workbenchHeightDp: Float = 800f,
    val placedWidgets: List<PlacedWidgetState> = emptyList(),
    val workpieces: List<Workpiece> = emptyList(),
    val ioLabelMap: Map<String, String> = emptyMap()  // 래더 편집기와 공유
)
```

---

## 작업대 캔버스 구현

### WorkbenchCanvas.kt

```kotlin
@Composable
fun WorkbenchCanvas(
    layout: WorkbenchLayout,
    selectedId: String?,
    simulationMemory: Map<String, Boolean>,  // 래더 시뮬레이터 메모리 공유
    onWidgetSelect: (String) -> Unit,
    onWidgetMove: (id: String, dx: Float, dy: Float) -> Unit,
    onWidgetRotate: (id: String, deltaDeg: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 작업대 전체 Pan/Zoom 지원
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableStateOf(1f) }

    Box(modifier = modifier
        .transformGestures(
            onPan  = { delta -> panOffset += delta },
            onZoom = { scale -> zoomScale = (zoomScale * scale).coerceIn(0.3f, 3f) }
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. 작업대 배경 + 격자
            WorkbenchRenderer.drawBackground(this, layout, panOffset, zoomScale)

            // 2. 위젯 렌더링 (zOrder 순)
            layout.placedWidgets
                .sortedBy { it.zOrder }
                .forEach { widget ->
                    withTransform({
                        val cx = (widget.positionX + panOffset.x) * zoomScale
                        val cy = (widget.positionY + panOffset.y) * zoomScale
                        translate(cx, cy)
                        rotate(widget.rotationDeg)
                        scale(widget.scaleFactor * zoomScale)
                    }) {
                        WidgetRenderer.draw(
                            scope   = this,
                            widget  = widget,
                            memory  = simulationMemory,
                            isSelected = (widget.id == selectedId)
                        )
                    }
                }

            // 3. 공작물 렌더링
            layout.workpieces.forEach { wp ->
                WorkpieceRenderer.draw(this, wp, panOffset, zoomScale)
            }
        }

        // 선택된 위젯 핸들 오버레이 (회전 핸들, 크기 조절 핸들)
        selectedId?.let { id ->
            layout.placedWidgets.find { it.id == id }?.let { widget ->
                WidgetOverlay(
                    widget    = widget,
                    panOffset = panOffset,
                    zoom      = zoomScale,
                    onRotate  = { delta -> onWidgetRotate(id, delta) },
                    onResize  = { /* scaleFactor 변경 */ }
                )
            }
        }
    }
}
```

### 위젯 오버레이 핸들 (회전/이동)

```
       ↺ [회전 핸들] ← 상단 중앙 원형 핸들 드래그로 회전
       ┌──────────────┐
       │   위젯 본체   │ ← 드래그로 위치 이동
       └──────────────┘
     ↗ [크기 핸들] ← 모서리 드래그로 scaleFactor 변경
```

---

## 컨베이어 벨트 위젯

### ConveyorWidget.kt

```kotlin
@Composable
fun ConveyorWidget(
    config: ConveyorConfig,
    isRunning: Boolean,             // 연결 모터 출력 Y ON/OFF
    isForward: Boolean = true,      // 정방향/역방향
    workpieces: List<Workpiece>,    // 컨베이어 위 공작물 목록
    modifier: Modifier = Modifier
) {
    val beltOffset = remember { Animatable(0f) }

    // 모터 운전 중 벨트 애니메이션
    LaunchedEffect(isRunning, isForward) {
        if (isRunning) {
            beltOffset.animateTo(
                targetValue = if (isForward) 1f else -1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,  // 벨트 1패턴 이동 시간
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            beltOffset.stop()
        }
    }

    Canvas(modifier = modifier.size(
        width  = config.size.lengthDp.dp,
        height = config.size.widthDp.dp
    )) {
        val w = size.width
        val h = size.height
        val stripeSpacing = 24.dp.toPx()
        val animShift = beltOffset.value * stripeSpacing

        // ── 벨트 본체 배경
        drawRoundRect(
            color       = Color(config.beltColor),
            cornerRadius = CornerRadius(h / 2)
        )

        // ── 벨트 이동 줄무늬 (대각선 스트라이프)
        clipRect {
            val stripeColor = Color.Black.copy(alpha = 0.25f)
            var x = -stripeSpacing + animShift % stripeSpacing
            while (x < w + stripeSpacing) {
                drawLine(
                    color       = stripeColor,
                    start       = Offset(x, 0f),
                    end         = Offset(x + h * 0.6f, h),
                    strokeWidth = stripeSpacing * 0.4f
                )
                x += stripeSpacing
            }
        }

        // ── 롤러 (양 끝 원형)
        val rollerR = h / 2f
        listOf(rollerR, w - rollerR).forEach { cx ->
            drawCircle(Color(0xFF424242), rollerR, Offset(cx, h / 2f))
            drawCircle(Color(0xFF757575), rollerR * 0.5f, Offset(cx, h / 2f))
        }

        // ── 크기 라벨
        drawContext.canvas.nativeCanvas.drawText(
            config.size.label,
            w / 2f, h * 0.38f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = h * 0.3f
            }
        )
    }

    // 컨베이어 위 공작물 렌더링
    workpieces.forEach { wp ->
        val wpX = wp.conveyorProgress * config.size.lengthDp.dp.value
        WorkpieceChip(wp, Modifier.offset(wpX.dp, 0.dp))
    }
}
```

**컨베이어 3단계 크기:**

| 단계 | 길이 | 폭 | 표시 |
|---|---|---|---|
| 소 (SMALL) | 160dp | 48dp | `소` |
| 중 (MEDIUM) | 280dp | 64dp | `중` |
| 대 (LARGE) | 420dp | 80dp | `대` |

---

## 공작물 (Workpiece) 공급 시스템

### WorkpieceSupplier.kt

```kotlin
// 공작물 공급기 위젯 — 탭/시뮬레이션 신호로 공작물 생성
class WorkpieceSupplier @Inject constructor() {

    /**
     * 공급기 출력 신호(Y) ON → 공작물 1개 생성
     * 생성된 공작물은 공급기 바로 아래 컨베이어 또는 작업대에 배치
     */
    fun supply(
        type: WorkpieceType,
        supplierId: String,
        targetConveyorId: String?,
        currentWorkpieces: List<Workpiece>
    ): Workpiece {
        return Workpiece(
            id               = UUID.randomUUID().toString(),
            type             = type,
            positionX        = 0f,
            positionY        = 0f,
            onConveyorId     = targetConveyorId,
            conveyorProgress = 0f
        )
    }
}
```

### 공작물 종류

| 종류 | 색상 | 형태 | 센서 반응 |
|---|---|---|---|
| 금속 (METAL) | 은회색 `#B0BEC5` | 사각형 | 근접센서 O, 광전센서 O |
| 비금속 (NON_METAL) | 주황색 `#FFCC80` | 사각형 | 근접센서 X, 광전센서 O |

> 센서 위젯 타입(`SensorType`)에 따라 공작물 종류별 감지 여부 자동 판별

---

## 개별 위젯 테스트 패널

```kotlin
// WidgetTestPanel.kt — 위젯 선택 후 하단 테스트 패널 표시
@Composable
fun WidgetTestPanel(
    widget: PlacedWidgetState,
    simulationMemory: Map<String, Boolean>,
    onForceOutput: (address: String, value: Boolean) -> Unit,
    onClose: () -> Unit
) {
    BottomSheetScaffold(...) {
        Column {
            // ── 위젯 정보
            Row {
                Text("${widget.widgetType.name}  │  ${widget.label}")
                Text("IO: ${widget.linkedIOAddress ?: "미연결"}")
            }

            Divider()

            // ── 타입별 테스트 UI
            when (widget.widgetType) {

                WidgetType.CYLINDER -> CylinderTestUI(
                    outputAddr   = widget.linkedIOAddress,
                    currentState = simulationMemory[widget.linkedIOAddress] ?: false,
                    onForce      = onForceOutput
                )

                WidgetType.LAMP -> LampTestUI(
                    outputAddr   = widget.linkedIOAddress,
                    currentState = simulationMemory[widget.linkedIOAddress] ?: false,
                    onForce      = onForceOutput
                )

                WidgetType.PUSH_BUTTON -> SwitchTestUI(
                    inputAddr    = widget.linkedIOAddress,
                    currentState = simulationMemory[widget.linkedIOAddress] ?: false,
                    onForce      = onForceOutput
                )

                WidgetType.MOTOR -> MotorTestUI(
                    outputAddr   = widget.linkedIOAddress,
                    currentState = simulationMemory[widget.linkedIOAddress] ?: false,
                    onForce      = onForceOutput
                )

                WidgetType.CONVEYOR -> ConveyorTestUI(
                    config       = widget.conveyorConfig!!,
                    linkedMotor  = widget.linkedMotorId,
                    isRunning    = simulationMemory[widget.linkedIOAddress] ?: false,
                    onForce      = onForceOutput
                )

                WidgetType.SENSOR -> SensorTestUI(
                    inputAddr    = widget.linkedIOAddress,
                    currentState = simulationMemory[widget.linkedIOAddress] ?: false,
                    onForce      = onForceOutput
                )

                else -> {}
            }

            // ── 동작 모션 미리보기 (Canvas 미니 뷰)
            Text("동작 미리보기", style = MaterialTheme.typography.labelMedium)
            WidgetMotionPreview(widget, simulationMemory)
        }
    }
}
```

**테스트 패널 기능:**

| 위젯 | 테스트 조작 | 확인 가능 동작 |
|---|---|---|
| 실린더 | [전진] / [후진] 버튼 | 피스톤 로드 이동 애니메이션 |
| 램프 | [ON] / [OFF] 토글 | 램프 점등/소등 색상 변화 |
| 스위치 | [누름] / [해제] | 접점 신호 ON/OFF |
| 모터 | [기동] / [정지] / [역전] | 회전 애니메이션 |
| 컨베이어 | [정방향] / [역방향] / [정지] | 벨트 스트라이프 이동 |
| 센서 | [감지 ON] / [감지 OFF] | 출력 신호 변화 |
| 밸브 | [개방] / [폐쇄] | 밸브 개폐 아이콘 |

---

## ViewModel

### WorkbenchBuilderViewModel.kt

```kotlin
@HiltViewModel
class WorkbenchBuilderViewModel @Inject constructor(
    private val workpieceSupplier: WorkpieceSupplier,
    private val layoutRepository: WorkbenchLayoutRepository
) : ViewModel() {

    private val _layout     = MutableStateFlow(WorkbenchLayout())
    val layout: StateFlow<WorkbenchLayout> = _layout.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _workpieces = MutableStateFlow<List<Workpiece>>(emptyList())
    val workpieces: StateFlow<List<Workpiece>> = _workpieces.asStateFlow()

    // 래더 시뮬레이터 메모리 공유 (SimulationViewModel에서 collect)
    var simulationMemory: StateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    // ── 위젯 추가 / 삭제
    fun addWidget(type: WidgetType, x: Float = 100f, y: Float = 100f) {
        val newWidget = PlacedWidgetState(
            id          = UUID.randomUUID().toString(),
            widgetType  = type,
            positionX   = x,
            positionY   = y,
            zOrder      = _layout.value.placedWidgets.size
        )
        _layout.update { it.copy(placedWidgets = it.placedWidgets + newWidget) }
    }

    fun removeWidget(id: String) {
        _layout.update { it.copy(placedWidgets = it.placedWidgets.filter { w -> w.id != id }) }
    }

    // ── 위치 / 회전 / 크기
    fun moveWidget(id: String, dx: Float, dy: Float) {
        _layout.update {
            it.copy(placedWidgets = it.placedWidgets.map { w ->
                if (w.id == id) w.copy(positionX = w.positionX + dx, positionY = w.positionY + dy)
                else w
            })
        }
    }

    fun rotateWidget(id: String, deltaDeg: Float) {
        _layout.update {
            it.copy(placedWidgets = it.placedWidgets.map { w ->
                if (w.id == id) w.copy(rotationDeg = (w.rotationDeg + deltaDeg) % 360f)
                else w
            })
        }
    }

    fun scaleWidget(id: String, factor: Float) {
        _layout.update {
            it.copy(placedWidgets = it.placedWidgets.map { w ->
                if (w.id == id) w.copy(scaleFactor = (w.scaleFactor * factor).coerceIn(0.5f, 2.0f))
                else w
            })
        }
    }

    // ── 컨베이어 ↔ 모터 연결
    fun linkConveyorToMotor(conveyorId: String, motorId: String) {
        _layout.update {
            it.copy(placedWidgets = it.placedWidgets.map { w ->
                if (w.id == conveyorId) w.copy(linkedMotorId = motorId) else w
            })
        }
    }

    // ── 공작물 공급
    fun supplyWorkpiece(type: WorkpieceType, supplierId: String) {
        val supplier = _layout.value.placedWidgets.find { it.id == supplierId }
        val wp = workpieceSupplier.supply(
            type             = type,
            supplierId       = supplierId,
            targetConveyorId = supplier?.linkedMotorId,  // 공급기 바로 연결된 컨베이어
            currentWorkpieces = _workpieces.value
        )
        _workpieces.update { it + wp }
    }

    // ── 컨베이어 위 공작물 이동 (시뮬레이션 틱마다 호출)
    fun tickConveyors() {
        val memory = simulationMemory.value
        _workpieces.update { wps ->
            wps.map { wp ->
                val conveyor = _layout.value.placedWidgets
                    .find { it.id == wp.onConveyorId && it.widgetType == WidgetType.CONVEYOR }
                if (conveyor != null) {
                    val motorAddr = conveyor.linkedIOAddress
                    val isRunning = memory[motorAddr] ?: false
                    val delta     = if (isRunning) 0.01f else 0f  // 1틱당 이동량
                    wp.copy(conveyorProgress = (wp.conveyorProgress + delta).coerceIn(0f, 1f))
                } else wp
            }.filter { it.conveyorProgress < 1f }  // 끝에 도달하면 제거
        }
    }

    // ── 저장 / 불러오기
    fun saveLayout(name: String) = viewModelScope.launch {
        layoutRepository.save(_layout.value.copy(name = name))
    }

    fun loadLayout(name: String) = viewModelScope.launch {
        layoutRepository.load(name)?.let { _layout.value = it }
    }

    fun exportLayoutJson(): String =
        Json.encodeToString(WorkbenchLayout.serializer(), _layout.value)

    fun importLayoutJson(json: String) {
        _layout.value = Json.decodeFromString(WorkbenchLayout.serializer(), json)
    }
}
```

---

## 위젯 팔레트 (추가 UI)

```kotlin
// 좌측 드로어 또는 하단 시트 — 위젯 드래그 앤 드롭으로 작업대에 추가
@Composable
fun WidgetPalette(
    onAddWidget: (WidgetType) -> Unit
) {
    val items = listOf(
        WidgetType.CYLINDER        to "실린더",
        WidgetType.MOTOR           to "모터",
        WidgetType.LAMP            to "램프",
        WidgetType.PUSH_BUTTON     to "스위치",
        WidgetType.SENSOR          to "센서",
        WidgetType.VALVE           to "밸브",
        WidgetType.CONVEYOR        to "컨베이어",
        WidgetType.WORKPIECE_SUPPLIER to "공급기",
    )
    LazyColumn {
        items(items) { (type, label) ->
            PaletteItem(
                label    = label,
                icon     = widgetIcon(type),
                onClick  = { onAddWidget(type) }  // 탭으로 작업대 중앙에 추가
                // TODO: 드래그로 작업대 원하는 위치에 직접 놓기
            )
        }
    }
}
```

---

## 작업대 저장/불러오기

### JSON 파일 포맷 예시

```json
{
  "version": 1,
  "name": "공압 실습 라인 1",
  "workbenchWidthDp": 1200.0,
  "workbenchHeightDp": 800.0,
  "placedWidgets": [
    {
      "id": "w-001",
      "widgetType": "PUSH_BUTTON",
      "positionX": 80.0,
      "positionY": 200.0,
      "rotationDeg": 0.0,
      "scaleFactor": 1.0,
      "linkedIOAddress": "X000",
      "label": "기동 PB"
    },
    {
      "id": "w-002",
      "widgetType": "CONVEYOR",
      "positionX": 200.0,
      "positionY": 300.0,
      "rotationDeg": 0.0,
      "scaleFactor": 1.0,
      "linkedIOAddress": "Y000",
      "label": "컨베이어1",
      "conveyorConfig": {
        "size": "MEDIUM",
        "direction": "RIGHT",
        "linkedMotorId": "w-003"
      }
    },
    {
      "id": "w-003",
      "widgetType": "MOTOR",
      "positionX": 530.0,
      "positionY": 310.0,
      "rotationDeg": 0.0,
      "scaleFactor": 1.0,
      "linkedIOAddress": "Y001",
      "label": "컨베이어 모터"
    }
  ],
  "workpieces": [],
  "ioLabelMap": {
    "X000": "기동 PB",
    "Y000": "컨베이어1",
    "Y001": "컨베이어 모터"
  }
}
```

### 저장/불러오기 구현

```kotlin
// WorkbenchLayoutRepository.kt
class WorkbenchLayoutRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkbenchLayoutRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun save(layout: WorkbenchLayout) {
        val dir = context.filesDir.resolve("workbench").also { it.mkdirs() }
        dir.resolve("${layout.name}.json")
           .writeText(json.encodeToString(layout))
    }

    override suspend fun load(name: String): WorkbenchLayout? =
        context.filesDir.resolve("workbench/$name.json")
            .takeIf { it.exists() }
            ?.let { json.decodeFromString(it.readText()) }

    override suspend fun listLayouts(): List<String> =
        context.filesDir.resolve("workbench")
            .listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
}
```

### 래더 편집기에서 불러오기 연동

```kotlin
// LadderEditorViewModel.kt 에 추가
fun importWorkbenchLayout(layout: WorkbenchLayout) {
    // IO 레이블 동기화
    layout.ioLabelMap.forEach { (addr, label) ->
        _ioLabels.update { it + (addr to label) }
    }
    // 시뮬레이션 오브젝트 자동 생성
    layout.placedWidgets.forEach { widget ->
        val ioAddr = widget.linkedIOAddress ?: return@forEach
        simViewModel.registerFromWorkbench(widget, ioAddr)
    }
}
```

---

## 화면 레이아웃

```
┌─────────────────────────────────────────────────────────────────────┐
│  TopAppBar: 실습장비 빌더 │ [저장] [불러오기] [래더 편집기로]         │
├────────────┬────────────────────────────────────────┬───────────────┤
│ 위젯       │                                        │ 속성 패널     │
│ 팔레트     │          작업대 캔버스                  │ (선택 시 표시)│
│            │     (Pan/Zoom + 격자 배경)              │               │
│ [실린더]   │                                        │ ID: w-002     │
│ [모터]     │    ┌─────────────────────────┐          │ 타입: 컨베이어│
│ [램프]     │    │   컨베이어 (중)  →→→→  │          │ 크기: 중      │
│ [스위치]   │    └─────────────────────────┘          │ 각도: 0°     │
│ [센서]     │         ↑                               │ IO: Y000     │
│ [밸브]     │    [모터]  연결됨                        │ 레이블: 컨베이어1│
│ [컨베이어] │                                        │               │
│ [공급기]   │    [PB1]  [CYL1]  [LAMP1]              │ [테스트 동작] │
│            │                                        │ [연결 설정]   │
│ ────────   │    [공급기] → 금속 ● 비금속 ○           │ [삭제]        │
│ [공작물    │                                        │               │
│  공급]     │                                        │               │
│ ● 금속     │                                        │               │
│ ○ 비금속   │                                        │               │
└────────────┴────────────────────────────────────────┴───────────────┘
│  테스트 패널 (위젯 선택 후 하단 BottomSheet)                         │
│  CYL1 — [전진▶] [◀후진]  │  현재: 후진  │  IO: Y002  │ [강제출력]   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 구현 우선순위

```
Phase 1 — 기본 빌더
  ☐ WorkbenchCanvas (격자 배경 + Pan/Zoom)
  ☐ 위젯 팔레트에서 탭으로 추가
  ☐ 드래그로 위치 이동
  ☐ 회전 핸들 (0~360° 자유 회전)
  ☐ 크기 조절 핸들 (0.5x ~ 2.0x)
  ☐ zOrder (위젯 앞/뒤 순서)

Phase 2 — 컨베이어 + 공작물
  ☐ ConveyorWidget (소/중/대 + 벨트 스트라이프 애니메이션)
  ☐ 모터 위젯 ↔ 컨베이어 연결 UI
  ☐ WorkpieceSupplier 위젯 (금속/비금속 공급)
  ☐ 공작물 컨베이어 위 이동 (conveyorProgress)
  ☐ 근접센서: 금속만 감지 / 광전센서: 전체 감지

Phase 3 — 테스트 동작
  ☐ WidgetTestPanel (BottomSheet)
  ☐ 위젯별 강제 출력 (IO 강제 ON/OFF)
  ☐ 동작 모션 미리보기 (실린더 전후진, 모터 회전 등)
  ☐ 래더 시뮬레이터 메모리와 연동

Phase 4 — 저장/불러오기 연동
  ☐ WorkbenchLayout JSON 직렬화 저장
  ☐ SAF 기반 JSON 내보내기/가져오기
  ☐ 래더 편집기 IO 레이블 자동 동기화
  ☐ 래더 편집기 → 빌더 불러오기 메뉴
  ☐ 레이아웃 목록 관리 화면
```

---

## 래더 편집기 앱과의 연동 요약

| 항목 | 방향 | 내용 |
|---|---|---|
| IO 레이블 | 빌더 → 래더 | 위젯 추가 시 IO 주소·레이블 자동 등록 |
| 시뮬레이션 메모리 | 래더 → 빌더 | `SimulationViewModel.memory` StateFlow 공유 |
| 공작물 공급 신호 | 빌더 → 래더 | 공급기 출력 Y 주소 ON 시 공작물 생성 |
| 컨베이어 운전 | 래더 → 빌더 | 연결 모터 Y 주소 ON 시 벨트 애니메이션 |
| 레이아웃 파일 | 파일 공유 | `WorkbenchLayout.json` 앱 내 공통 저장소 |

---

## 참고 사항

- 작업대 좌표계는 **dp 단위** (디스플레이 해상도 독립)
- 회전은 `withTransform { rotate(deg) }` Compose Canvas 변환 사용
- 컨베이어 벨트 애니메이션: `infiniteRepeatable + LinearEasing` (모터 ON 동안 무한 반복)
- 공작물 이동: 시뮬레이션 20ms 틱마다 `tickConveyors()` 호출
- 태블릿 가로 모드 3단 레이아웃 (팔레트 | 캔버스 | 속성 패널) 권장
- 위젯 복수 선택(다중 선택) → Phase 4 이후 고려
