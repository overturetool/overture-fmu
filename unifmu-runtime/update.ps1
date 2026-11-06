param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot "target/generated-fmu"
}
$Version = $Version -replace '^v', ''
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Expected a UniFMU version such as 1.1.0."
}

function Test-GeneratedFmu([string]$Directory) {
    $files = @(
        "resources/libs/unifmu-java-runtime-$Version.jar",
        "binaries/linux64/unifmu.so",
        "binaries/aarch64-linux/unifmu.so",
        "binaries/darwin64/unifmu.dylib",
        "binaries/win64/unifmu.dll"
    )
    foreach ($file in $files) {
        $path = Join-Path $Directory $file
        if (!(Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path).Length -eq 0) {
            return $false
        }
    }
    return $true
}

$versionFile = Join-Path $OutputDirectory ".unifmu-version"
if ((Test-Path -LiteralPath $versionFile) -and
    (Get-Content -LiteralPath $versionFile -Raw).Trim() -eq $Version -and
    (Test-GeneratedFmu $OutputDirectory)) {
    Write-Host "UniFMU $Version binaries and Java runtime are present; skipping generation."
    exit 0
}

$targetDirectory = Join-Path $PSScriptRoot "target/unifmu-bootstrap/$Version/x86_64-pc-windows-gnu"
$archiveName = "unifmu-x86_64-pc-windows-gnu-$Version.zip"
$archive = Join-Path $targetDirectory $archiveName
$extractDirectory = Join-Path $targetDirectory "extracted"
New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

if (!(Test-Path -LiteralPath $archive) -or (Get-Item -LiteralPath $archive).Length -eq 0) {
    $url = "https://github.com/INTO-CPS-Association/unifmu/releases/download/v$Version/$archiveName"
    Write-Host "Downloading UniFMU $Version for Windows x86_64 to $archive"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$archive.tmp"
    Move-Item -LiteralPath "$archive.tmp" -Destination $archive -Force
}

Expand-Archive -LiteralPath $archive -DestinationPath $extractDirectory -Force
$tool = Join-Path $extractDirectory "unifmu.exe"
$generatedDirectory = Join-Path $targetDirectory "generated"
& $tool generate java $generatedDirectory fmi2
if ($LASTEXITCODE -ne 0) {
    throw "UniFMU generation failed with exit code $LASTEXITCODE."
}
if (!(Test-GeneratedFmu $generatedDirectory)) {
    throw "UniFMU generation did not produce all expected binaries and the Java runtime JAR."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
Get-ChildItem -LiteralPath $generatedDirectory -Force | Copy-Item -Destination $OutputDirectory -Recurse -Force
Set-Content -LiteralPath $versionFile -Value $Version -Encoding ASCII
