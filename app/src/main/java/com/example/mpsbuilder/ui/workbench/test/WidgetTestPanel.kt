package com.example.mpsbuilder.ui.workbench.test

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.mpsbuilder.ui.workbench.model.IOSlot
import com.example.mpsbuilder.ui.workbench.model.PlacedWidgetState
import com.example.mpsbuilder.ui.workbench.model.WidgetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetTestPanel(
    widget: PlacedWidgetState,
    simulationMemory: Map<String, Boolean>,
    onForceOutput: (address: String, value: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val slots = widget.ioSlots
    val hasAnyAddr = slots.any { it.address.isNotBlank() }
    // 첫 출력 슬롯의 상태
    val out1Addr = slots.find { it.key == "OUT1" }?.address?.takeIf { it.isNotBlank() }
    val out1On = out1Addr?.let { simulationMemory[it] } ?: false

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${widget.widgetType.label}  │  ${widget.label}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // IO 슬롯 상태 표시
            slots.forEach { slot ->
                val addr = slot.address.takeIf { it.isNotBlank() }
                val state = addr?.let { simulationMemory[it] } ?: false
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${slot.label}: ${addr ?: "미연결"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (addr == null) "—" else if (state) "ON" else "OFF",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state) Color(0xFF4CAF50) else Color.Gray
                    )
                }
            }

            HorizontalDivider()

            if (!hasAnyAddr) {
                Text(
                    "IO 주소를 먼저 연결하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // 타입별 테스트 버튼
                when (widget.widgetType) {
                    WidgetType.CYLINDER -> CylinderTestUI(slots, simulationMemory, onForceOutput)
                    WidgetType.MOTOR -> MotorTestUI(slots, simulationMemory, onForceOutput)
                    WidgetType.CONVEYOR -> ConveyorTestUI(slots, simulationMemory, onForceOutput)
                    WidgetType.LAMP -> SingleOutputTestUI(slots, simulationMemory, onForceOutput, "점등", "소등")
                    WidgetType.PUSH_BUTTON -> SingleInputTestUI(slots, simulationMemory, onForceOutput, "누름", "해제")
                    WidgetType.SENSOR -> SingleInputTestUI(slots, simulationMemory, onForceOutput, "감지 ON", "감지 OFF")
                    WidgetType.VALVE -> SingleOutputTestUI(slots, simulationMemory, onForceOutput, "개방", "폐쇄")
                    WidgetType.WORKPIECE_SUPPLIER -> SingleOutputTestUI(slots, simulationMemory, onForceOutput, "공급 ON", "공급 OFF")
                    WidgetType.BUZZER -> SingleOutputTestUI(slots, simulationMemory, onForceOutput, "부저 ON", "부저 OFF")
                    WidgetType.STORAGE_BIN -> {} // IO 없음
                    WidgetType.SIGNAL_TOWER -> SignalTowerTestUI(slots, simulationMemory, onForceOutput)
                }

                HorizontalDivider()
                Text("동작 미리보기", style = MaterialTheme.typography.labelMedium)
                WidgetMotionPreview(widget.widgetType, out1On)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── 실린더: 출력1(전진Y) + 입력2개(LS)
@Composable
private fun CylinderTestUI(
    slots: List<IOSlot>, mem: Map<String, Boolean>,
    onForce: (String, Boolean) -> Unit
) {
    val out1 = slots.find { it.key == "OUT1" }?.address?.takeIf { it.isNotBlank() }
    val isExtended = out1?.let { mem[it] } ?: false

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { out1?.let { onForce(it, true) } },
            enabled = out1 != null && !isExtended,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) { Text("전진 ▶") }
        Button(
            onClick = { out1?.let { onForce(it, false) } },
            enabled = out1 != null && isExtended,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
        ) { Text("◀ 후진") }
        Text(
            if (isExtended) "전진" else "후진",
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

// ── 모터/컨베이어: 출력1(정전) + 출력2(역전)
@Composable
private fun MotorTestUI(
    slots: List<IOSlot>, mem: Map<String, Boolean>,
    onForce: (String, Boolean) -> Unit
) {
    val out1 = slots.find { it.key == "OUT1" }?.address?.takeIf { it.isNotBlank() }
    val out2 = slots.find { it.key == "OUT2" }?.address?.takeIf { it.isNotBlank() }
    val fwd = out1?.let { mem[it] } ?: false
    val rev = out2?.let { mem[it] } ?: false

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                out1?.let { onForce(it, true) }
                out2?.let { onForce(it, false) }
            },
            enabled = out1 != null && !fwd,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) { Text("정전") }
        Button(
            onClick = {
                out1?.let { onForce(it, false) }
                out2?.let { onForce(it, true) }
            },
            enabled = out2 != null && !rev,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
        ) { Text("역전") }
        Button(
            onClick = {
                out1?.let { onForce(it, false) }
                out2?.let { onForce(it, false) }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
        ) { Text("정지") }
        Text(
            when {
                fwd -> "정전 운전"
                rev -> "역전 운전"
                else -> "정지"
            },
            modifier = Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ConveyorTestUI(
    slots: List<IOSlot>, mem: Map<String, Boolean>,
    onForce: (String, Boolean) -> Unit
) = MotorTestUI(slots, mem, onForce)

// ── 단일 출력 (램프, 밸브, 공급기)
@Composable
private fun SingleOutputTestUI(
    slots: List<IOSlot>, mem: Map<String, Boolean>,
    onForce: (String, Boolean) -> Unit,
    onLabel: String, offLabel: String
) {
    val addr = slots.find { it.key == "OUT1" }?.address?.takeIf { it.isNotBlank() }
    val isOn = addr?.let { mem[it] } ?: false

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { addr?.let { onForce(it, !isOn) } }, enabled = addr != null) {
            Text(if (isOn) offLabel else onLabel)
        }
        Text(
            if (isOn) onLabel else offLabel,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

// ── 단일 입력 (스위치, 센서)
@Composable
private fun SingleInputTestUI(
    slots: List<IOSlot>, mem: Map<String, Boolean>,
    onForce: (String, Boolean) -> Unit,
    onLabel: String, offLabel: String
) {
    val addr = slots.find { it.key == "IN1" }?.address?.takeIf { it.isNotBlank() }
    val isOn = addr?.let { mem[it] } ?: false

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { addr?.let { onForce(it, true) } }, enabled = addr != null && !isOn) {
            Text(onLabel)
        }
        Button(onClick = { addr?.let { onForce(it, false) } }, enabled = addr != null && isOn) {
            Text(offLabel)
        }
        Text(
            if (isOn) onLabel else offLabel,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

// ── 경광등: 각 단 개별 ON/OFF
@Composable
private fun SignalTowerTestUI(
    slots: List<IOSlot>, mem: Map<String, Boolean>,
    onForce: (String, Boolean) -> Unit
) {
    val tierNames = listOf("빨강", "노랑", "녹색", "파랑")
    slots.filter { it.key.startsWith("OUT") }.forEachIndexed { idx, slot ->
        val addr = slot.address.takeIf { it.isNotBlank() }
        val isOn = addr?.let { mem[it] } ?: false
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tierNames.getOrElse(idx) { "${idx+1}단" },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(40.dp))
            Button(
                onClick = { addr?.let { onForce(it, !isOn) } },
                enabled = addr != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOn) Color(0xFF4CAF50) else Color.Gray
                )
            ) { Text(if (isOn) "ON" else "OFF") }
        }
    }
}

@Composable
private fun WidgetMotionPreview(widgetType: WidgetType, isOn: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "motion")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width; val h = size.height
        when (widgetType) {
            WidgetType.CYLINDER -> {
                val ext = if (isOn) w * 0.3f * animProgress + w * 0.15f else w * 0.15f
                drawRoundRect(Color(0xFF78909C), Offset(w * 0.1f, h * 0.25f), Size(w * 0.35f, h * 0.5f), CornerRadius(4f))
                drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.45f, h * 0.35f), Size(ext, h * 0.3f), CornerRadius(2f))
                drawCircle(Color(0xFF546E7A), h * 0.12f, Offset(w * 0.45f + ext, h * 0.5f))
            }
            WidgetType.MOTOR -> {
                val rot = if (isOn) rotationAnim else 0f
                val c = Offset(w / 2f, h / 2f)
                drawCircle(Color(0xFF5C6BC0), h * 0.35f, c)
                rotate(rot, c) {
                    drawLine(Color.White, c, Offset(c.x + h * 0.3f, c.y), strokeWidth = 3f)
                    drawLine(Color.White, c, Offset(c.x, c.y - h * 0.3f), strokeWidth = 3f)
                }
            }
            WidgetType.LAMP -> {
                val a = if (isOn) 0.4f + 0.6f * animProgress else 0.2f
                drawCircle(Color(0xFFFFEB3B).copy(alpha = a), h * 0.35f, Offset(w / 2f, h / 2f))
            }
            WidgetType.CONVEYOR -> {
                val shift = if (isOn) animProgress * 30f else 0f
                drawRoundRect(Color(0xFF555555), Offset(w * 0.1f, h * 0.3f), Size(w * 0.8f, h * 0.4f), CornerRadius(h * 0.2f))
                var sx = w * 0.1f + shift % 30f
                while (sx < w * 0.9f) {
                    drawLine(Color.Black.copy(alpha = 0.25f), Offset(sx, h * 0.3f), Offset(sx + h * 0.24f, h * 0.7f), strokeWidth = 8f)
                    sx += 30f
                }
            }
            else -> {
                val color = if (isOn) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                drawRoundRect(color, Offset(w * 0.3f, h * 0.2f), Size(w * 0.4f, h * 0.6f), CornerRadius(8f))
            }
        }
    }
}
