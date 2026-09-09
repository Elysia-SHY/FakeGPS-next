@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "ANDROID_HOME=C:\Android\sdk"
set "ANDROID_SDK_ROOT=C:\Android\sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;C:\Gradle\gradle-8.4\bin;%PATH%"
call "C:\Gradle\gradle-8.4\bin\gradle.bat" assembleDebug
