package com.kontext.util

import android.content.Context
import android.util.Log
import com.kontext.data.local.KontextDatabase
import com.kontext.data.local.SessionManager
import com.kontext.domain.model.LanguageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedManager @Inject constructor(
    private val database: KontextDatabase,
    private val context: Context,
    private val sessionManager: SessionManager,
    private val csvParser: CsvParser,
    private val languageConfig: LanguageConfig
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            // Check if DB is already seeded
            val count = database.vocabCardDao().getCount()
            if (count > 4500) {
                Log.d("SeedManager", "Database already seeded with $count cards")
                return@withContext
            }
            
            if (count > 0) {
                Log.d("SeedManager", "Partial seed detected ($count cards), clearing...")
                database.vocabCardDao().deleteAll()
            }

            Log.d("SeedManager", "Starting database seeding for ${languageConfig.targetLanguage.displayName}...")

            // Use dynamic resource ID from LanguageConfig
            val inputStream = context.resources.openRawResource(languageConfig.csvResourceId)
            val cards = csvParser.parseVocabCsv(inputStream, sessionManager.getCurrentUserId())

            if (cards.isNotEmpty()) {
                // Batch insert for performance
                cards.chunked(500).forEach { chunk ->
                    database.vocabCardDao().insertAll(chunk)
                }
                Log.d("SeedManager", "Seeding complete. Inserted ${cards.size} ${languageConfig.targetLanguage.displayName} cards")
            } else {
                Log.e("SeedManager", "CSV parsing returned 0 cards for ${languageConfig.targetLanguage.displayName}")
            }

        } catch (e: Exception) {
            Log.e("SeedManager", "Error during seeding for ${languageConfig.targetLanguage.displayName}", e)
        }
    }
}
