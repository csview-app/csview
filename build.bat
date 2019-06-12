@echo off

echo Building
REM mvn clean package

echo Packaging

set APP_NAME=CSView
set APP_VERSION=1.3.3
set JAR_VERSION=1.3.3
set JAR_FILE=csview-%JAR_VERSION%-jar-with-dependencies.jar

"%JAVA_HOME%\bin\javapackager.exe" -deploy -native exe ^
		-srcdir target -srcfiles %JAR_FILE% ^
		-outdir package -outfile %APP_NAME% ^
		-name %APP_NAME% ^
		-appclass net.kothar.csview.SingleInstanceLoader ^
		-BmainJar=%JAR_FILE% ^
		-BappVersion=%APP_VERSION% ^
		-BsystemWide=true ^
		-Bvendor="Kothar Labs" -v