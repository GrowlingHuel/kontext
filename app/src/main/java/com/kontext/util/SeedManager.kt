package com.kontext.util

import android.content.Context
import android.util.Log
import com.kontext.R
import com.kontext.data.local.KontextDatabase
import com.kontext.data.local.entity.VocabCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedManager @Inject constructor(
    private val database: KontextDatabase,
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("kontext_prefs", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            // 1. Check if DB is already seeded (using Room count as source of truth)
            val count = database.vocabCardDao().getCount()
            if (count > 4500) {
                Log.d("SeedManager", "Database already fully seeded with $count cards.")
                return@withContext
            }
            
            if (count > 0) {
                Log.d("SeedManager", "Partial seed detected ($count cards). Clearing for full seed...")
                database.vocabCardDao().deleteAll()
            }

            Log.d("SeedManager", "Starting database seeding...")

            // 2. Read CSV
            val inputStream = context.resources.openRawResource(R.raw.german_4000)
            val cards = parseCsv(inputStream)

            if (cards.isNotEmpty()) {
                // 3. Batch Insert
                cards.chunked(500).forEach { chunk ->
                    database.vocabCardDao().insertAll(chunk)
                }
                Log.d("SeedManager", "Seeding complete. Inserted ${cards.size} cards.")
            } else {
                Log.e("SeedManager", "CSV parsing returned 0 cards.")
            }

        } catch (e: Exception) {
            Log.e("SeedManager", "Error during seeding", e)
        }
    }

    private fun parseCsv(inputStream: InputStream): List<VocabCard> {
        val cards = mutableListOf<VocabCard>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        try {
            var line = reader.readLine()
            
            // Skip Header if present
            // Checking if first line is a header by looking for "German" or "English"
            if (line != null && (line.contains("German", ignoreCase = true) || line.contains("English", ignoreCase = true))) {
                line = reader.readLine()
            }

            while (line != null) {
                val parts = line.split("|") // Using pipe as requested delimiter
                // Expected format: German|English|ExampleDE|ExampleEN
                if (parts.size >= 4) {
                    cards.add(
                        VocabCard(
                            germanTerm = parts[0].trim(),
                            englishTerm = parts[1].trim(),
                            exampleSentenceGerman = parts[2].trim(),
                            exampleSentenceEnglish = parts[3].trim(),
                            nextReviewTimestamp = System.currentTimeMillis(),
                            userId = "local_user" // Default for now
                        )
                    )
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.e("SeedManager", "CSV Parsing Error", e)
        } finally {
            reader.close()
        }
        return cards
    }
}
