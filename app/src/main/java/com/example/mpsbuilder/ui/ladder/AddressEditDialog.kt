package com.example.mpsbuilder.ui.ladder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpsbuilder.data.ladder.IOAddress
import com.example.mpsbuilder.data.ladder.LadderElement

/**
 * 래더 요소 속성 편집 다이얼로그
 * - IO 주소 입력/선택
 * - 라벨(이름) 편집
 * - 타이머/카운터: 번호 + 프리셋
 * - 펑션블록: 오퍼랜드 3개
 * - 사용 중인 주소 히스토리
 */
@Composable
fun AddressEditDialog(
    element: LadderElement,
    usedAddresses: List<IOAddress>,
    ioLabels: Map<String, String>,
    onConfirm: (LadderElement) -> Unit,
    onDismiss: () -> Unit
) {
    var addressText by remember {
        mutableStateOf(element.address?.toString() ?: "")
    }
    var labelText by remember { mutableStateOf(element.label) }

    // 타이머/카운터 전용
    var timerNumber by remember {
        mutableStateOf(
            when (element) {
                is LadderElement.Timer -> element.timerNumber.toString()
                else -> ""
            }
        )
    }
    var counterNumber by remember {
        mutableStateOf(
            when (element) {
                is LadderElement.Counter -> element.counterNumber.toString()
                else -> ""
            }
        )
    }
    var presetValue by remember {
        mutableStateOf(
            when (element) {
                is LadderElement.Timer -> element.preset.toString()
                is LadderElement.Counter -> element.preset.toString()
                else -> ""
            }
        )
    }

    // FB 전용
    var op1 by remember {
        mutableStateOf(if (element is LadderElement.FunctionBlock) element.operand1 else "")
    }
    var op2 by remember {
        mutableStateOf(if (element is LadderElement.FunctionBlock) element.operand2 else "")
    }
    var op3 by remember {
        mutableStateOf(if (element is LadderElement.FunctionBlock) element.operand3 else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("요소 편집") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 요소 타입 표시
                Text(
                    elementTypeName(element),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // 주소 입력 (타이머/카운터/FB 제외)
                if (element !is LadderElement.Timer && element !is LadderElement.Counter
                    && element !is LadderElement.FunctionBlock
                ) {
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = { addressText = it.uppercase() },
                        label = { Text("IO 주소") },
                        placeholder = { Text("예: X000, Y001, M0") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )
                }

                // 라벨
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("라벨(이름)") },
                    placeholder = { Text("예: PB_START") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── 타이머 전용
                if (element is LadderElement.Timer) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timerNumber,
                            onValueChange = { timerNumber = it.filter { c -> c.isDigit() } },
                            label = { Text("T 번호") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = presetValue,
                            onValueChange = { presetValue = it.filter { c -> c.isDigit() } },
                            label = { Text("K (프리셋)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "프리셋 × 100ms (K100 = 10초)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── 카운터 전용
                if (element is LadderElement.Counter) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = counterNumber,
                            onValueChange = { counterNumber = it.filter { c -> c.isDigit() } },
                            label = { Text("C 번호") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = presetValue,
                            onValueChange = { presetValue = it.filter { c -> c.isDigit() } },
                            label = { Text("K (프리셋)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── 펑션블록 전용
                if (element is LadderElement.FunctionBlock) {
                    Text("명령: ${element.mnemonic}",
                        fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = op1, onValueChange = { op1 = it.uppercase() },
                        label = { Text("오퍼랜드 1") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = op2, onValueChange = { op2 = it.uppercase() },
                        label = { Text("오퍼랜드 2") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = op3, onValueChange = { op3 = it.uppercase() },
                        label = { Text("오퍼랜드 3") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── 사용 중인 주소 히스토리
                if (usedAddresses.isNotEmpty() && element !is LadderElement.FunctionBlock) {
                    HorizontalDivider()
                    Text("사용 중인 주소", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        items(usedAddresses) { addr ->
                            val addrStr = addr.toString()
                            val label = ioLabels[addrStr] ?: ""
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        addressText = addrStr
                                        if (label.isNotBlank()) labelText = label
                                    }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    addrStr, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = addrColor(addr)
                                )
                                Text(label, fontSize = 11.sp,
                                    color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = buildResult(
                    element, addressText, labelText,
                    timerNumber, counterNumber, presetValue,
                    op1, op2, op3
                )
                onConfirm(result)
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun buildResult(
    original: LadderElement,
    addressText: String,
    labelText: String,
    timerNum: String,
    counterNum: String,
    preset: String,
    op1: String, op2: String, op3: String
): LadderElement {
    val addr = IOAddress.parse(addressText)
    return when (original) {
        is LadderElement.NormallyOpen -> original.copy(address = addr, label = labelText)
        is LadderElement.NormallyClosed -> original.copy(address = addr, label = labelText)
        is LadderElement.RisingEdgeContact -> original.copy(address = addr, label = labelText)
        is LadderElement.FallingEdgeContact -> original.copy(address = addr, label = labelText)
        is LadderElement.SpecialRelay -> original.copy(address = addr, label = labelText)
        is LadderElement.OutputCoil -> original.copy(address = addr, label = labelText)
        is LadderElement.SetCoil -> original.copy(address = addr, label = labelText)
        is LadderElement.ResetCoil -> original.copy(address = addr, label = labelText)
        is LadderElement.RisingEdge -> original.copy(address = addr, label = labelText)
        is LadderElement.FallingEdge -> original.copy(address = addr, label = labelText)
        is LadderElement.Timer -> {
            val tNum = timerNum.toIntOrNull() ?: original.timerNumber
            val pVal = preset.toIntOrNull() ?: original.preset
            original.copy(
                address = IOAddress(IOAddress.AddressType.T, tNum),
                label = labelText,
                timerNumber = tNum,
                preset = pVal
            )
        }
        is LadderElement.Counter -> {
            val cNum = counterNum.toIntOrNull() ?: original.counterNumber
            val pVal = preset.toIntOrNull() ?: original.preset
            original.copy(
                address = IOAddress(IOAddress.AddressType.C, cNum),
                label = labelText,
                counterNumber = cNum,
                preset = pVal
            )
        }
        is LadderElement.FunctionBlock -> original.copy(
            label = labelText,
            operand1 = op1, operand2 = op2, operand3 = op3
        )
        is LadderElement.HorizontalLine -> original
    }
}

private fun elementTypeName(el: LadderElement): String = when (el) {
    is LadderElement.NormallyOpen -> "a접점 (NO)"
    is LadderElement.NormallyClosed -> "b접점 (NC)"
    is LadderElement.RisingEdgeContact -> "상승에지 접점 (AP)"
    is LadderElement.FallingEdgeContact -> "하강에지 접점 (BP)"
    is LadderElement.SpecialRelay -> "특수 릴레이 (SM)"
    is LadderElement.OutputCoil -> "출력 코일 (OUT)"
    is LadderElement.SetCoil -> "세트 코일 (SET)"
    is LadderElement.ResetCoil -> "리셋 코일 (RST)"
    is LadderElement.RisingEdge -> "상승펄스 (PLS)"
    is LadderElement.FallingEdge -> "하강펄스 (PLF)"
    is LadderElement.Timer -> "타이머 (T)"
    is LadderElement.Counter -> "카운터 (C)"
    is LadderElement.FunctionBlock -> "펑션블록 (${el.mnemonic})"
    is LadderElement.HorizontalLine -> "수평선"
}

private fun addrColor(addr: IOAddress): Color = when (addr.type) {
    IOAddress.AddressType.X -> Color(0xFF1565C0)
    IOAddress.AddressType.Y -> Color(0xFFE65100)
    IOAddress.AddressType.M -> Color(0xFF2E7D32)
    IOAddress.AddressType.T -> Color(0xFF6A1B9A)
    IOAddress.AddressType.C -> Color(0xFF00838F)
    IOAddress.AddressType.SM -> Color(0xFF37474F)
    else -> Color.Gray
}
