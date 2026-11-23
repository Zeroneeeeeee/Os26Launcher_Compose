package gambi.global.os26launcher.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.core.appwidget.OsAppWidgetHost
import javax.inject.Singleton

/** G3c: một [OsAppWidgetHost] cho cả app. */
@Module
@InstallIn(SingletonComponent::class)
object AppWidgetModule {
    @Provides
    @Singleton
    fun provideAppWidgetHost(@ApplicationContext context: Context): OsAppWidgetHost = OsAppWidgetHost(context)
}
