package gambi.global.os26launcher.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gambi.global.os26launcher.data.search.ContactsRepositoryImpl
import gambi.global.os26launcher.domain.repository.ContactsRepository

/**
 * PG4 — bản `perf` (đo hiệu năng): BẢN SAO của `src/release/.../di/ContactsModule.kt`, phải giữ khớp với bản đó. Build type `perf` dựng bằng
 * `initWith(release)` chỉ chép thiết lập, không chép thư mục nguồn `src/release/`, nên thiếu module này thì Hilt báo `MissingBinding`.
 * Như release: luôn đọc danh bạ thật. Scope @Singleton nằm trên ContactsRepositoryImpl.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsModule {
    @Binds
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository
}
