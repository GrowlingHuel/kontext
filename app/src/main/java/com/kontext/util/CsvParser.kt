package com.kontext.util

import com.kontext.data.local.entity.VocabCard
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvParser {
    fun parse(inputStream: InputStream, userId: String): List<VocabCard> {
        val cards = mutableListOf<VocabCard>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        try {
            // Skip header if present, or just assume first line is data if we can't be sure.
            // For now, assuming standard CSV format: German,English,ExampleDE,ExampleEN
            // Skipping header for safety if first line contains "German"
            var line = reader.readLine()
            if (line != null && line.contains("German", ignoreCase = true)) {
                line = reader.readLine()
            }

            while (line != null) {
                parseLine(line, userId)?.let { cards.add(it) }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
        }
        return cards
    }
    
    private fun parseLine(line: String, userId: String): VocabCard? {
        // Simple manual CSV parsing (splitting by comma). 
        // In a real app, use a CSV library to handle quotes/commas inside fields.
        // Assuming clean data for tracer bullet.
        // Using pipe | as delimiter
        val parts = line.split("|")
        if (parts.size < 4) return null
        
        return VocabCard(
            germanTerm = parts[0].trim(),
            englishTerm = parts[1].trim(),
            exampleSentenceGerman = parts[2].trim(),
            exampleSentenceEnglish = parts[3].trim(),
            masteryLevel = 0,
            nextReviewTimestamp = System.currentTimeMillis(),
            userId = userId
        )
    }
}
