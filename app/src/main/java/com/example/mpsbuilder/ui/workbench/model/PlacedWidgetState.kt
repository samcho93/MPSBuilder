package com.example.mpsbuilder.ui.workbench.model

import kotlinx.serialization.Serializable

@Serializable
data class IOSlot(
    val key: String,
    val label: String,
    val address: String = ""
)

@Serializable
enum class CylinderMode(val label: String) {
    SINGLE("단동"),
    DOUBLE("복동")
}

@Serializable
enum class ConveyorDriveMode(val label: String) {
    UNI("단방향"),
    BI("양방향")
}

/** 센서 타입 */
@Serializable
enum class SensorType(val label: String) {
    PROXIMITY("근접센서"),      // 금속만 감지
    PHOTO("광전센서")            // 금속+비금속 모두 감지
}

/** 스위치 타입 */
@Serializable
enum class SwitchType(val label: String) {
    PUSH("푸시"),     // 누르는 동안만 ON, 떼면 OFF
    TOGGLE("토글"),   // 누를 때마다 ON/OFF 교대
    SELECT("선택")    // 여러 접점 중 하나 선택 (ON 유지, 다시 누르면 OFF)
}

/** 경광등 단 수 */
@Serializable
enum class SignalTowerTiers(val count: Int, val label: String) {
    TWO(2, "2단"),
    THREE(3, "3단"),
    FOUR(4, "4단"),
}

@Serializable
data class PlacedWidgetState(
    val id: String,
    val widgetType: WidgetType,
    val positionX: Float,
    val positionY: Float,
    val rotationDeg: Float = 0f,
    val scaleFactor: Float = 1f,
    val ioSlots: List<IOSlot> = emptyList(),
    val label: String = "",
    val conveyorConfig: ConveyorConfig? = null,
    val linkedMotorId: String? = null,
    val zOrder: Int = 0,
    val cylinderMode: CylinderMode = CylinderMode.SINGLE,
    val conveyorDriveMode: ConveyorDriveMode = ConveyorDriveMode.UNI,
    val sensorType: SensorType = SensorType.PROXIMITY,
    val switchType: SwitchType = SwitchType.PUSH,
    val linkedCylinderId: String? = null,
    val workpieceStack: List<WorkpieceType> = emptyList(),
    val widgetColor: Long = 0,  // 0=기본색, 그 외=사용자 선택 색상 (ARGB)
    val signalTowerTiers: SignalTowerTiers = SignalTowerTiers.THREE,
    val storedWorkpieces: List<WorkpieceType> = emptyList(),  // 적재함에 담긴 공작물
    val linkedConveyorId: String? = null,  // 테이블/공급기 → 컨베이어 연결
) {
    val linkedIOAddress: String?
        get() = ioSlots.firstOrNull { it.address.isNotBlank() }?.address
}

fun cylinderIOSlots(mode: CylinderMode): List<IOSlot> = when (mode) {
    CylinderMode.SINGLE -> listOf(
        IOSlot("OUT1", "전진 출력"),
        IOSlot("IN1", "전진 LS"),
        IOSlot("IN2", "후진 LS"),
    )
    CylinderMode.DOUBLE -> listOf(
        IOSlot("OUT1", "전진 출력"),
        IOSlot("OUT2", "후진 출력"),
        IOSlot("IN1", "전진 LS"),
        IOSlot("IN2", "후진 LS"),
    )
}

fun conveyorIOSlots(mode: ConveyorDriveMode): List<IOSlot> = when (mode) {
    ConveyorDriveMode.UNI -> listOf(IOSlot("OUT1", "운전 출력"))
    ConveyorDriveMode.BI -> listOf(
        IOSlot("OUT1", "정전 출력"),
        IOSlot("OUT2", "역전 출력"),
    )
}

fun WidgetType.defaultIOSlots(): List<IOSlot> = when (this) {
    WidgetType.CYLINDER -> cylinderIOSlots(CylinderMode.SINGLE)
    WidgetType.MOTOR -> listOf(
        IOSlot("OUT1", "정전 출력"),
        IOSlot("OUT2", "역전 출력"),
    )
    WidgetType.CONVEYOR -> conveyorIOSlots(ConveyorDriveMode.UNI)
    WidgetType.LAMP -> listOf(IOSlot("OUT1", "점등 출력"))
    WidgetType.PUSH_BUTTON -> listOf(IOSlot("IN1", "접점 입력"))
    WidgetType.SENSOR -> listOf(IOSlot("IN1", "감지 입력"))
    WidgetType.VALVE -> listOf(IOSlot("OUT1", "개방 출력"))
    WidgetType.WORKPIECE_SUPPLIER -> listOf(IOSlot("IN1", "공작물 유무"))  // 공작물 있으면 ON
    WidgetType.BUZZER -> listOf(IOSlot("OUT1", "부저 출력"))
    WidgetType.STORAGE_BIN -> emptyList()  // 적재함은 IO 없음
    WidgetType.SIGNAL_TOWER -> signalTowerIOSlots(SignalTowerTiers.THREE)
    WidgetType.TABLE -> emptyList()  // 테이블은 IO 없음 — 실린더+컨베이어 연결
}

fun signalTowerIOSlots(tiers: SignalTowerTiers): List<IOSlot> {
    val colors = listOf("빨강", "노랑", "녹색", "파랑")
    return (1..tiers.count).map { i ->
        IOSlot("OUT$i", "${colors.getOrElse(i - 1) { "$i" }}등 출력")
    }
}
