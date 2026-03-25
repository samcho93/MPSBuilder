package com.example.mpsbuilder.data.ladder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LadderElement {
    abstract val id: String
    abstract val address: IOAddress?
    abstract val label: String

    @Serializable @SerialName("normally_open")
    data class NormallyOpen(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("normally_closed")
    data class NormallyClosed(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("rising_edge_contact")
    data class RisingEdgeContact(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("falling_edge_contact")
    data class FallingEdgeContact(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("output_coil")
    data class OutputCoil(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("set_coil")
    data class SetCoil(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("reset_coil")
    data class ResetCoil(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("rising_edge")
    data class RisingEdge(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("falling_edge")
    data class FallingEdge(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("timer")
    data class Timer(
        override val id: String, override val address: IOAddress?, override val label: String = "",
        val timerNumber: Int = 0, val preset: Int = 100
    ) : LadderElement()

    @Serializable @SerialName("counter")
    data class Counter(
        override val id: String, override val address: IOAddress?, override val label: String = "",
        val counterNumber: Int = 0, val preset: Int = 10
    ) : LadderElement()

    @Serializable @SerialName("function_block")
    data class FunctionBlock(
        override val id: String, override val address: IOAddress? = null, override val label: String,
        val mnemonic: String, val operand1: String = "", val operand2: String = "", val operand3: String = ""
    ) : LadderElement()

    @Serializable @SerialName("special_relay")
    data class SpecialRelay(
        override val id: String, override val address: IOAddress?, override val label: String = ""
    ) : LadderElement()

    @Serializable @SerialName("horizontal_line")
    data class HorizontalLine(
        override val id: String = "HLINE_${System.nanoTime()}",
        override val address: IOAddress? = null, override val label: String = ""
    ) : LadderElement()

    fun isContact(): Boolean = this is NormallyOpen || this is NormallyClosed
            || this is RisingEdgeContact || this is FallingEdgeContact || this is SpecialRelay
    fun isCoil(): Boolean = this is OutputCoil || this is SetCoil || this is ResetCoil
            || this is RisingEdge || this is FallingEdge
    fun isOutput(): Boolean = isCoil() || this is Timer || this is Counter || this is FunctionBlock
}
