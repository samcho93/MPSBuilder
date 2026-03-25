package com.example.mpsbuilder.data.ladder

import kotlinx.serialization.Serializable

@Serializable
data class IOAddress(
    val type: AddressType,
    val number: Int
) {
    @Serializable
    enum class AddressType(val prefix: String) {
        X("X"), Y("Y"), M("M"), S("S"),
        T("T"), C("C"), D("D"), SM("SM"), SD("SD")
    }

    override fun toString(): String = when (type) {
        AddressType.X, AddressType.Y ->
            "${type.prefix}${number.toString(16).uppercase().padStart(3, '0')}"
        else -> "${type.prefix}$number"
    }

    companion object {
        fun parse(str: String): IOAddress? {
            val prefixes = AddressType.entries.sortedByDescending { it.prefix.length }
            for (addrType in prefixes) {
                if (str.startsWith(addrType.prefix, ignoreCase = true)) {
                    val numStr = str.removePrefix(addrType.prefix)
                    val radix = if (addrType == AddressType.X || addrType == AddressType.Y) 16 else 10
                    val num = numStr.toIntOrNull(radix) ?: continue
                    return IOAddress(addrType, num)
                }
            }
            return null
        }
    }
}
