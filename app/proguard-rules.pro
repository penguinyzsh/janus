# Xposed hook entry — must not be obfuscated
-keep class org.pysh.janus.hook.HookEntry
-keep class org.pysh.janus.hook.CardHook { *; }
-keep class org.pysh.janus.hook.MusicTemplatePatch { *; }

# Lyric injection — base class + all providers
-keep class org.pysh.janus.hook.LyricInjector { *; }
-keep class * extends org.pysh.janus.hook.LyricInjector { *; }
-keep class org.pysh.janus.hook.LunaMusicLyricHook { *; }
-keep class org.pysh.janus.hook.LyricParser { *; }
-keep class org.pysh.janus.hook.MamlConstants { *; }

# WhitelistManager — accessed via XSharedPreferences reflection
-keep class org.pysh.janus.data.WhitelistManager { *; }

# Module activation detection method
-keep class org.pysh.janus.MainActivity {
    boolean isModuleActive();
}

# Services registered in manifest
-keep class org.pysh.janus.service.SwitchToRearTileService
-keep class org.pysh.janus.service.ScreenKeepAliveService
