package com.kontext.di

import com.kontext.domain.algorithm.SM2Algorithm
import com.kontext.domain.algorithm.SpacedRepetitionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlgorithmModule {

    @Binds
    @Singleton
    abstract fun bindSpacedRepetitionEngine(
        impl: SM2Algorithm
    ): SpacedRepetitionEngine
}
