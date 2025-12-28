package com.kontext.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kontext.data.local.dao.VocabCardDao
import com.kontext.data.local.entity.VocabCard

import com.kontext.data.local.dao.StoryDao
import com.kontext.data.local.entity.StoryEntity

@Database(entities = [VocabCard::class, StoryEntity::class], version = 2, exportSchema = false)
abstract class KontextDatabase : RoomDatabase() {
    abstract fun vocabCardDao(): VocabCardDao
    abstract fun storyDao(): StoryDao
}
