@echo Packaging

set APP_NAME=CSView
set APP_VERSION=1.2.1
set JAR_FILE=csview-%APP_VERSION%-jar-with-dependencies.jar

javapackager -deploy -native msi ^
		-srcdir target -srcfiles %JAR_FILE% ^
		-outdir package -outfile %APP_NAME% ^
		-name %APP_NAME% ^
		-appclass net.kothar.csview.SingleInstanceLoader ^
		-BmainJar=%JAR_FILE% ^
		-BappVersion=%APP_VERSION% ^
		-BsystemWide=true ^
		-Bvendor="Kothar Labs"