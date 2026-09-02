-dontwarn javax.annotation.**
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { kotlinx.serialization.KSerializer serializer(...); }

-keepclassmembers class * extends com.cloudstreamextgen.models.** { *; }
