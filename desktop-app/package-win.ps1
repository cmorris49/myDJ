$ErrorActionPreference = "Stop"

if (-not $env:JAVA_FX_JMODS) {
  Write-Error "Set JAVA_FX_JMODS to your JavaFX jmods folder (e.g., C:\javafx-jmods-21)."
}

# 1) Build jar and copy runtime deps
mvn -q -DskipTests package
mvn -q dependency:copy-dependencies -DoutputDirectory=target\deps

# 2) Locate the built jar (ignore any "original-*.jar")
$jar = (Get-ChildItem target -Filter "*.jar" | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1).Name
if (-not $jar) { Write-Error "Jar not found in target/"; }

# 3) Fresh dist folder
$dist = "dist"
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist | Out-Null

# 4) jpackage portable app-image (note: using --classpath)
& jpackage `
  --type app-image `
  --name myDJ `
  --app-version 1.0.0 `
  --input target `
  --main-jar $jar `
  --main-class com.mydj.desktop.App `
  --dest $dist `
  --classpath "deps\*" `
  --add-modules "java.base,java.logging,java.xml,java.desktop,jdk.crypto.ec,javafx.controls,javafx.fxml" `
  --module-path "$env:JAVA_FX_JMODS;$env:JAVA_HOME\jmods" `
  --win-console

# 5) Zip for distribution
$zipPath = Join-Path $dist "myDJ-win.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path "$dist\myDJ\*" -DestinationPath $zipPath

Write-Host "Built: $zipPath"
