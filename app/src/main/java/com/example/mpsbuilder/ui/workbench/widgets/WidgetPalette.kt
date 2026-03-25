package com.example.mpsbuilder.ui.workbench.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.mpsbuilder.ui.workbench.model.PlacedWidgetState
import com.example.mpsbuilder.ui.workbench.model.WidgetType
import com.example.mpsbuilder.ui.workbench.model.WorkpieceType

@Composable
fun WidgetPalette(
    onAddWidget: (WidgetType) -> Unit,
    suppliers: List<PlacedWidgetState>,
    onAddWorkpieceToSupplier: (supplierId: String, type: WorkpieceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val widgetItems = listOf(
        WidgetType.CYLINDER to Icons.Default.SwapHoriz,
        WidgetType.MOTOR to Icons.Default.Settings,
        WidgetType.LAMP to Icons.Default.Lightbulb,
        WidgetType.PUSH_BUTTON to Icons.Default.TouchApp,
        WidgetType.SENSOR to Icons.Default.Sensors,
        WidgetType.VALVE to Icons.Default.ToggleOn,
        WidgetType.CONVEYOR to Icons.Default.LinearScale,
        WidgetType.WORKPIECE_SUPPLIER to Icons.Default.Inventory,
        WidgetType.BUZZER to Icons.Default.VolumeUp,
        WidgetType.STORAGE_BIN to Icons.Default.Inbox,
        WidgetType.SIGNAL_TOWER to Icons.Default.SignalCellularAlt,
    )

    Column(
        modifier = modifier
            .width(100.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Text("위젯", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(widgetItems) { (type, icon) ->
                PaletteItem(label = type.label, icon = icon, onClick = { onAddWidget(type) })
            }
        }

        // ── 공작물 섹션
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("공작물", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 4.dp))

        if (suppliers.isEmpty()) {
            Text("공급기를\n배치하세요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // 첫 번째 공급기에 공작물 추가
            val targetSupplier = suppliers.first()

            // 스택 표시
            if (targetSupplier.workpieceStack.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    targetSupplier.workpieceStack.takeLast(5).forEach { wpType ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    Color(wpType.color),
                                    RoundedCornerShape(2.dp)
                                )
                                .border(0.5.dp, Color.Gray, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    if (targetSupplier.workpieceStack.size > 5) {
                        Text("+${targetSupplier.workpieceStack.size - 5}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkpieceButton(
                    label = "금속",
                    color = Color(WorkpieceType.METAL.color),
                    onClick = { onAddWorkpieceToSupplier(targetSupplier.id, WorkpieceType.METAL) },
                    modifier = Modifier.weight(1f)
                )
                WorkpieceButton(
                    label = "비금속",
                    color = Color(WorkpieceType.NON_METAL.color),
                    onClick = { onAddWorkpieceToSupplier(targetSupplier.id, WorkpieceType.NON_METAL) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WorkpieceButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.3f))
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PaletteItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, label, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
