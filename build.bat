@echo Packaging

set APP_NAME=CSView
set APP_VERSION=1.1.0

javapackager -deploy -native installer ^
		-srcdir build -srcfiles csview.jar ^
		-outdir package -outfile %APP_NAME% ^
		-name %APP_NAME% ^
		-appclass net.kothar.csview.CSView ^
		-BmainJar=csview.jar ^
		-BappVersion=%APP_VERSION%