package com.example.commentanalyzer

import android.app.Application

class CommentAnalyzerApplication : Application() {
    val container: AppContainer by lazy { ProductionAppContainer() }
}
