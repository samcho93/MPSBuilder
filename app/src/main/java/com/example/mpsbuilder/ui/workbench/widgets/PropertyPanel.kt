package com.example.mpsbuilder.ui.workbench.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.mpsbuilder.ui.workbench.model.*

@Composable
fun PropertyPanel(
    widget: PlacedWidgetState,
    motors: List<PlacedWidgetState>,
    cylinders: List<PlacedWidgetState>,
    onLabelChange: (String) -> Unit,
    onIOSlotChange: (slotKey: String, address: String) -> Unit,
    onRotate: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onConveyorConfigChange: ((ConveyorConfig) -> Unit)? = null,
    onLinkMotor: ((String) -> Unit)? = null,
    onCylinderModeChange: ((CylinderMode) -> Unit)? = null,
    onConveyorDriveModeChange: ((ConveyorDriveMode) -> Unit)? = null,
    onSensorTypeChange: ((SensorType) -> Unit)? = null,
    onSwitchTypeChange: ((SwitchType) -> Unit)? = null,
    onLinkCylinder: ((String) -> Unit)? = null,
    onClearStack: (() -> Unit)? = null,
    onWidgetColorChange: ((Long) -> Unit)? = null,
    onSignalTowerTiersChange: ((SignalTowerTiers) -> Unit)? = null,
    onDelete: () -> Unit,
    onOpenTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var labelText by remember(widget.id) { mutableStateOf(widget.label) }

    Column(
        modifier = modifier
            .width(210.dp)
            .fillMaxHeight()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("속성", style = MaterialTheme.typography.titleSmall)

        // ID & 타입
        Text("ID: ${widget.id}", style = MaterialTheme.typography.bodySmall)
        Text("타입: ${widget.widgetType.label}", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()

        // 레이블
        OutlinedTextField(
            value = labelText,
            onValueChange = {
                labelText = it
                onLabelChange(it)
            },
            label = { Text("레이블") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )

        // ── 실린더 단동/복동 선택
        if (widget.widgetType == WidgetType.CYLINDER && onCylinderModeChange != null) {
            HorizontalDivider()
            Text("실린더 타입", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CylinderMode.entries.forEach { mode ->
                    FilterChip(
                        selected = widget.cylinderMode == mode,
                        onClick = { onCylinderModeChange(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // ── 스위치 타입 선택
        if (widget.widgetType == WidgetType.PUSH_BUTTON && onSwitchTypeChange != null) {
            HorizontalDivider()
            Text("스위치 타입", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchType.entries.forEach { st ->
                    FilterChip(
                        selected = widget.switchType == st,
                        onClick = { onSwitchTypeChange(st) },
                        label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Text(
                when (widget.switchType) {
                    SwitchType.PUSH -> "누르는 동안만 ON"
                    SwitchType.TOGGLE -> "누를 때마다 ON/OFF 교대"
                    SwitchType.SELECT -> "ON 유지, 다시 누르면 OFF"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── 색상 선택 (스위치, 램프, 부저)
        if ((widget.widgetType == WidgetType.PUSH_BUTTON || widget.widgetType == WidgetType.LAMP
                    || widget.widgetType == WidgetType.BUZZER)
            && onWidgetColorChange != null) {
            HorizontalDivider()
            Text("색상", style = MaterialTheme.typography.labelMedium)
            val colorOptions = listOf(
                0L to "기본",
                0xFFF44336 to "빨강",
                0xFF4CAF50 to "녹색",
                0xFF2196F3 to "파랑",
                0xFFFFEB3B to "노랑",
                0xFFFF9800 to "주황",
                0xFF9C27B0 to "보라",
                0xFFFFFFFF to "흰색",
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                colorOptions.forEach { (colorVal, _) ->
                    val isSelected = widget.widgetColor == colorVal
                    val displayColor = if (colorVal == 0L) Color(0xFF9E9E9E) else Color(colorVal)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(displayColor, RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .clickable { onWidgetColorChange(colorVal) }
                    )
                }
            }
        }

        // ── 컨베이어 단방향/양방향 선택
        if (widget.widgetType == WidgetType.CONVEYOR && onConveyorDriveModeChange != null) {
            HorizontalDivider()
            Text("구동 모드", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ConveyorDriveMode.entries.forEach { mode ->
                    FilterChip(
                        selected = widget.conveyorDriveMode == mode,
                        onClick = { onConveyorDriveModeChange(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        HorizontalDivider()

        // ── IO 주소 슬롯 (위젯별 자동 분기)
        Text("IO 주소", style = MaterialTheme.typography.labelMedium)

        widget.ioSlots.forEach { slot ->
            IOSlotField(
                widgetId = widget.id,
                slot = slot,
                onAddressChange = { addr -> onIOSlotChange(slot.key, addr) }
            )
        }

        HorizontalDivider()

        // 위치 정보
        Text(
            "위치: (${widget.positionX.toInt()}, ${widget.positionY.toInt()})",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "각도: ${widget.rotationDeg.toInt()}°",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "크기: ${String.format("%.1f", widget.scaleFactor)}x",
            style = MaterialTheme.typography.bodySmall
        )

        // 회전 버튼
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(
                onClick = { onRotate(-45f) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.RotateLeft, "왼쪽 회전", Modifier.size(16.dp))
            }
            FilledTonalButton(
                onClick = { onRotate(45f) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.RotateRight, "오른쪽 회전", Modifier.size(16.dp))
            }
        }

        // 크기 조절
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(
                onClick = { onScale(0.8f) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.ZoomOut, "축소", Modifier.size(16.dp))
            }
            FilledTonalButton(
                onClick = { onScale(1.25f) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(Icons.Default.ZoomIn, "확대", Modifier.size(16.dp))
            }
        }

        // zOrder
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(
                onClick = onBringToFront,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) { Text("앞으로", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton(
                onClick = onSendToBack,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp)
            ) { Text("뒤로", style = MaterialTheme.typography.labelSmall) }
        }

        // 경광등 단 수 선택
        if (widget.widgetType == WidgetType.SIGNAL_TOWER && onSignalTowerTiersChange != null) {
            HorizontalDivider()
            Text("경광등 단 수", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SignalTowerTiers.entries.forEach { tier ->
                    FilterChip(
                        selected = widget.signalTowerTiers == tier,
                        onClick = { onSignalTowerTiersChange(tier) },
                        label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // 컨베이어 전용 설정
        if (widget.widgetType == WidgetType.CONVEYOR && widget.conveyorConfig != null) {
            HorizontalDivider()
            Text("컨베이어 설정", style = MaterialTheme.typography.labelMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ConveyorSize.entries.forEach { size ->
                    FilterChip(
                        selected = widget.conveyorConfig.size == size,
                        onClick = {
                            onConveyorConfigChange?.invoke(widget.conveyorConfig.copy(size = size))
                        },
                        label = { Text(size.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ConveyorDirection.entries.forEach { dir ->
                    val dirLabel = when (dir) {
                        ConveyorDirection.LEFT -> "←"
                        ConveyorDirection.RIGHT -> "→"
                        ConveyorDirection.UP -> "↑"
                        ConveyorDirection.DOWN -> "↓"
                    }
                    FilterChip(
                        selected = widget.conveyorConfig.direction == dir,
                        onClick = {
                            onConveyorConfigChange?.invoke(widget.conveyorConfig.copy(direction = dir))
                        },
                        label = { Text(dirLabel) }
                    )
                }
            }

            if (motors.isNotEmpty() && onLinkMotor != null) {
                Text("모터 연결", style = MaterialTheme.typography.labelSmall)
                motors.forEach { motor ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = widget.linkedMotorId == motor.id,
                            onClick = { onLinkMotor(motor.id) }
                        )
                        Text(
                            "${motor.label} (${motor.linkedIOAddress ?: "?"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 센서 타입 선택
        if (widget.widgetType == WidgetType.SENSOR && onSensorTypeChange != null) {
            HorizontalDivider()
            Text("센서 타입", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SensorType.entries.forEach { st ->
                    FilterChip(
                        selected = widget.sensorType == st,
                        onClick = { onSensorTypeChange(st) },
                        label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Text(
                when (widget.sensorType) {
                    SensorType.PROXIMITY -> "금속만 감지"
                    SensorType.PHOTO -> "금속+비금속 모두 감지"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 공급기 설정 — 실린더 연결 + 스택
        if (widget.widgetType == WidgetType.WORKPIECE_SUPPLIER) {
            HorizontalDivider()
            Text("공급기 설정", style = MaterialTheme.typography.labelMedium)
            Text("실린더 전진 → 공작물 투입", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (cylinders.isNotEmpty() && onLinkCylinder != null) {
                Text("실린더 연결", style = MaterialTheme.typography.labelSmall)
                cylinders.forEach { cyl ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = widget.linkedCylinderId == cyl.id,
                            onClick = { onLinkCylinder(cyl.id) }
                        )
                        Text("${cyl.label} (${cyl.id})",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text("실린더를 먼저 배치하세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            // 스택 표시
            if (widget.workpieceStack.isNotEmpty()) {
                Text("적재: ${widget.workpieceStack.size}개", style = MaterialTheme.typography.bodySmall)
                if (onClearStack != null) {
                    TextButton(onClick = onClearStack) {
                        Text("스택 비우기", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        HorizontalDivider()

        // 테스트 동작
        Button(onClick = onOpenTest, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("테스트 동작")
        }

        // 삭제
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("삭제")
        }
    }
}

/** 개별 IO 슬롯 입력 필드 — 대문자 자동 변환 */
@Composable
private fun IOSlotField(
    widgetId: String,
    slot: IOSlot,
    onAddressChange: (String) -> Unit
) {
    var text by remember(widgetId, slot.key) { mutableStateOf(slot.address) }
    val isOutput = slot.key.startsWith("OUT")
    val prefix = if (isOutput) "Y" else "X"
    val tagColor = if (isOutput) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 출력/입력 태그
        Surface(
            color = tagColor,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.width(32.dp)
        ) {
            Text(
                if (isOutput) "OUT" else "IN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val upper = raw.uppercase()
                text = upper
                onAddressChange(upper)
            },
            label = { Text(slot.label, style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text("예: ${prefix}000") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters
            )
        )
    }
}
