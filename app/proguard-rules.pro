# Xposed hook entry — must not be obfuscated (libxposed XposedModule subclass)
-keep class org.pysh.janus.hook.HookEntry

# Rule engine — engines are referenced by name from JSON rules
-keep class org.pysh.janus.hook.engine.** { *; }

# Hook utilities — used by engines via reflection patterns
-keep class org.pysh.janus.hook.ReflectUtils { *; }
-keep class org.pysh.janus.hook.HookStatusReporter { *; }
-keep class org.pysh.janus.hook.ViewStateObserver { *; }

# Lyric injection — base class + all providers
-keep class org.pysh.janus.hook.LyricInjector { *; }
-keep class * extends org.pysh.janus.hook.LyricInjector { *; }
-keep class org.pysh.janus.hook.LyricParser { *; }

# Application class — XposedServiceHelper listener
-keep class org.pysh.janus.JanusApplication

# WhitelistManager — accessed from hook process
-keep class org.pysh.janus.data.WhitelistManager { *; }

# Module activation detection method
-keep class org.pysh.janus.MainActivity {
    boolean isModuleActive();
}

# Services registered in manifest
-keep class org.pysh.janus.service.SwitchToRearTileService
-keep class org.pysh.janus.service.ScreenKeepAliveService
