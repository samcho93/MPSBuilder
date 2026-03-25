package com.example.mpsbuilder.domain.usecase

import com.example.mpsbuilder.data.ladder.IOAddress
import com.example.mpsbuilder.data.ladder.IOAddress.AddressType

/**
 * IO 주소 자동 채번 — 접점/코일 배치 시 다음 번호 자동 할당
 */
class AutoNumberingUseCase {

    private var xCounter = 0   // 입력
    private var yCounter = 0   // 출력
    private var mCounter = 0   // 보조 릴레이
    private var tCounter = 0   // 타이머
    private var cCounter = 0   // 카운터

    fun nextInput(): IOAddress = IOAddress(AddressType.X, xCounter++)
    fun nextOutput(): IOAddress = IOAddress(AddressType.Y, yCounter++)
    fun nextAux(): IOAddress = IOAddress(AddressType.M, mCounter++)
    fun nextTimer(): IOAddress = IOAddress(AddressType.T, tCounter).also { tCounter++ }
    fun nextCounter(): IOAddress = IOAddress(AddressType.C, cCounter).also { cCounter++ }

    fun currentTimerNumber(): Int = tCounter
    fun currentCounterNumber(): Int = cCounter

    data class NumberingState(
        val x: Int, val y: Int, val m: Int, val t: Int, val c: Int
    )

    fun currentState() = NumberingState(xCounter, yCounter, mCounter, tCounter, cCounter)

    fun restoreState(state: NumberingState) {
        xCounter = state.x; yCounter = state.y; mCounter = state.m
        tCounter = state.t; cCounter = state.c
    }

    fun reset() { xCounter = 0; yCounter = 0; mCounter = 0; tCounter = 0; cCounter = 0 }
}
