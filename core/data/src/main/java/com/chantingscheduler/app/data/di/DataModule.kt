package com.chantingscheduler.app.data.di

import android.content.Context
import com.chantingscheduler.app.data.AppDatabase
import com.chantingscheduler.app.data.LocationStore
import com.chantingscheduler.app.data.OnboardingStore
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleDao
import com.chantingscheduler.app.data.SessionLogDao
import com.chantingscheduler.app.data.ThemeStore
import com.chantingscheduler.app.data.UpdateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and the SharedPreferences-backed stores. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.get(context)

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideSessionLogDao(db: AppDatabase): SessionLogDao = db.sessionLogDao()

    @Provides
    @Singleton
    fun provideLocationStore(@ApplicationContext context: Context): LocationStore =
        LocationStore.get(context)

    @Provides
    @Singleton
    fun provideThemeStore(@ApplicationContext context: Context): ThemeStore = ThemeStore.get(context)

    @Provides
    @Singleton
    fun provideOnboardingStore(@ApplicationContext context: Context): OnboardingStore =
        OnboardingStore.get(context)

    @Provides
    @Singleton
    fun providePlaybackSettingsStore(@ApplicationContext context: Context): PlaybackSettingsStore =
        PlaybackSettingsStore.get(context)

    @Provides
    @Singleton
    fun provideUpdateStore(@ApplicationContext context: Context): UpdateStore =
        UpdateStore.get(context)
}
