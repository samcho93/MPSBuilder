package com.example.mpsbuilder.ui.workbench.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpsbuilder.ui.workbench.model.IOSlot
import com.example.mpsbuilder.ui.workbench.model.PlacedWidgetState

/**
 * 테스트 모드 좌측 IO 모니터 패널
 * 모든 위젯의 IO 주소를 리스트로 표시하고, ON/OFF 상태를 하이라이트
 */
@Composable
fun IOMonitorPanel(
    widgets: List<PlacedWidgetState>,
    simulationMemory: Map<String, Boolean>,
    modifier: Modifier = Modifier
) {
    // 모든 위젯의 IO 슬롯 수집 (주소가 있는 것만)
    data class IOEntry(
        val address: String,
        val slotLabel: String,
        val widgetLabel: String,
        val isOutput: Boolean,
        val isOn: Boolean
    )

    val entries = widgets.flatMap { widget ->
        widget.ioSlots
            .filter { it.address.isNotBlank() }
            .map { slot ->
                IOEntry(
                    address = slot.address,
                    slotLabel = slot.label,
                    widgetLabel = widget.label,
                    isOutput = slot.key.startsWith("OUT"),
                    isOn = simulationMemory[slot.address] ?: false
                )
            }
    }.sortedBy { it.address }

    Column(
        modifier = modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(Color(0xFF1E1E1E))
            .padding(6.dp)
    ) {
        Text(
            "IO 모니터",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF90CAF9),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (entries.isEmpty()) {
            Text(
                "IO 주소 없음",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(entries) { entry ->
                    IOMonitorRow(entry.address, entry.slotLabel, entry.widgetLabel,
                        entry.isOutput, entry.isOn)
                }
            }
        }
    }
}

@Composable
private fun IOMonitorRow(
    address: String,
    slotLabel: String,
    widgetLabel: String,
    isOutput: Boolean,
    isOn: Boolean
) {
    val bgColor = when {
        isOn && isOutput -> Color(0xFF1B5E20).copy(alpha = 0.6f)  // 출력 ON: 녹색
        isOn && !isOutput -> Color(0xFFE65100).copy(alpha = 0.6f) // 입력 ON: 주황
        else -> Color(0xFF2A2A2A)
    }
    val ledColor = when {
        isOn && isOutput -> Color(0xFF4CAF50)
        isOn && !isOutput -> Color(0xFFFF9800)
        else -> Color(0xFF555555)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // LED 점
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(ledColor, CircleShape)
        )

        // 주소
        Text(
            address,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = if (isOn) Color.White else Color(0xFFBBBBBB)
        )

        // 위젯 이름 (축약)
        Text(
            widgetLabel.take(4),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color(0xFF888888),
            modifier = Modifier.weight(1f)
        )
    }
}
