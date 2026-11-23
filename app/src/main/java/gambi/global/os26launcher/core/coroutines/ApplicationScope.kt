package gambi.global.os26launcher.core.coroutines

import javax.inject.Qualifier

/** CoroutineScope sống bằng tuổi process, không bao giờ cancel. Dùng cho flow dùng chung qua stateIn. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
