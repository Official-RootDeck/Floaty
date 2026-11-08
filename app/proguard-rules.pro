# Keep runtime metadata used by Kotlin, AndroidX, and Jetpack Compose.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Android framework entry points declared in AndroidManifest.xml are retained by AGP/R8.
# Avoid broad java.security/javax.crypto keep rules and blanket Compose dontwarn rules;
# those suppress useful diagnostics and are not required by this project.
