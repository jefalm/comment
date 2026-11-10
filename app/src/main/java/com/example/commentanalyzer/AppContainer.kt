package com.example.commentanalyzer

import com.example.commentanalyzer.domain.analysis.DeepAnalyzer
import com.example.commentanalyzer.domain.analysis.DisabledDeepAnalyzer
import com.example.commentanalyzer.domain.gate.DisabledSemanticGate
import com.example.commentanalyzer.domain.gate.SemanticGate

interface AppContainer {
    val semanticGate: SemanticGate
    val deepAnalyzer: DeepAnalyzer
}

class ProductionAppContainer : AppContainer {
    override val semanticGate: SemanticGate by lazy { DisabledSemanticGate() }
    override val deepAnalyzer: DeepAnalyzer by lazy { DisabledDeepAnalyzer() }
}
