# Xposed hook entry — must not be obfuscated
-keep class org.pysh.janus.hook.HookEntry
-keep class org.pysh.janus.hook.WeatherCardHook { *; }

# WhitelistManager — accessed via XSharedPreferences reflection
-keep class org.pysh.janus.data.WhitelistManager { *; }

# Module activation detection method
-keep class org.pysh.janus.MainActivity {
    boolean isModuleActive();
}

# Services registered in manifest
-keep class org.pysh.janus.service.SwitchToRearTileService
-keep class org.pysh.janus.service.ScreenKeepAliveService
