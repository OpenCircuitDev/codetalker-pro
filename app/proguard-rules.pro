# CCT-32 Task D.3 — ProGuard / R8 keep rules for release builds.
#
# AGP 8 + R8 are aggressive: anything not reflectively reachable will be
# stripped. We document each preserve block with the library it serves so
# future updates can prune obsolete rules.
#
# Source order:
#   - proguard-android-optimize.txt (system default)
#   - this file
#
# When in doubt, prefer narrow rules over -keep ** so the apk shrinks.

# -----------------------------------------------------------------------
# OkHttp + okhttp-sse — internals reflect onto SSE streams and platform
# trust managers. Keep the public types; suppress warnings on unused
# Conscrypt / org.ietf paths bundled by OkHttp.
# -----------------------------------------------------------------------
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.sse.** { *; }
-keep interface okhttp3.sse.** { *; }
-keep class okio.** { *; }

# -----------------------------------------------------------------------
# Media3 / ExoPlayer — MediaSource factories load by reflection.
# -----------------------------------------------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# -----------------------------------------------------------------------
# ZXing — reads BitMatrix internals via reflection during decode.
# -----------------------------------------------------------------------
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
-keep interface com.google.zxing.** { *; }

# -----------------------------------------------------------------------
# Compose runtime — most rules ship as consumer rules from AndroidX, but
# explicit @Composable + remember() preservation guards against the rare
# case where R8 over-strips.
# -----------------------------------------------------------------------
-keepclassmembers class androidx.compose.runtime.** { *; }
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.Composable { *; }
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------
# Sentry SDK — crash payload classes are loaded reflectively by the
# integration. Keep the public surface and the breadcrumb / event types.
# -----------------------------------------------------------------------
-dontwarn io.sentry.**
-keep class io.sentry.** { *; }
-keep interface io.sentry.** { *; }

# -----------------------------------------------------------------------
# org.json — DaemonClient uses JSONObject reflectively (mapToJson). The
# Android platform ships with org.json bundled; keep the type to be
# safe across vendor flavours.
# -----------------------------------------------------------------------
-keep class org.json.** { *; }
-dontwarn org.json.**

# -----------------------------------------------------------------------
# Kotlin coroutines — flow operators occasionally serialize stack traces
# that reference internal class names. Keep them so crash reports remain
# human-readable.
# -----------------------------------------------------------------------
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# -----------------------------------------------------------------------
# Project — preserve our own data classes that cross the wire boundary
# so JSONObject reflection sees the Kotlin field names. (JsonObject
# serialization is by-name; renamed fields would silently break the
# overlay endpoint.)
# -----------------------------------------------------------------------
-keep class dev.opencircuit.codetalker.net.** { *; }
-keep class dev.opencircuit.codetalker.prefs.** { *; }

# Don't strip Compose previews — they're @Preview-annotated functions
# and matter for tooling. (Not invoked at runtime; this is purely a
# safety net.)
-keep @androidx.compose.ui.tooling.preview.Preview class * { *; }

# -----------------------------------------------------------------------
# Final hardening — preserve generic signatures for OkHttp's reified
# generic type tokens.
# -----------------------------------------------------------------------
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
