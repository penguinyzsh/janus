package org.pysh.janus.util

import org.pysh.janus.core.util.RootUtils

object DisplayUtils {
    const val MAIN_DISPLAY = 0
    const val BACK_DISPLAY = 1

    private val ROOT_TASK_REGEX = Regex("RootTask id=(\\d+).*displayId=(\\d+)")
    private val TASK_VISIBLE_REGEX = Regex("taskId=(\\d+): (\\S+).*visible=true")
    private val OVERRIDE_DENSITY_REGEX = Regex("Override density: (\\d+)")
    private val PHYSICAL_DENSITY_REGEX = Regex("Physical density: (\\d+)")

    private val HOME_PACKAGES =
        setOf(
            "com.xiaomi.subscreencenter",
            "com.miui.home",
        )

    fun moveCurrentAppToBackScreen(): Boolean {
        val taskId = getForegroundTaskId(MAIN_DISPLAY) ?: return false
        return moveTaskToDisplay(taskId, BACK_DISPLAY)
    }

    fun moveTaskToDisplay(
        taskId: Int,
        displayId: Int,
    ): Boolean = RootUtils.exec("am display move-stack $taskId $displayId")

    fun getForegroundTaskId(displayId: Int): Int? {
        val output = RootUtils.execWithOutput("am stack list") ?: return null
        var currentDisplayId = -1
        for (line in output.lines()) {
            val rootMatch = ROOT_TASK_REGEX.find(line)
            if (rootMatch != null) {
                currentDisplayId = rootMatch.groupValues[2].toIntOrNull() ?: -1
                continue
            }
            if (currentDisplayId == displayId) {
                val taskMatch = TASK_VISIBLE_REGEX.find(line)
                if (taskMatch != null) {
                    val pkg = taskMatch.groupValues[2].substringBefore("/")
                    if (pkg !in HOME_PACKAGES) {
                        return taskMatch.groupValues[1].toIntOrNull()
                    }
                }
            }
        }
        return null
    }

    fun setRearRotation(rotation: Int): Boolean =
        if (rotation == 0) {
            resetRearRotation()
        } else {
            RootUtils.exec("wm fixed-to-user-rotation -d $BACK_DISPLAY enabled") &&
                RootUtils.exec("wm user-rotation -d $BACK_DISPLAY lock $rotation")
        }

    fun resetRearRotation(): Boolean =
        RootUtils.exec("wm user-rotation -d $BACK_DISPLAY free") &&
            RootUtils.exec("wm fixed-to-user-rotation -d $BACK_DISPLAY default")

    fun getRearDpi(): Int? {
        val output =
            RootUtils.execWithOutput("wm density -d $BACK_DISPLAY")
                ?: return null
        val overrideMatch = OVERRIDE_DENSITY_REGEX.find(output)
        if (overrideMatch != null) return overrideMatch.groupValues[1].toIntOrNull()
        val physicalMatch = PHYSICAL_DENSITY_REGEX.find(output)
        return physicalMatch?.groupValues[1]?.toIntOrNull()
    }

    fun setRearDpi(dpi: Int): Boolean = RootUtils.exec("wm density $dpi -d $BACK_DISPLAY")

    fun resetRearDpi(): Boolean = RootUtils.exec("wm density reset -d $BACK_DISPLAY")
}
