package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.config.AppStateRepositoryImpl
import gambi.global.os26launcher.data.config.AppearanceRepositoryImpl
import gambi.global.os26launcher.data.config.GeneralConfigRepositoryImpl
import gambi.global.os26launcher.data.config.HomeScreenConfigRepositoryImpl
import gambi.global.os26launcher.data.config.MotionConfigRepositoryImpl
import gambi.global.os26launcher.data.config.SearchConfigRepositoryImpl
import gambi.global.os26launcher.domain.config.AppStateRepository
import gambi.global.os26launcher.domain.config.AppearanceRepository
import gambi.global.os26launcher.domain.config.GeneralConfigRepository
import gambi.global.os26launcher.domain.config.HomeScreenConfigRepository
import gambi.global.os26launcher.domain.config.MotionConfigRepository
import gambi.global.os26launcher.domain.config.SearchConfigRepository

/** CF — repository các nhóm config trên DataStore `launcher_settings` (`docs/config-schema.md`). Scope @Singleton nằm trên từng impl. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigModule {

    @Binds
    abstract fun bindAppearanceRepository(impl: AppearanceRepositoryImpl): AppearanceRepository

    @Binds
    abstract fun bindGeneralConfigRepository(impl: GeneralConfigRepositoryImpl): GeneralConfigRepository

    @Binds
    abstract fun bindHomeScreenConfigRepository(impl: HomeScreenConfigRepositoryImpl): HomeScreenConfigRepository

    @Binds
    abstract fun bindSearchConfigRepository(impl: SearchConfigRepositoryImpl): SearchConfigRepository

    @Binds
    abstract fun bindMotionConfigRepository(impl: MotionConfigRepositoryImpl): MotionConfigRepository

    @Binds
    abstract fun bindAppStateRepository(impl: AppStateRepositoryImpl): AppStateRepository
}
