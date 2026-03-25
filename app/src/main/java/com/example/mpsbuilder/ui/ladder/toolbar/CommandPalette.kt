package com.example.mpsbuilder.ui.ladder.toolbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpsbuilder.data.ladder.LadderElement
import java.util.UUID

/**
 * 펑션 블록 선택 팔레트 (ModalBottomSheet)
 * 28종 명령어를 카테고리별 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    onSelect: (LadderElement.FunctionBlock) -> Unit,
    onDismiss: () -> Unit
) {
    data class FBItem(val mnemonic: String, val desc: String)
    data class Category(val name: String, val items: List<FBItem>)

    val categories = listOf(
        Category("전송", listOf(
            FBItem("MOV", "워드 전송"),
            FBItem("DMOV", "더블워드 전송"),
            FBItem("BMOV", "블록 전송"),
            FBItem("FMOV", "동일 데이터 전송"),
            FBItem("MOVP", "펄스 전송"),
        )),
        Category("비교", listOf(
            FBItem("CMP", "비교"),
            FBItem("DCMP", "더블워드 비교"),
        )),
        Category("사칙연산", listOf(
            FBItem("ADD", "덧셈"),
            FBItem("SUB", "뺄셈"),
            FBItem("MUL", "곱셈"),
            FBItem("DIV", "나눗셈"),
            FBItem("DADD", "더블 덧셈"),
            FBItem("DSUB", "더블 뺄셈"),
        )),
        Category("비트", listOf(
            FBItem("WAND", "워드 AND"),
            FBItem("WOR", "워드 OR"),
            FBItem("WXOR", "워드 XOR"),
            FBItem("CML", "보수"),
        )),
        Category("시프트", listOf(
            FBItem("SHL", "좌 시프트"),
            FBItem("SHR", "우 시프트"),
            FBItem("ROL", "좌 로테이트"),
            FBItem("ROR", "우 로테이트"),
        )),
        Category("변환", listOf(
            FBItem("BCD", "BIN→BCD"),
            FBItem("BIN", "BCD→BIN"),
            FBItem("DECO", "디코드"),
            FBItem("ENCO", "엔코드"),
        )),
        Category("증감", listOf(
            FBItem("INC", "인크리먼트"),
            FBItem("DEC", "디크리먼트"),
        )),
        Category("제어", listOf(
            FBItem("CALL", "서브루틴 호출"),
            FBItem("FEND", "메인 종료"),
        )),
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "명령어 선택",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .padding(horizontal = 16.dp)
        ) {
            categories.forEach { cat ->
                item {
                    Text(
                        cat.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(cat.items) { fb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(LadderElement.FunctionBlock(
                                    id = UUID.randomUUID().toString(),
                                    label = fb.mnemonic,
                                    mnemonic = fb.mnemonic
                                ))
                                onDismiss()
                            }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(fb.mnemonic, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(fb.desc, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
