package com.kontext.di

import com.kontext.data.repository.AuthRepository
import com.kontext.data.repository.AuthRepositoryImpl
import com.kontext.data.repository.VocabRepository
import com.kontext.data.repository.VocabRepositoryImpl
import com.kontext.data.repository.StoryRepository
import com.kontext.data.repository.StoryRepositoryImpl
import com.kontext.data.repository.ImageRepository
import com.kontext.data.repository.ImageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVocabRepository(
        vocabRepositoryImpl: VocabRepositoryImpl
    ): VocabRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindStoryRepository(
        storyRepositoryImpl: StoryRepositoryImpl
    ): StoryRepository

    @Binds
    @Singleton
    abstract fun bindImageRepository(
        imageRepositoryImpl: ImageRepositoryImpl
    ): ImageRepository
}
