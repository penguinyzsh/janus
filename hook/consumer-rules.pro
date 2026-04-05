# Xposed module entry point — libxposed uses reflection via META-INF/xposed/java_init.list
# to instantiate the module class. Without these keep rules R8 would strip it.
-keep class org.pysh.janus.hook.HookEntry {
    public <init>(...);
    public *;
}

# Keep any class referenced from META-INF/xposed/java_init.list (defensive).
-keepclassmembers class ** {
    @io.github.libxposed.api.annotations.** *;
}

# Keep libxposed API contracts as-is so reflection-based dispatch works.
-keep class io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.api.**
