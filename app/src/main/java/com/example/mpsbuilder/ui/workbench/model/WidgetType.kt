package com.example.mpsbuilder.ui.workbench.model

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetType(val label: String) {
    CYLINDER("실린더"),
    LAMP("램프"),
    PUSH_BUTTON("스위치"),
    MOTOR("모터"),
    SENSOR("센서"),
    VALVE("밸브"),
    CONVEYOR("컨베이어"),
    WORKPIECE_SUPPLIER("공급기"),
    BUZZER("부저"),
    STORAGE_BIN("적재함"),
    SIGNAL_TOWER("경광등"),
    TABLE("테이블"),
}
