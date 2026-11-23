package gambi.global.os26launcher.ui.controlcenter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import gambi.global.os26launcher.R
import gambi.global.os26launcher.core.designsystem.compose.DsRow
import gambi.global.os26launcher.core.designsystem.compose.DsRowTrailing
import gambi.global.os26launcher.core.designsystem.compose.DsScreen
import gambi.global.os26launcher.core.ui.reassertEdgeToEdge
import gambi.global.os26launcher.domain.controlcenter.ControlCenterPrefsRepository
import gambi.global.os26launcher.domain.model.controlcenter.ControlCenterConfig
import gambi.global.os26launcher.domain.model.controlcenter.EdgeConfig
import gambi.global.os26launcher.domain.model.controlcenter.EdgePosition
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * G3d: chỉnh dải mép (RE mục 5.4, `CustomPositionActivity`): vị trí, độ dài, độ dày, khoảng cách từ đỉnh (dải cạnh) và màu vạch.
 * Mỗi thay đổi ghi ngay; service đang theo dõi cài đặt nên dải thật trên màn hình đổi theo, người dùng thấy kết quả tại chỗ.
 *
 * JS7: Compose (`DsScreen`, nhóm kính, hàng radio DS, thanh trượt DS) thay `CcForm` + Material.
 */
@AndroidEntryPoint
class EdgeSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: ControlCenterPrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val config by prefs.config.collectAsStateWithLifecycle(initialValue = ControlCenterConfig())
            val e = config.edge
            DsScreen(title = stringResource(R.string.cc_edge_settings), onBack = ::finish) {
                CcFormColumn(Modifier.weight(1f)) {
                    CcHeader(stringResource(R.string.cc_edge_position))
                    CcGroup {
                        EdgePosition.entries.forEachIndexed { i, p ->
                            DsRow(
                                title = stringResource(p.labelRes()),
                                onClick = { if (p != e.position) save { it.copy(position = p) } },
                                trailing = DsRowTrailing.Radio(p == e.position),
                                showDivider = i < EdgePosition.entries.lastIndex
                            )
                        }
                    }
                    CcHeader(stringResource(R.string.cc_edge_shape))
                    CcGroup {
                        CcSlider(stringResource(R.string.cc_edge_length), e.lengthFraction, EdgeConfig.MIN_LENGTH, EdgeConfig.MAX_LENGTH) { v ->
                            save { it.copy(lengthFraction = v) }
                        }
                        CcSlider(
                            stringResource(R.string.cc_edge_thickness), e.thicknessDp.toFloat(),
                            EdgeConfig.MIN_THICKNESS_DP.toFloat(), EdgeConfig.MAX_THICKNESS_DP.toFloat()
                        ) { v -> save { it.copy(thicknessDp = Math.round(v)) } }
                    }
                    if (e.position == EdgePosition.LEFT_SIDE || e.position == EdgePosition.RIGHT_SIDE) {
                        CcGroup {
                            CcSlider(stringResource(R.string.cc_edge_offset), e.offsetFraction, 0f, EdgeConfig.MAX_OFFSET) { v ->
                                save { it.copy(offsetFraction = v) }
                            }
                        }
                    }
                    CcHeader(stringResource(R.string.cc_edge_color))
                    CcGroup {
                        val desc = stringResource(R.string.cc_edge_color)
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            for (c in EDGE_COLORS) CcSwatch(c, c == e.color, desc) { save { it.copy(color = c) } }
                        }
                    }
                    CcNote(stringResource(R.string.cc_edge_note))
                }
            }
        }
        reassertEdgeToEdge(window)
    }

    private fun save(transform: (EdgeConfig) -> EdgeConfig) {
        lifecycleScope.launch { prefs.update { it.copy(edge = transform(it.edge)) } }
    }

    private companion object {
        /** Trắng mờ (mặc định), xám, xanh, xanh lá, cam, hồng; alpha ~35% như vạch iOS. */
        val EDGE_COLORS = listOf(
            EdgeConfig.DEFAULT_COLOR, 0x598E8E93, 0x590A84FF, 0x5930D158, 0x59FF9F0A, 0x59FF375F
        )
    }
}
