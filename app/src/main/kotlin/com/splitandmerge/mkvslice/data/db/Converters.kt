package com.splitandmerge.mkvslice.data.db

import androidx.room.TypeConverter
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.domain.model.SplitMode

class Converters {
    @TypeConverter
    fun fromJobStatus(v: JobStatus): String = v.name

    @TypeConverter
    fun toJobStatus(v: String): JobStatus = JobStatus.valueOf(v)

    @TypeConverter
    fun fromJobType(v: JobType): String = v.name

    @TypeConverter
    fun toJobType(v: String): JobType = JobType.valueOf(v)

    @TypeConverter
    fun fromPartStatus(v: PartStatus): String = v.name

    @TypeConverter
    fun toPartStatus(v: String): PartStatus = PartStatus.valueOf(v)

    @TypeConverter
    fun fromSplitMode(v: SplitMode?): String? = v?.name

    @TypeConverter
    fun toSplitMode(v: String?): SplitMode? = v?.let { SplitMode.valueOf(it) }
}
