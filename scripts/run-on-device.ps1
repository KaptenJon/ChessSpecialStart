[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Fail([string]$Message) {
    [Console]::Error.WriteLine("ERROR: $Message")
    exit 1
}

function Get-JavaMajorVersion([string]$JavaExecutable) {
    $versionOutput = & $JavaExecutable -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    $firstLine = ($versionOutput | Select-Object -First 1)
    if ($firstLine -match '"(?<version>\d+)(\.\d+)?') {
        return [int]$Matches["version"]
    }

    return $null
}

function Ensure-JavaHome {
    if ($env:JAVA_HOME) {
        $configuredJava = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $configuredJava) {
            $configuredMajor = Get-JavaMajorVersion -JavaExecutable $configuredJava
            if ($configuredMajor -ge 11) {
                Write-Step "Using JAVA_HOME at $env:JAVA_HOME"
                return
            }
        }
    }

    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot",
        "C:\Program Files\Eclipse Adoptium\jdk-17",
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-11"
    )

    foreach ($candidate in $candidates) {
        $javaExecutable = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path $javaExecutable)) {
            continue
        }

        $majorVersion = Get-JavaMajorVersion -JavaExecutable $javaExecutable
        if ($majorVersion -ge 11) {
            $env:JAVA_HOME = $candidate
            $env:Path = "$candidate\bin;$env:Path"
            Write-Step "Using detected JDK at $candidate"
            return
        }
    }

    Fail "JAVA_HOME is not set to a JDK 11+ installation and no fallback JDK was found. Install JDK 17, set JAVA_HOME, and retry."
}

function Get-LocalPropertiesValue([string]$PropertyName) {
    $localPropertiesPath = Join-Path $repoRoot "local.properties"
    if (-not (Test-Path $localPropertiesPath)) {
        return $null
    }

    $line = Get-Content $localPropertiesPath |
        Where-Object { $_ -match "^$([Regex]::Escape($PropertyName))=" } |
        Select-Object -First 1

    if (-not $line) {
        return $null
    }

    $value = $line.Substring($PropertyName.Length + 1).Trim()
    return $value.Replace('\:', ':').Replace('\\', '\')
}

function Get-AndroidSdkRoot {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Get-LocalPropertiesValue -PropertyName "sdk.dir")
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    Fail "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT, or add sdk.dir to local.properties. Then retry with USB debugging enabled on your device."
}

function Get-AdbPath {
    $sdkRoot = Get-AndroidSdkRoot
    $adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
    if (-not (Test-Path $adbPath)) {
        Fail "adb was not found at $adbPath. Install Android SDK platform-tools and retry."
    }

    return $adbPath
}

function Assert-ConnectedDevice([string]$AdbPath) {
    & $AdbPath start-server | Out-Null
    $deviceOutput = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) {
        Fail "adb devices failed. Confirm the Android SDK platform-tools installation is healthy and retry."
    }

    $unauthorized = $deviceOutput | Where-Object { $_ -match "\sunauthorized$" }
    if ($unauthorized) {
        Fail "A device is connected but unauthorized. Unlock it, accept the USB debugging prompt, and retry."
    }

    $devices = $deviceOutput | Where-Object {
        $_ -match "\sdevice$" -and $_ -notmatch "^List of devices attached"
    }

    if (-not $devices) {
        Fail "No connected Android device was found. Connect a device, enable Developer Options and USB debugging, then retry."
    }

    Write-Step "Detected device(s):"
    $devices | ForEach-Object { Write-Host "    $_" }
}

$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$debugApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$launchTarget = "com.chesspoints.app/.MainActivity"

if (-not (Test-Path $gradleWrapper)) {
    Fail "Gradle wrapper not found at $gradleWrapper."
}

Write-Step "Checking Java"
Ensure-JavaHome

Write-Step "Locating adb"
$adbPath = Get-AdbPath
Write-Step "Using adb at $adbPath"

Write-Step "Checking for a connected Android device"
Assert-ConnectedDevice -AdbPath $adbPath

Write-Step "Building the debug APK"
Push-Location $repoRoot
try {
    & $gradleWrapper ":app:assembleDebug" "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle build failed. See the output above for details."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path $debugApk)) {
    Fail "Debug APK not found at $debugApk after the build completed."
}

Write-Step "Installing the debug APK"
& $adbPath install -r $debugApk
if ($LASTEXITCODE -ne 0) {
    Fail "adb install failed. See the output above for details."
}

Write-Step "Launching ChessPoints"
& $adbPath shell am start -n $launchTarget
if ($LASTEXITCODE -ne 0) {
    Fail "adb shell am start failed. See the output above for details."
}

Write-Step "Done. ChessPoints should now be running on the connected device."
