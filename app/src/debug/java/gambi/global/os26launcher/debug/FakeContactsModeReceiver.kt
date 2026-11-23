package gambi.global.os26launcher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * CP — CHỈ BẢN DEBUG. Đặt chế độ nguồn danh bạ giả:
 * `adb shell am broadcast -a gambi.global.os26launcher.CP_FAKE_MODE -p gambi.global.os26launcher --es mode off|small|load`
 * Receiver khai trong src/debug/AndroidManifest.xml với exported="true" (lý do ở đó).
 */
class FakeContactsModeReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun repository(): DebugContactsRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mode = FakeContactsSource.Mode.parse(intent.getStringExtra("mode"))
        EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).repository().setMode(mode)
    }
}
