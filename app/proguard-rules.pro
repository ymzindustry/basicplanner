# Basic Planner — R8 rules

# Apache POI (xlsx export) references desktop-only classes that do not exist on Android.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.xmlgraphics.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.math3.**
-dontwarn org.osgi.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn javax.xml.**
-dontwarn java.rmi.**

# POI is used reflectively in places; keep it wholesale so the export keeps working.
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }

# kotlinx.serialization: serializers are generated but the plugin's rules don't always cover
# models nested in other modules.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ymz.basicplanner.model.**$$serializer { *; }
-keepclassmembers class com.ymz.basicplanner.model.** {
    *** Companion;
}

# Chaquopy: the Python bridge is called from Kotlin by name.
-keep class com.chaquo.python.** { *; }

# Media3 / ExoPlayer player view used by the video preview.
-keep class androidx.media3.** { *; }

# Classes referenced by xmlbeans/POI that only exist on desktop toolchains.
-dontwarn com.github.javaparser.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**
-dontwarn de.rototor.pdfbox.**
