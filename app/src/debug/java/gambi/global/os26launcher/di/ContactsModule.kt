package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.debug.DebugContactsRepository
import gambi.global.os26launcher.domain.repository.ContactsRepository

/** CP — CHỈ BẢN DEBUG: chọn giữa danh bạ thật và nguồn giả. Bản release có module cùng tên ở src/release. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsModule {
    @Binds
    abstract fun bindContactsRepository(impl: DebugContactsRepository): ContactsRepository
}
