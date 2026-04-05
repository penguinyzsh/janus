package org.pysh.janus.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.DisplayUtils

class SwitchToRearTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val whitelistManager = WhitelistManager(applicationContext)
            val hasRearTask = DisplayUtils.getForegroundTaskId(DisplayUtils.BACK_DISPLAY) != null
            if (hasRearTask && watchJob?.isActive != true) {
                startWatching(whitelistManager)
            }
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    state = if (hasRearTask) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    label = getString(R.string.cast_tile_label)
                    subtitle = if (hasRearTask) getString(R.string.cast_tile_active) else null
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val whitelistManager = WhitelistManager(applicationContext)
            val rearTaskId = DisplayUtils.getForegroundTaskId(DisplayUtils.BACK_DISPLAY)
            if (rearTaskId != null) {
                // 背屏有任务 → 移回主屏
                val success = DisplayUtils.moveTaskToDisplay(rearTaskId, DisplayUtils.MAIN_DISPLAY)
                if (success) {
                    stopWatching(whitelistManager)
                }
                withContext(Dispatchers.Main) {
                    qsTile?.apply {
                        state = if (success) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                        subtitle = if (success) null else getString(R.string.cast_tile_failed)
                        updateTile()
                    }
                }
            } else {
                // 背屏没任务 → 把主屏前台移过去
                val mainTaskId = DisplayUtils.getForegroundTaskId(DisplayUtils.MAIN_DISPLAY)
                val success = mainTaskId != null && DisplayUtils.moveTaskToDisplay(mainTaskId, DisplayUtils.BACK_DISPLAY)
                if (success) {
                    val rotation = whitelistManager.getCastRotation()
                    if (rotation != 0) {
                        DisplayUtils.setRearRotation(rotation)
                    }
                    if (whitelistManager.isCastKeepAlive() && !ScreenKeepAliveService.isRunning) {
                        ScreenKeepAliveService.start(applicationContext, whitelistManager.getKeepAliveInterval())
                    }
                    startWatching(whitelistManager)
                }
                withContext(Dispatchers.Main) {
                    qsTile?.apply {
                        state = if (success) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        subtitle = if (success) getString(R.string.cast_tile_active) else getString(R.string.cast_tile_failed)
                        updateTile()
                    }
                }
            }
        }
    }

    private fun startWatching(whitelistManager: WhitelistManager) {
        watchJob?.cancel()
        watchJob =
            scope.launch {
                while (true) {
                    delay(2000)
                    val rearTask = DisplayUtils.getForegroundTaskId(DisplayUtils.BACK_DISPLAY)
                    if (rearTask == null) {
                        // 背屏应用已退出，自动还原
                        stopWatching(whitelistManager)
                        withContext(Dispatchers.Main) {
                            qsTile?.apply {
                                state = Tile.STATE_INACTIVE
                                subtitle = null
                                updateTile()
                            }
                        }
                        break
                    }
                }
            }
    }

    private fun stopWatching(whitelistManager: WhitelistManager) {
        watchJob?.cancel()
        watchJob = null
        DisplayUtils.resetRearRotation()
        // 仅当投屏常亮开启、且手动常亮未开启时，才停止 Service
        if (whitelistManager.isCastKeepAlive() && ScreenKeepAliveService.isRunning) {
            val manualKeepAlive = whitelistManager.isKeepAliveEnabled()
            if (!manualKeepAlive) {
                ScreenKeepAliveService.stop(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
