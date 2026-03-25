package com.example.mpsbuilder.di

import com.example.mpsbuilder.data.repository.WorkbenchLayoutRepository
import com.example.mpsbuilder.data.repository.WorkbenchLayoutRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkbenchModule {

    @Binds
    @Singleton
    abstract fun bindWorkbenchLayoutRepository(
        impl: WorkbenchLayoutRepositoryImpl
    ): WorkbenchLayoutRepository
}
