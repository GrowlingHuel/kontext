package com.kontext.di

import android.app.Application
import androidx.room.Room
import com.kontext.data.local.KontextDatabase
import com.kontext.data.local.dao.VocabCardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(app: Application): KontextDatabase {
        return Room.databaseBuilder(
            app,
            KontextDatabase::class.java,
            "kontext_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideVocabCardDao(db: KontextDatabase): VocabCardDao {
        return db.vocabCardDao()
    }
}
