package com.example.mpsbuilder.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column {
                // 상단 바
                TopAppBar(
                    title = { Text("도움말") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "닫기")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                // 탭
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("개요", "사용 설명서", "위젯 가이드", "래더 연동", "기술 스택")

                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                // 내용
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> OverviewTab()
                        1 -> UserGuideTab()
                        2 -> WidgetGuideTab()
                        3 -> LadderGuideTab()
                        4 -> TechStackTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab() {
    SectionTitle("MPS Builder")
    Body(
        "PLC 기반 실습장비(MPS: Modular Production System)를 " +
        "가상 작업대 위에 자유 배치하고, 개별 테스트 동작 및 " +
        "공작물 흐름을 시뮬레이션하는 드래그&드롭 빌더입니다."
    )

    SectionTitle("주요 기능")
    BulletList(listOf(
        "11종 산업용 장비 위젯 배치 (실린더, 모터, 컨베이어, 센서 등)",
        "자유 회전(0~360도), 크기 조절, zOrder 변경",
        "PLC IO 주소(X/Y) 할당 및 래더 프로그램 연동",
        "테스트 모드에서 개별 장비 조작 및 자동 시뮬레이션",
        "공작물(금속/비금속) 투입, 이송, 감지, 적재 시뮬레이션",
        "PLCSimul JSON 및 MELSEC CSV 래더 파일 읽기",
        "래더 다이어그램 실시간 표시 (전류 흐름 하이라이트)",
        "레이아웃 저장/불러오기 (.mps 파일)",
        "자동 저장 (앱 전환 시 상태 유지)"
    ))

    SectionTitle("화면 구성")
    Body("태블릿 가로 모드 3단 레이아웃:")
    BulletList(listOf(
        "좌측: 위젯 팔레트 (편집) / IO 모니터 (테스트)",
        "중앙: 작업대 캔버스 (Pan/Zoom + 격자 배경)",
        "우측: 속성 패널 (편집) / 래더 뷰어 (래더 로드 시)"
    ))
}

@Composable
private fun UserGuideTab() {
    SectionTitle("편집 모드")

    SubTitle("위젯 배치")
    BulletList(listOf(
        "좌측 팔레트에서 위젯을 탭하면 작업대 중앙에 추가됩니다",
        "위젯을 드래그하여 원하는 위치로 이동합니다",
        "위젯을 탭하면 선택되고 우측에 속성 패널이 나타납니다",
    ))

    SubTitle("회전 및 크기 조절")
    BulletList(listOf(
        "선택된 위젯 상단의 파란색 바를 드래그하면 자유 회전됩니다",
        "네 모서리의 원형 핸들을 드래그하면 크기가 조절됩니다",
        "속성 패널의 회전/크기 버튼으로도 조절 가능합니다",
    ))

    SubTitle("복수 선택")
    BulletList(listOf(
        "빈 공간을 드래그하면 블록 선택 사각형이 나타납니다",
        "사각형 안의 모든 위젯이 선택됩니다",
        "복수 선택 후 복사/잘라내기/삭제가 가능합니다"
    ))

    SubTitle("IO 주소 할당")
    BulletList(listOf(
        "속성 패널에서 각 IO 슬롯에 주소를 입력합니다",
        "출력(Y): Y000, Y001, ... / 입력(X): X000, X001, ...",
        "자동으로 대문자 변환됩니다",
        "위젯 타입에 따라 IO 슬롯이 자동으로 결정됩니다"
    ))

    SubTitle("공작물 설정")
    BulletList(listOf(
        "공급기 위젯을 배치하고 실린더를 연결합니다",
        "좌측 팔레트 하단에서 금속/비금속 공작물을 추가합니다",
        "공급기 스택에 순서대로 쌓입니다"
    ))

    SubTitle("저장/불러오기")
    BulletList(listOf(
        "저장 버튼: 현재 이름으로 저장 (새 파일이면 이름 입력)",
        "다른 이름으로 저장: 현재 이름을 기본으로 표시, 수정 가능",
        "불러오기: 저장된 레이아웃 목록에서 선택",
        "앱이 백그라운드로 가도 자동 저장되어 상태가 유지됩니다"
    ))

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionTitle("테스트 모드")
    Body("상단 바의 재생(Play) 버튼으로 테스트 모드에 진입합니다.")

    SubTitle("개별 조작")
    BulletList(listOf(
        "실린더: 탭하면 전진/후진 토글 (단동: 누르는 동안 전진)",
        "스위치: 푸시(누르는 동안), 토글(탭마다 교대), 선택(ON 유지)",
        "모터: 탭하면 정전/역전/정지 순환",
        "램프: 탭하면 ON/OFF 토글",
        "부저: ON 시 1kHz 비프음 출력"
    ))

    SubTitle("자동 시뮬레이션")
    BulletList(listOf(
        "실린더 전진 → 공급기에서 공작물 투입",
        "컨베이어 ON → 공작물이 벨트 위에서 이동",
        "센서 위치 통과 시 자동 감지 (근접: 금속만, 광전: 전체)",
        "실린더가 컨베이어 위 공작물을 밀면 적재함으로 이동",
        "래더 로드 시 20ms 스캔 루프로 자동 연동"
    ))

    SubTitle("IO 모니터")
    Body("테스트 모드 좌측에 전체 IO 주소 상태가 실시간 표시됩니다. " +
         "ON 상태는 녹색으로 하이라이트됩니다.")

    SubTitle("테스트 종료")
    Body("정지(Stop) 버튼으로 테스트 모드를 종료하면, " +
         "공급기 스택과 적재함이 테스트 전 상태로 복구됩니다.")
}

@Composable
private fun WidgetGuideTab() {
    data class WidgetInfo(
        val name: String,
        val io: String,
        val desc: String
    )

    val widgets = listOf(
        WidgetInfo("실린더", "단동: 출력1 + LS입력2 / 복동: 출력2 + LS입력2",
            "단동은 출력 ON 시 전진, OFF 시 자동 후진. 복동은 전진/후진 각각 출력. " +
            "전진 완료 시 전진LS ON, 후진 완료 시 후진LS ON."),
        WidgetInfo("모터", "출력2 (정전, 역전)",
            "정전 출력 ON → 정방향 회전, 역전 출력 ON → 역방향 회전. " +
            "둘 다 OFF → 정지."),
        WidgetInfo("컨베이어", "단방향: 출력1 / 양방향: 출력2 (정전, 역전)",
            "소(160dp)/중(280dp)/대(420dp) 크기 선택. " +
            "방향(상하좌우) 설정. 벨트 스트라이프 애니메이션으로 운전 상태 표시."),
        WidgetInfo("램프", "출력1 (점등)",
            "출력 ON 시 선택한 색상으로 점등. 색상 선택 가능."),
        WidgetInfo("스위치", "입력1 (접점)",
            "푸시: 누르는 동안만 ON. 토글: 탭마다 교대. 선택: ON 유지. " +
            "사각형 베이스 + 원형 버튼. 색상 선택 가능."),
        WidgetInfo("센서", "입력1 (감지)",
            "근접센서: 금속 공작물만 감지. 광전센서: 모든 공작물 감지. " +
            "컨베이어 위 공작물이 센서 위치를 지나갈 때 자동 감지."),
        WidgetInfo("밸브", "출력1 (개방)",
            "출력 ON 시 개방(녹색), OFF 시 폐쇄(빨강)."),
        WidgetInfo("공급기", "IO 없음 (실린더 연결)",
            "연결된 실린더가 전진하면 스택의 공작물을 투입. " +
            "팔레트 하단에서 금속/비금속 공작물 추가."),
        WidgetInfo("부저", "출력1 (부저)",
            "출력 ON 시 1kHz 비프음 출력. OFF 시 즉시 정지. 색상 선택 가능."),
        WidgetInfo("적재함", "IO 없음",
            "실린더가 공작물을 밀어내면 적재함에 담김. " +
            "적재된 공작물이 실제 크기로 표시됨."),
        WidgetInfo("경광등", "2~4단, 각 단 출력1개씩",
            "2단/3단/4단 선택. 각 단(빨강/노랑/녹색/파랑) 개별 ON/OFF.")
    )

    widgets.forEach { w ->
        SubTitle(w.name)
        Body("IO: ${w.io}")
        Body(w.desc)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun LadderGuideTab() {
    SectionTitle("래더 파일 읽기")
    Body("상단 바의 래더 파일 아이콘을 탭하여 파일을 선택합니다.")

    SubTitle("지원 형식")
    BulletList(listOf(
        "PLCSimul JSON: {\"rungs\":[...]} 형식의 프로젝트 파일",
        "MELSEC CSV: GX-Works2 내보내기 (UTF-16LE/UTF-8)",
        "간단 CSV: STEP,INSTRUCTION,P1,P2,P3,COMMENT",
        "텍스트 니모닉: LD X000 / AND X001 / OUT Y000"
    ))

    SectionTitle("래더 다이어그램 표시")
    BulletList(listOf(
        "래더 로드 후 눈 아이콘으로 래더 패널 표시/숨기기",
        "확대/축소 버튼 또는 핀치 제스처로 줌 조절",
        "수평 방향은 자동 fit, 수직 방향은 스크롤",
        "줌 비율은 패널 숨기기/보이기 후에도 유지됨"
    ))

    SectionTitle("래더 스캔 엔진")
    Body("테스트 모드 진입 시 20ms 간격으로 래더를 스캔합니다.")

    SubTitle("지원 요소")
    BulletList(listOf(
        "접점: LD/LDI (a/b접점), LDP/LDF (상승/하강 에지), AND/ANI, OR/ORI",
        "코일: OUT, SET, RST, PLS (상승펄스), PLF (하강펄스)",
        "타이머: T0~T999, 프리셋 x 100ms",
        "카운터: C0~C999, 상승에지 카운트",
        "펑션블록: MOV, ADD, SUB, MUL, DIV, CMP, INC, DEC 등",
        "스택분기: MPS/MRD/MPP (병렬 분기)"
    ))

    SubTitle("IO 매핑")
    BulletList(listOf(
        "위젯의 IO 주소(X000, Y000 등)와 래더의 IO가 자동 매핑됩니다",
        "래더가 Y 출력을 변경하면 → 해당 위젯이 동작",
        "위젯 상태(센서 감지, LS 등)가 X 입력으로 → 래더에 반영",
        "특수 릴레이: SM400(항상 ON), SM412(1초 클록) 등 지원"
    ))
}

@Composable
private fun TechStackTab() {
    SectionTitle("기술 스택")

    data class TechItem(val category: String, val tech: String)
    val items = listOf(
        TechItem("언어", "Kotlin 2.0"),
        TechItem("UI 프레임워크", "Jetpack Compose + Canvas API"),
        TechItem("아키텍처", "MVVM (ViewModel + StateFlow)"),
        TechItem("의존성 주입", "Hilt (Dagger)"),
        TechItem("직렬화", "kotlinx.serialization (JSON)"),
        TechItem("저장", "내부 파일 저장소 (filesDir/mpsbuilder/)"),
        TechItem("제스처", "detectDragGestures, detectTransformGestures"),
        TechItem("애니메이션", "infiniteRepeatable, Animatable"),
        TechItem("오디오", "AudioTrack (PCM 스트리밍)"),
        TechItem("빌드", "Gradle 8.9, AGP 8.7.3, Version Catalog"),
        TechItem("최소 SDK", "26 (Android 8.0)"),
        TechItem("타겟 SDK", "35 (Android 15)"),
    )

    items.forEach { (cat, tech) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(cat, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.4f))
            Text(tech, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.6f))
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SectionTitle("프로그램 구조")
    val structure = listOf(
        "data/ladder/ — 래더 모델, 시뮬레이터, CSV 파서",
        "data/repository/ — 레이아웃 저장소",
        "di/ — Hilt DI 모듈",
        "ui/ladder/ — 래더 다이어그램 뷰어",
        "ui/workbench/ — 작업대 빌더 메인 화면",
        "ui/workbench/canvas/ — 캔버스, 격자, 오버레이",
        "ui/workbench/widgets/ — 위젯 렌더러, 팔레트, 속성패널",
        "ui/workbench/workpiece/ — 공작물 렌더러, 공급기",
        "ui/workbench/test/ — 테스트 패널, IO 모니터",
        "ui/workbench/model/ — 위젯/레이아웃/공작물 데이터 모델",
    )
    structure.forEach { line ->
        Text("  $line", style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp))
    }
}

// ── 공통 컴포넌트

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        lineHeight = 18.sp)
}

@Composable
private fun BulletList(items: List<String>) {
    items.forEach { item ->
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("  ·  ") }
                append(item)
            },
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            modifier = Modifier.padding(vertical = 1.dp)
        )
    }
}
