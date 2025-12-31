package com.kontext.di

import com.kontext.data.local.SessionManager
import com.kontext.domain.model.Language
import com.kontext.domain.model.LanguageConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides language configuration for the application.
 * Reads the selected language from SessionManager.
 */
@Module
@InstallIn(SingletonComponent::class)
object LanguageModule {
    
    @Provides
    @Singleton
    fun provideLanguageConfig(sessionManager: SessionManager): LanguageConfig {
        val languageCode = sessionManager.getLanguageCode()
        val language = Language.fromCode(languageCode) ?: Language.GERMAN
        
        return when (language) {
            Language.GERMAN -> LanguageConfig.forGerman()
            Language.SPANISH -> LanguageConfig.forSpanish()
            Language.FRENCH -> LanguageConfig.forFrench()
        }
    }
}
