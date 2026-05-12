$ErrorActionPreference = "Stop"

Write-Host "Downloading JNA..."
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar" -OutFile "jna.jar"
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/5.13.0/jna-platform-5.13.0.jar" -OutFile "jna-platform.jar"

Write-Host "Compiling Injector.java..."
javac -cp "jna.jar;jna-platform.jar" Injector.java

Write-Host "Extracting JNA classes..."
mkdir build_temp -Force | Out-Null
Set-Location build_temp
jar xf ..\jna.jar
jar xf ..\jna-platform.jar
Copy-Item ..\Injector*.class .

Write-Host "Creating Manifest..."
$manifest = "Main-Class: Injector`n"
Set-Content -Path "manifest.txt" -Value $manifest -Encoding Ascii

Write-Host "Building Injector.jar..."
jar cvfm ..\Injector.jar manifest.txt .

Set-Location ..
Remove-Item -Recurse -Force build_temp
Remove-Item jna.jar
Remove-Item jna-platform.jar
Remove-Item Injector*.class

Write-Host "Done! Injector.jar created successfully."
