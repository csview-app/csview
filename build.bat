@echo Packaging

set APP_NAME=CSView
set APP_VERSION=1.1.1

javapackager -deploy -native msi ^
		-srcdir build -srcfiles csview.jar ^
		-outdir package -outfile %APP_NAME% ^
		-name %APP_NAME% ^
		-appclass net.kothar.csview.SingleInstanceLoader ^
		-BmainJar=csview.jar ^
		-BappVersion=%APP_VERSION% ^
		-BsystemWide=true ^
		-Bvendor="Kothar Labs"