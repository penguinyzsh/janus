package org.pysh.janus.hook.engine

import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface
import org.pysh.janus.core.model.HookRule

/**
 * Interface for complex hook engine plugins.
 * Engine plugins handle multi-hook modules with shared state and complex orchestration.
 * JSON rules provide target class/method names; all logic lives in the engine code.
 */
interface HookEnginePlugin {

    /**
     * Install all hooks for this engine.
     *
     * @param module the XposedInterface for hook registration
     * @param rule the rule containing `targets` map with class/method names
     * @param classLoader the target app's class loader
     * @param config RemotePreferences for reading configuration
     */
    fun install(
        module: XposedInterface,
        rule: HookRule,
        classLoader: ClassLoader,
        config: SharedPreferences,
    )
}
