@echo Packaging

set APP_NAME=CSView
set APP_VERSION=1.2.0
set JAR_FILE=csview-%APP_VERSION%-jar-with-dependencies.jar

rmdir /S /Q target
mvn package
