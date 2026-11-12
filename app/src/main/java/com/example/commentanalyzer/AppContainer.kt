package com.example.commentanalyzer

import com.example.commentanalyzer.data.local.AnalysisOrchestrator
import com.example.commentanalyzer.data.local.AnalysisPersistenceRepository
import com.example.commentanalyzer.data.local.AnalysisReadRepository
import com.example.commentanalyzer.data.local.AnalysisRunResult
import com.example.commentanalyzer.data.local.AppDatabase
import com.example.commentanalyzer.data.local.RemoteProcessingReadiness
import com.example.commentanalyzer.data.local.entity.ProcessingConfigEntity
import com.example.commentanalyzer.domain.analysis.DeepAnalyzer
import com.example.commentanalyzer.domain.gate.SemanticGate
import com.example.commentanalyzer.network.adapter.OpenRouterGlmDeepAnalyzer
import com.example.commentanalyzer.network.adapter.OpenRouterJevSemanticGate
import com.example.commentanalyzer.network.glm.GlmApiService
import com.example.commentanalyzer.network.jev.JevApiService

interface AppContainer {
    val semanticGate: SemanticGate
    val deepAnalyzer: DeepAnalyzer
}

interface AnalysisAppContainer : AppContainer {
    val analysisOrchestrator: AnalysisOrchestrator
    val analysisReadRepository: AnalysisReadRepository
    val synthesisOrchestrator: com.example.commentanalyzer.domain.synthesis.SynthesisOrchestrator
    suspend fun runAnalysis(importId: String, configVersion: Long): AnalysisRunResult
}

class ProductionAppContainer internal constructor(
    private val database: AppDatabase,
    private val openRouterKeyProvider: () -> String?,
    private val remoteProcessingConsentProvider: () -> Boolean,
    jevApiService: JevApiService = JevApiService.create(tokenProvider = openRouterKeyProvider),
    glmApiService: GlmApiService = GlmApiService.create(tokenProvider = openRouterKeyProvider)
) : AnalysisAppContainer {
    override val semanticGate: SemanticGate by lazy {
        OpenRouterJevSemanticGate(jevApiService, openRouterKeyProvider)
    }
    override val deepAnalyzer: DeepAnalyzer by lazy {
        OpenRouterGlmDeepAnalyzer(glmApiService, openRouterKeyProvider)
    }
    val synthesisDeepAnalyzer: com.example.commentanalyzer.domain.analysis.SynthesisDeepAnalyzer by lazy {
        com.example.commentanalyzer.network.adapter.OpenRouterGlmSynthesisAdapter(
            glmApiService = glmApiService,
            tokenProvider = openRouterKeyProvider
        )
    }
    override val synthesisOrchestrator: com.example.commentanalyzer.domain.synthesis.SynthesisOrchestrator by lazy {
        com.example.commentanalyzer.domain.synthesis.SynthesisOrchestrator(
            database = database,
            synthesisAnalyzer = synthesisDeepAnalyzer
        )
    }
    val analysisPersistenceRepository: AnalysisPersistenceRepository by lazy {
        AnalysisPersistenceRepository(database)
    }
    override val analysisOrchestrator: AnalysisOrchestrator by lazy {
        AnalysisOrchestrator(
            database = database,
            persistence = analysisPersistenceRepository,
            semanticGate = semanticGate,
            deepAnalyzer = deepAnalyzer,
            readiness = {
                RemoteProcessingReadiness(
                    hasOpenRouterKey = !openRouterKeyProvider().isNullOrBlank(),
                    remoteProcessingConsent = remoteProcessingConsentProvider()
                )
            }
        )
    }
    override val analysisReadRepository: AnalysisReadRepository by lazy {
        AnalysisReadRepository(
            commentDao = database.commentDao(),
            importDao = database.importDao(),
            processingBatchDao = database.processingBatchDao(),
            commentSynthesisDao = database.commentSynthesisDao()
        )
    }

    override suspend fun runAnalysis(importId: String, configVersion: Long): AnalysisRunResult {
        val config = database.processingConfigDao().getConfigByVersion(configVersion)
            ?: ProcessingConfigEntity(
                configVersion = configVersion,
                jevModelRequested = "typesafe/jev-1.14",
                jevRejectThreshold = 0.3,
                jevPassThreshold = 0.7,
                glmModelRequested = "z-ai/glm-5.3-flash",
                youtubeKeyCiphertext = null,
                openrouterKeyCiphertext = null,
                openrouterReferer = null,
                createdAt = System.currentTimeMillis(),
                isActive = true
            )
        return analysisOrchestrator.run(importId, config)
    }
}
