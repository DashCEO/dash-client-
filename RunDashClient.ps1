$ErrorActionPreference = 'Stop'

function Test-SharingViolation($errorRecord) {
    $exception = $errorRecord.Exception.GetBaseException()
    return $exception -is [IO.IOException] -and ($exception.HResult -band 0xFFFF) -in 32, 33
}

function Get-DashJavaRendererFailure([string]$report) {
    if ($report -notmatch 'net\.vulkanmod\.') { return }
    if ($report -match 'VK_ERROR_(?:DEVICE_LOST|SURFACE_LOST_KHR|OUT_OF_DATE_KHR)') {
        return 'Vulkan lost the graphics device or surface'
    }
    if ($report -match 'NullPointerException[^\r\n]*getDepthAttachment\(\)' -and
            $report -match 'net\.vulkanmod\.vulkan\.framebuffer\.RenderPass\.beginDynamicRendering') {
        return 'Vulkan framebuffer lost its depth attachment'
    }
    if ($report -match 'IndexOutOfBoundsException' -and
            $report -match 'net\.vulkanmod\.vulkan\.(?:pass\.DefaultMainPass|framebuffer\.SwapChain)\.getColorAttachment') {
        return 'Vulkan swapchain returned an invalid image index'
    }
}

function Get-DashRendererFailure([string]$gameDir, [datetime]$sinceUtc) {
    $reports = Join-Path $gameDir 'crash-reports'
    foreach ($file in Get-ChildItem -LiteralPath $reports -Filter 'crash-*-client.txt' -File -ErrorAction SilentlyContinue |
            Where-Object LastWriteTimeUtc -GE $sinceUtc | Sort-Object LastWriteTimeUtc -Descending) {
        $report = (Get-Content -LiteralPath $file.FullName -TotalCount 80 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
        $failure = Get-DashJavaRendererFailure $report
        if ($failure) { return $failure }
    }

    $log = Get-Item -LiteralPath (Join-Path $gameDir 'logs/latest.log') -ErrorAction SilentlyContinue
    if ($log -and $log.LastWriteTimeUtc -ge $sinceUtc) {
        $tail = (Get-Content -LiteralPath $log.FullName -Tail 250 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
        $failure = Get-DashJavaRendererFailure $tail
        if ($failure) { return $failure }
    }

    foreach ($file in Get-ChildItem -LiteralPath $gameDir -Filter 'hs_err_pid*.log' -File -ErrorAction SilentlyContinue |
            Where-Object LastWriteTimeUtc -GE $sinceUtc | Sort-Object LastWriteTimeUtc -Descending) {
        $report = (Get-Content -LiteralPath $file.FullName -TotalCount 130 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
        if ($report -notmatch '(?m)^#\s+EXCEPTION_ACCESS_VIOLATION') { continue }
        # Do not reopen Minecraft after a deliberate Quit followed by a cleanup crash.
        if ($report -match 'net\.vulkanmod\.vulkan\.(?:Renderer\.cleanUpResources|Vulkan\.cleanUp)') {
            return 'Vulkan renderer shutdown'
        }
        if ($report -match '(?m)^# C\s+\[(?:nvoglv64|vulkan-1)\.dll\+' -or
                ($report -match 'org\.lwjgl\.vulkan\.' -and $report -match 'net\.vulkanmod\.')) {
            return 'Vulkan/NVIDIA native graphics crash'
        }
    }
}

function Clear-DashVulkanSelection {
    Remove-Item Env:VK_LOADER_DRIVERS_SELECT,Env:VK_LOADER_VENDOR_ID_FILTER,Env:VK_LOADER_DEVICE_ID_FILTER -ErrorAction SilentlyContinue
}

function Set-DashOpenGlWindowed([string]$gameDir) {
    $optionsPath = Join-Path $gameDir 'options.txt'
    if (!(Test-Path -LiteralPath $optionsPath -PathType Leaf)) { return }

    $current = [IO.File]::ReadAllText($optionsPath)
    $updated = [regex]::Replace($current, '(?m)^fullscreen:true(?=\r?$)', 'fullscreen:false')
    if ($updated -ceq $current) { return }

    # Change the video mode only after the launch lock has been acquired.
    $tempPath = "$optionsPath.dash-$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        [IO.File]::WriteAllText($tempPath, $updated, [Text.UTF8Encoding]::new($false))
        [IO.File]::Replace($tempPath, $optionsPath, [NullString]::Value)
    } finally {
        if (Test-Path -LiteralPath $tempPath -PathType Leaf) {
            Remove-Item -LiteralPath $tempPath
        }
    }
}

function Test-DashNvidiaOpenGlFailure([string]$gameDir, [datetime]$sinceUtc) {
    foreach ($file in Get-ChildItem -LiteralPath $gameDir -Filter 'hs_err_pid*.log' -File -ErrorAction SilentlyContinue |
            Where-Object LastWriteTimeUtc -GE $sinceUtc) {
        $report = (Get-Content -LiteralPath $file.FullName -TotalCount 130 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
        if ($report -match '(?m)^#\s+EXCEPTION_ACCESS_VIOLATION' -and
                $report -match '(?m)^# C\s+\[nvapi64_impl\.dll\+' -and
                $report -match 'org\.lwjgl\.glfw\.GLFW\.glfwSwapBuffers|com\.mojang\.blaze3d\.systems\.RenderSystem\.flipFrame|com\.mojang\.blaze3d\.platform\.Window\.updateDisplay') {
            return $true
        }
    }
    return $false
}

function Read-DashNickname([string]$path) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { return }
    $nickname = [IO.File]::ReadAllText($path).Trim()
    if ($nickname -cmatch '^[A-Za-z0-9_]{3,16}$') { return $nickname }
}

$gradleDir = Join-Path $PSScriptRoot '.gradle'
[void][IO.Directory]::CreateDirectory($gradleDir)
$lockPath = Join-Path $gradleDir 'dash-client-launch.lock'
try {
    $lock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
} catch {
    if (!(Test-SharingViolation $_)) { throw }
    Write-Host 'Dash Client is already running or starting. Use the open Minecraft window.'
    exit 0
}

try {
    $cachePaths = @('run\.fabric\processedMods', 'run\rtx-profile\.fabric\processedMods')
    foreach ($relativeCache in $cachePaths) {
        $cache = Join-Path $PSScriptRoot $relativeCache
        if (!(Test-Path -LiteralPath $cache -PathType Container)) { continue }
        foreach ($jar in Get-ChildItem -LiteralPath $cache -Filter '*.jar' -File) {
            try {
                $probe = [IO.File]::Open($jar.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
                $probe.Dispose()
            } catch {
                $exception = $_.Exception.GetBaseException()
                if ($exception -is [IO.FileNotFoundException]) { continue }
                if (!(Test-SharingViolation $_)) { throw }
                Write-Host 'The Fabric mod cache is in use. Close the other Minecraft window before restarting Dash Client.'
                exit 0
            }
        }
    }

    $nicknamePath = Join-Path $PSScriptRoot 'run/dash-nickname.txt'
    $nickname = Read-DashNickname $nicknamePath
    $env:DASH_NICKNAME_FILE = $nicknamePath
    $env:DASH_SKIP_STARTUP = $null
    $runtimeArgs = @()
    $jdkRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
    $jdk21 = Get-ChildItem -LiteralPath $jdkRoot -Filter 'jdk-21.*' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') -PathType Leaf } |
        Select-Object -First 1
    if ($jdk21) {
        $runtimeArgs += '-PdashGameJava21'
    }
    $gradleArgs = @('runClient')
    $rtxDir = Join-Path $PSScriptRoot 'run/rtx-profile'
    $launchVulkan = $false
    $launchOpenGl = $false
    if ($env:DASH_RTX_PROFILE -eq '1' -and $env:DASH_STABLE_PROFILE -ne '1') {
        $vulkanMod = Join-Path $rtxDir 'mods/VulkanMod-0.6.8+1.21.11.jar'
        $launchVulkan = Test-Path -LiteralPath $vulkanMod -PathType Leaf
        if (!$launchVulkan) {
            Write-Host 'VulkanMod is missing. Starting the RTX profile with OpenGL.'
        }
    }
    if ($launchVulkan) {
        $env:VK_LOADER_DRIVERS_SELECT = 'nv-vk64.json'
        $env:VK_LOADER_VENDOR_ID_FILTER = '0x10de'
        $env:VK_LOADER_DEVICE_ID_FILTER = '0x2f04'
        $gradleArgs = @('--no-daemon', 'runRtxClient')
        Write-Host 'Starting Dash Client RTX (Vulkan)...'
    } elseif ($env:DASH_RTX_PROFILE -eq '1') {
        $launchOpenGl = $true
        Clear-DashVulkanSelection
        Set-DashOpenGlWindowed $rtxDir
        $gradleArgs = @('--no-daemon', 'runRtxSafeClient')
        Write-Host 'Starting the Dash RTX profile with borderless OpenGL (840 FPS cap)...'
    } else {
        Write-Host 'Starting Dash Client...'
    }
    Push-Location -LiteralPath $PSScriptRoot
    try {
        $gradleWrapper = Join-Path $PSScriptRoot 'gradlew.bat'
        do {
            $needsNickname = [string]::IsNullOrEmpty($nickname)
            $env:DASH_NICKNAME_SETUP = if ($needsNickname) { '1' } else { $null }
            $userArgument = if ($needsNickname) { '--args=--disableMultiplayer' } else { "--args=--username $nickname" }
            $launchTimeUtc = [datetime]::UtcNow
            & $gradleWrapper @runtimeArgs @gradleArgs $userArgument
            $exitCode = $LASTEXITCODE
            if ($launchVulkan -and $exitCode -ne 0) {
                $rendererFailure = Get-DashRendererFailure $rtxDir $launchTimeUtc
                if ($rendererFailure -eq 'Vulkan renderer shutdown') {
                    Write-Host 'Vulkan failed during shutdown. DashClient-Stable.bat starts the same profile with OpenGL.'
                } elseif ($rendererFailure) {
                    Write-Host "$rendererFailure. Restarting once with OpenGL in the same game profile."
                    Clear-DashVulkanSelection
                    Set-DashOpenGlWindowed $rtxDir
                    $launchVulkan = $false
                    $launchOpenGl = $true
                    $gradleArgs = @('--no-daemon', 'runRtxSafeClient')
                    & $gradleWrapper @runtimeArgs @gradleArgs $userArgument
                    $exitCode = $LASTEXITCODE
                }
            } elseif ($launchOpenGl -and $exitCode -ne 0 -and
                    (Test-DashNvidiaOpenGlFailure $rtxDir $launchTimeUtc)) {
                Write-Host 'The NVIDIA OpenGL driver failed while presenting a frame. See the latest hs_err_pid log in run/rtx-profile.'
            }

            if ($exitCode -ne 0) { break }
            $savedNickname = Read-DashNickname $nicknamePath
            if ([string]::IsNullOrEmpty($savedNickname) -or $savedNickname -ceq $nickname) { break }
            Write-Host 'Nickname saved. Restarting Dash Client with the selected profile.'
            $nickname = $savedNickname
            $env:DASH_SKIP_STARTUP = '1'
        } while ($true)
    } finally {
        Pop-Location
    }
    exit $exitCode
} finally {
    $lock.Dispose()
}
