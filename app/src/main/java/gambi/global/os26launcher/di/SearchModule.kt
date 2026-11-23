package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.search.RecentAppsRepositoryImpl
import gambi.global.os26launcher.data.search.SearchQueryHistoryRepositoryImpl
import gambi.global.os26launcher.data.search.SystemSettingsRepositoryImpl
import gambi.global.os26launcher.data.search.TrendingSearchRepositoryImpl
import gambi.global.os26launcher.data.search.WebSuggestRepositoryImpl
import gambi.global.os26launcher.domain.repository.RecentAppsRepository
import gambi.global.os26launcher.domain.repository.SearchQueryHistoryRepository
import gambi.global.os26launcher.domain.repository.SystemSettingsRepository
import gambi.global.os26launcher.domain.repository.TrendingSearchRepository
import gambi.global.os26launcher.domain.repository.WebSuggestRepository

/** M8d — binding của các nguồn tìm kiếm, tách khỏi `RepositoryModule` (file dùng chung). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    // Scope @Singleton nằm trên SystemSettingsRepositoryImpl.
    @Binds
    abstract fun bindSystemSettingsRepository(impl: SystemSettingsRepositoryImpl): SystemSettingsRepository

    // Scope @Singleton nằm trên RecentAppsRepositoryImpl.
    @Binds
    abstract fun bindRecentAppsRepository(impl: RecentAppsRepositoryImpl): RecentAppsRepository

    // CP: binding ContactsRepository nằm ở ContactsModule của src/release và src/debug (nguồn danh bạ giả chỉ có ở debug).

    @Binds
    abstract fun bindSearchQueryHistoryRepository(impl: SearchQueryHistoryRepositoryImpl): SearchQueryHistoryRepository

    @Binds
    abstract fun bindWebSuggestRepository(impl: WebSuggestRepositoryImpl): WebSuggestRepository

    @Binds
    abstract fun bindTrendingSearchRepository(impl: TrendingSearchRepositoryImpl): TrendingSearchRepository
}
