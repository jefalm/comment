package com.example.commentanalyzer.ingestion

import com.example.commentanalyzer.domain.model.ImportProgress
import com.example.commentanalyzer.domain.model.ImportRequest

interface CommentImporter {
    suspend fun import(request: ImportRequest): ImportProgress
}
