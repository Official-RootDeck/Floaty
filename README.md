Floaty

Floaty is an Android application developed by RootDeck, built with Kotlin and modern Android development tools.

Designed to support a wide range of Android devices, Floaty targets Android 5.0 (Lollipop) through Android 16.

<p align="center">
  <b>Developed by RootDeck</b><br>
  <a href="https://github.com/Official-RootDeck">GitHub Organization</a>
</p>---

Compatibility

Configuration| Version
Minimum Android| Android 5.0 (Lollipop)
Minimum SDK| API 21
Target SDK| API 36
Compile SDK| API 36
Application ID| "com.rootdeck.floaty"
Programming Language| Kotlin
Build System| Gradle
Android Gradle Plugin| 9.3.1
Kotlin| 2.4.21
Gradle| 9.7.0

Features

- Native Android application built with Kotlin.
- Broad Android compatibility (API 21–36).
- Modern Android build configuration.
- Release build with R8 code shrinking and optimization.
- Permission handling for Android platform requirements.
- ProGuard configuration for release builds.

See the application source code for the complete implementation and functionality.

Screenshots

Screenshots will be added here.

Getting Started

Requirements

- Android Studio with a compatible Android SDK.
- JDK compatible with Gradle 9.7.0.
- Android SDK Platform 36.
- Internet connection for downloading Gradle dependencies.

Clone the repository

git clone https://github.com/Official-RootDeck/Floaty.git

cd Floaty

Build Debug APK

Linux / macOS

chmod +x gradlew
./gradlew assembleDebug

Windows

.\gradlew.bat assembleDebug

Build Release APK

./gradlew assembleRelease

For Windows:

.\gradlew.bat assembleRelease

Clean the project

./gradlew clean

Build outputs

Generated APK files are available in:

app/build/outputs/apk/


Permissions

Floaty uses Android permissions required by its application functionality.

Depending on the Android version, certain permissions may require runtime approval or special access through Android Settings.

Users should review permission requests before granting access.

Android Compatibility

Floaty targets the following Android versions:

Android Version| API
Android 5.0| 21
Android 5.1| 22
Android 6.0| 23
Android 7.0–7.1| 24–25
Android 8.0–8.1| 26–27
Android 9| 28
Android 10| 29
Android 11| 30
Android 12–12L| 31–32
Android 13| 33
Android 14| 34
Android 15| 35
Android 16| 36

Compatibility can vary depending on device manufacturers, Android restrictions, and permission availability.

Contributing

Contributions are welcome!

1. Fork this repository.
2. Create a new branch.
3. Make your changes.
4. Build and test your changes.
5. Submit a pull request.

Please preserve the original copyright and license notices when redistributing the project.

License

Floaty is licensed under the MIT License.

You are free to:

- Use the source code.
- Modify the source code.
- Distribute original or modified versions.
- Use the code in personal or commercial projects.

Attribution is required. Copies or substantial portions of the software must include the original copyright notice and MIT License text.

See the "LICENSE" (LICENSE) file for the complete license.

Credits

Developed and maintained by RootDeck.

- GitHub: https://github.com/Official-RootDeck
- Repository: https://github.com/Official-RootDeck/Floaty

If you use Floaty's source code, please preserve the original copyright and license notice.

---

<p align="center">
  <b>Floaty — Built by RootDeck.</b><br>
  Open source. Built for Android.
</p>
