# Mantieni numeri di riga per stack trace leggibili su Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Modelli di dominio usati con Room e Firestore
-keep class it.manzolo.geojournal.domain.model.** { *; }
-keep class it.manzolo.geojournal.data.local.db.**Entity { *; }

# MapLibre: plugin annotations + JNI-backed classes — caricate via reflection
-keep class org.maplibre.android.** { *; }
-keep class com.mapbox.** { *; }
-dontwarn org.maplibre.android.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
