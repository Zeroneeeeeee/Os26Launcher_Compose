package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.controlcenter.AndroidControlPermissions
import gambi.global.os26launcher.data.controlcenter.AndroidSystemControls
import gambi.global.os26launcher.data.controlcenter.ControlCenterPrefsStore
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.controlcenter.ControlPermissionsPort
import gambi.global.os26launcher.domain.controlcenter.SystemControlsPort

/** G3d: nối hiện thực Trung tâm điều khiển vào các cổng của domain. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ControlCenterModule {
    // Scope @Singleton nằm trên từng lớp hiện thực, theo mẫu SearchModule.

    @Binds
    abstract fun bindSystemControls(impl: AndroidSystemControls): SystemControlsPort

    @Binds
    abstract fun bindControlCenterPrefs(impl: ControlCenterPrefsStore): ControlCenterPrefsRepository

    @Binds
    abstract fun bindControlPermissions(impl: AndroidControlPermissions): ControlPermissionsPort

    @Binds
    abstract fun bindMediaControls(impl: gambi.global.os26launcher.data.controlcenter.MediaSessionSource):
            gambi.global.os26launcher.domain.controlcenter.MediaControlsPort

    @Binds
    abstract fun bindBackgroundStore(impl: gambi.global.os26launcher.data.controlcenter.ControlCenterBackgroundFileStore):
            gambi.global.os26launcher.domain.controlcenter.ControlCenterBackgroundStore
}
