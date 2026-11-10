package com.example.commentanalyzer.data.local.converter

import androidx.room.TypeConverter
import com.example.commentanalyzer.domain.model.SourceType
import com.example.commentanalyzer.data.local.entity.*

class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? {
        if (value == null) return null
        return SourceType.valueOf(value)
    }

    @TypeConverter
    fun fromImportStatus(value: ImportStatus?): String? = value?.name

    @TypeConverter
    fun toImportStatus(value: String?): ImportStatus? {
        if (value == null) return null
        return ImportStatus.valueOf(value)
    }

    @TypeConverter
    fun fromProcessingState(value: ProcessingState?): String? = value?.name

    @TypeConverter
    fun toProcessingState(value: String?): ProcessingState? {
        if (value == null) return null
        return ProcessingState.valueOf(value)
    }

    @TypeConverter
    fun fromProcessingStage(value: ProcessingStage?): String? = value?.name

    @TypeConverter
    fun toProcessingStage(value: String?): ProcessingStage? {
        if (value == null) return null
        return ProcessingStage.valueOf(value)
    }

    @TypeConverter
    fun fromBatchStatus(value: BatchStatus?): String? = value?.name

    @TypeConverter
    fun toBatchStatus(value: String?): BatchStatus? {
        if (value == null) return null
        return BatchStatus.valueOf(value)
    }

    @TypeConverter
    fun fromBatchItemStatus(value: BatchItemStatus?): String? = value?.name

    @TypeConverter
    fun toBatchItemStatus(value: String?): BatchItemStatus? {
        if (value == null) return null
        return BatchItemStatus.valueOf(value)
    }

    @TypeConverter
    fun fromTimestampPrecision(value: TimestampPrecision?): String? = value?.name

    @TypeConverter
    fun toTimestampPrecision(value: String?): TimestampPrecision? {
        if (value == null) return null
        return TimestampPrecision.valueOf(value)
    }

    @TypeConverter
    fun fromResultStage(value: ResultStage?): String? = value?.name

    @TypeConverter
    fun toResultStage(value: String?): ResultStage? {
        if (value == null) return null
        return ResultStage.valueOf(value)
    }
}
