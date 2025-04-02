# i3droid

**_TESTING/UNSTABLE_**
This application is an attempt to improve Android desktop by adding i3-like functionality to free form windows. This application should be considered unstable. This application assumes you are familiar with ADB and you have a rooted device. This application is considered in the research/testing/unstable phase.

## Description

A simple Android implementation of a tiling window manager inspired by i3 for Linux. It utilizes Android's freeform window mode to arrange applications in non-overlapping tiles, maximizing screen usage and improving productivity. Requires root or ADB to enable freeform mode on most devices.

## Building on Termux

To compile this app directly on your Android device using Termux:

1. Install Termux from F-Droid (not Play Store version as it's outdated)

2. Update packages:

```
pkg update && pkg upgrade
```

3. Install required packages:

```
pkg install git wget aapt dx ecj
```

4. Clone the repository:

```
git clone https://github.com/trojas-gnister/i3droid
cd i3droid
```

5. Build the APK:

```
chmod +x gradlew
./gradlew assembleDebug
```

If the built-in Gradle build fails, you can use the alternative method:

```
aapt package -f -m -J src/main/java -M AndroidManifest.xml -S res/ -I /path/to/android.jar
ecj -d obj src/main/java
dx --dex --output=classes.dex obj/
aapt package -f -m -F i3droid.apk -M AndroidManifest.xml -S res/ -I /path/to/android.jar
```

The APK will be available at `app/build/outputs/apk/debug/app-debug.apk` or as `i3droid.apk` in the current directory depending on which method you used.
