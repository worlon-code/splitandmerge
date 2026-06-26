package com.splitandmerge.mkvslice.domain.defaulttracks.model

data class RegionEdit(
    val originalOffset: Long,
    val originalBytes: ByteArray,
    val replacementBytes: ByteArray,
    val description: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegionEdit) return false
        if (originalOffset != other.originalOffset) return false
        if (!originalBytes.contentEquals(other.originalBytes)) return false
        if (!replacementBytes.contentEquals(other.replacementBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = originalOffset.hashCode()
        result = 31 * result + originalBytes.contentHashCode()
        result = 31 * result + replacementBytes.contentHashCode()
        return result
    }
}

data class EditPlan(
    val fileEdits: List<RegionEdit>,
    val writeStrategy: WriteStrategy,
    val skipReason: String? = null
)
