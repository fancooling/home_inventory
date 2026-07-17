package com.homeinventory.app.di

import android.content.Context
import androidx.room.Room
import com.homeinventory.app.data.db.HomeInventoryDatabase
import com.homeinventory.app.data.db.ScanCheckpointDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HomeInventoryDatabase =
        Room.databaseBuilder(
            context,
            HomeInventoryDatabase::class.java,
            HomeInventoryDatabase.NAME,
        ).build()

    @Provides
    fun provideScanCheckpointDao(db: HomeInventoryDatabase): ScanCheckpointDao =
        db.scanCheckpointDao()
}
