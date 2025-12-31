package com.kontext.util

import android.util.Log
import com.kontext.data.local.entity.VocabCard
import com.kontext.domain.model.LanguageConfig
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvParser @Inject constructor(
    private val languageConfig: LanguageConfig
) {
    
    fun parseVocabCsv(
        inputStream: InputStream,
        userId: String,
        delimiter: String = "|"
    ): List<VocabCard> {
        val cards = mutableListOf<VocabCard>()
        
        inputStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            
            // Skip header if present
            if (line != null && isHeaderRow(line)) {
                line = reader.readLine()
            }
            
            while (line != null) {
                parseVocabLine(line, userId, delimiter)?.let { cards.add(it) }
                line = reader.readLine()
            }
        }
        
        return cards
    }
    
    private fun isHeaderRow(line: String): Boolean {
        return line.contains("German", ignoreCase = true) || 
               line.contains("English", ignoreCase = true) ||
               line.contains("Target", ignoreCase = true)
    }
    
    private fun parseVocabLine(line: String, userId: String, delimiter: String): VocabCard? {
        val parts = line.split(delimiter)
        if (parts.size < 4) {
            Log.w("CsvParser", "Skipping malformed CSV line: $line")
            return null
        }
        
        // CSV format: TargetLanguage|NativeLanguage|ExampleTarget|ExampleNative
        return VocabCard(
            targetLanguageTerm = parts[0].trim(),
            nativeLanguageTerm = parts[1].trim(),
            exampleSentenceTarget = parts[2].trim(),
            exampleSentenceNative = parts[3].trim(),
            languageCode = languageConfig.targetLanguage.code,
            nextReviewTimestamp = System.currentTimeMillis(),
            userId = userId
        )
    }
}
