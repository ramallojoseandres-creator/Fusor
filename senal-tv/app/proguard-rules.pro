# SEÑAL TV — keep Retrofit/Kotlin serialization models
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep class com.senal.tv.data.model.** { *; }
