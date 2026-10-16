@echo off
REM FakeGPS-next build script (Windows)
REM
REM The previous version hard-coded this machine's JDK / Android SDK / Gradle
REM absolute paths, so it failed on any other machine or JDK version. Now:
REM   1. Uses the repo's own gradlew (version pinned by gradle-wrapper.properties,
REM      no local Gradle install required)
REM   2. Only overrides JAVA_HOME / ANDROID_HOME when FAKEGPS_JAVA_HOME /
REM      FAKEGPS_ANDROID_HOME are explicitly set; otherwise the system's
REM      existing JAVA_HOME / ANDROID_HOME / PATH are used
REM
REM NOTE: messages below are ASCII on purpose. cmd.exe reads .bat files using
REM the OEM code page (GBK on zh-CN), so non-ASCII text here renders as mojibake.
REM
REM Usage:
REM   build.bat                 build Debug APK
REM   build.bat assembleRelease build Release APK
REM   build.bat clean           clean
setlocal

REM Only override when explicitly provided, so no local paths leak into the repo
if defined FAKEGPS_JAVA_HOME set "JAVA_HOME=%FAKEGPS_JAVA_HOME%"
if defined FAKEGPS_ANDROID_HOME (
    set "ANDROID_HOME=%FAKEGPS_ANDROID_HOME%"
    set "ANDROID_SDK_ROOT=%FAKEGPS_ANDROID_HOME%"
)

if not exist "%CD%\gradlew.bat" (
    echo [ERROR] gradlew.bat not found in the current directory.
    echo         Please "cd" to the repository root and run this script again.
    echo         Current directory: %CD%
    endlocal
    exit /b 1
)

if "%~1"=="" (
    call "%CD%\gradlew.bat" assembleDebug
) else (
    call "%CD%\gradlew.bat" %*
)

endlocal
exit /b %ERRORLEVEL%
