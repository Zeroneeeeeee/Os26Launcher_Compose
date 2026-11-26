package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.apps.AppGroupTitleProviderImpl
import gambi.global.os26launcher.data.apps.AppLabelRepositoryImpl
import gambi.global.os26launcher.data.apps.AppRepositoryImpl
import gambi.global.os26launcher.data.apps.DefaultAppResolver
import gambi.global.os26launcher.data.apps.ShortcutRepositoryImpl
import gambi.global.os26launcher.data.backdrop.BackdropRepositoryImpl
import gambi.global.os26launcher.data.layout.LayoutRepositoryImpl
import gambi.global.os26launcher.data.system.DefaultLauncherRepositoryImpl
import gambi.global.os26launcher.domain.repository.AppLabelRepository
import gambi.global.os26launcher.domain.repository.AppRepository
import gambi.global.os26launcher.domain.repository.BackdropRepository
import gambi.global.os26launcher.domain.repository.DefaultAppResolverPort
import gambi.global.os26launcher.domain.repository.DefaultLauncherRepository
import gambi.global.os26launcher.domain.repository.LayoutRepository
import gambi.global.os26launcher.domain.repository.ShortcutRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // TV-3: widget của Today View. Scope @Singleton nằm trên TodayWidgetsStore.
    @Binds
    abstract fun bindTodayWidgetsRepository(
        impl: gambi.global.os26launcher.data.today.TodayWidgetsStore
    ): gambi.global.os26launcher.domain.today.TodayWidgetsRepository

    // AG: tên thư mục nhóm (seed + Cài đặt → Chung). Không trạng thái, không cần scope.
    @Binds
    abstract fun bindAppGroupTitleProvider(
        impl: AppGroupTitleProviderImpl
    ): gambi.global.os26launcher.domain.category.AppGroupTitleProvider

    // Scope @Singleton nằm trên BackdropRepositoryImpl.
    @Binds
    abstract fun bindBackdropRepository(impl: BackdropRepositoryImpl): BackdropRepository

    // Scope @Singleton nằm trên DefaultLauncherRepositoryImpl.
    @Binds
    abstract fun bindDefaultLauncherRepository(impl: DefaultLauncherRepositoryImpl): DefaultLauncherRepository

    // Scope @Singleton nằm trên AppRepositoryImpl.
    @Binds
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    // Scope @Singleton nằm trên AppLabelRepositoryImpl. (RN)
    @Binds
    abstract fun bindAppLabelRepository(impl: AppLabelRepositoryImpl): AppLabelRepository

    // Scope @Singleton nằm trên CustomIconStore. (P3-4)
    @Binds
    abstract fun bindCustomIconRepository(impl: gambi.global.os26launcher.data.apps.CustomIconStore): gambi.global.os26launcher.domain.repository.CustomIconRepository

    // Scope @Singleton nằm trên AppWidgetRepositoryImpl. (G3c)
    @Binds
    abstract fun bindAppWidgetRepository(impl: gambi.global.os26launcher.data.appwidget.AppWidgetRepositoryImpl): gambi.global.os26launcher.domain.repository.AppWidgetRepository

    // Scope @Singleton nằm trên LayoutRepositoryImpl. (M4 Task 2)
    @Binds
    abstract fun bindLayoutRepository(impl: LayoutRepositoryImpl): LayoutRepository

    // Scope @Singleton nằm trên ShortcutRepositoryImpl. (M7 Task 10)
    @Binds
    abstract fun bindShortcutRepository(impl: ShortcutRepositoryImpl): ShortcutRepository

    // Scope @Singleton nằm trên IconPackManager. (IP)
    @Binds
    abstract fun bindIconPackRepository(impl: gambi.global.os26launcher.data.apps.IconPackManager): gambi.global.os26launcher.domain.repository.IconPackRepository

    // Scope @Singleton nằm trên NotificationBadgeStore. (BG)
    @Binds
    abstract fun bindNotificationBadgeRepository(impl: gambi.global.os26launcher.data.notification.NotificationBadgeStore): gambi.global.os26launcher.domain.repository.NotificationBadgeRepository

    // Scope @Singleton nằm trên DefaultAppResolver. (M3 Task 4)
    @Binds
    abstract fun bindDefaultAppResolverPort(impl: DefaultAppResolver): DefaultAppResolverPort
}
