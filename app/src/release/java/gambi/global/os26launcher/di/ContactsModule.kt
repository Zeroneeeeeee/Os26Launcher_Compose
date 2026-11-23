package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.search.ContactsRepositoryImpl
import gambi.global.os26launcher.domain.repository.ContactsRepository

/** CP — bản release: luôn đọc danh bạ thật. Bản debug có module cùng tên ở src/debug. Scope @Singleton nằm trên ContactsRepositoryImpl. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsModule {
    @Binds
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository
}
