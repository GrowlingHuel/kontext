package com.kontext.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kontext.data.local.dao.VocabCardDao
import com.kontext.data.local.entity.VocabCard

@Database(entities = [VocabCard::class], version = 1, exportSchema = false)
abstract class KontextDatabase : RoomDatabase() {
    abstract fun vocabCardDao(): VocabCardDao
}
