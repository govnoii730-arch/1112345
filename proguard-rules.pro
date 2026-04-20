# Keep mod entrypoint and mixins referenced from json resources
-keep class ru.mytheria.Mytheria { *; }
-keep class ru.mytheria.mixin.** { *; }

# Keep annotations and signatures required by mixins/event bus
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep event handler methods for runtime event wiring
-keepclassmembers class * {
    @meteordevelopment.orbit.EventHandler <methods>;
}

# Keep source resource mappings valid
-adaptresourcefilecontents fabric.mod.json,mytheria.mixins.json

# Obfuscation profile: keep behavior, avoid risky optimizations
-dontoptimize
-dontshrink
-dontpreverify
-useuniqueclassmembernames

# Reduce noisy warnings from game/runtime classpath
-dontwarn **
