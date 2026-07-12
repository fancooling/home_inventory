# Add project specific ProGuard rules here.
# Full R8/ProGuard tuning happens in Phase 7; this file exists so the release build type
# (isMinifyEnabled = true) has a rules file to reference.

# Keep Hilt-generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
