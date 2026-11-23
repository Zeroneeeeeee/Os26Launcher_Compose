package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.photo.PhotoWidgetRepositoryImpl
import gambi.global.os26launcher.domain.photo.PhotoWidgetRepository

/** G3b — nối kho ảnh của widget Ảnh. Scope @Singleton nằm trên [PhotoWidgetRepositoryImpl]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoWidgetModule {
    @Binds
    abstract fun bindPhotoWidgetRepository(impl: PhotoWidgetRepositoryImpl): PhotoWidgetRepository
}
