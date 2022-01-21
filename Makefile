APP_NAME = CSView
VERSION := $(shell mvn -q -Dexec.executable="echo" -Dexec.args='$${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)
USER_NAME = Michael Houston (D5HSL8R3CY)
APP_KEY = Developer ID Application: $(USER_NAME)
INSTALLER_KEY = Developer ID Installer: $(USER_NAME)
APPSTORE_INSTALLER_KEY = 3rd Party Mac Developer Installer: $(USER_NAME)
APPSTORE_APP_KEY = Apple Distribution: $(USER_NAME)
APPSTORE_CREDS = --username "michael@overuse.org" --password "@keychain:ac_notarize"

APP_BUNDLE = bundles/$(APP_NAME).app
JAR_FILE = csview-$(VERSION)-jar-with-dependencies.jar
APPSTORE_PKG = bundles/CSView-$(VERSION)-MacAppStore.pkg
APP_PKG = bundles/CSView-$(VERSION).pkg
APP_DMG = bundles/CSView-$(VERSION).dmg

# JAVA_HOME := $(shell /usr/libexec/java_home -v 11)
#JPACKAGER = $(HOME)/Downloads/jdk.packager-osx/jpackager
JPACKAGER = $(shell jenv javahome)/bin/jpackage
SOURCES := $(shell find src)
PKG_SOURCES := $(shell find package)

all: app

icons: package/macosx/CSView.icns package/windows/CSView.ico package/windows/CSView-setup-icon.bmp

src/main/resources/icon.png: icon.svg
	mkdir -p `dirname $@`
	rsvg-convert -h 256 icon.svg > $@

package/macosx/CSView.icns: icon.svg
	mkdir -p CSView.iconset
	rsvg-convert -h 1024 icon.svg > CSView.iconset/icon_512x512@2x.png
	rsvg-convert -h 512 icon.svg > CSView.iconset/icon_512x512.png
	rsvg-convert -h 512 icon.svg > CSView.iconset/icon_256x256@2x.png
	rsvg-convert -h 256 icon.svg > CSView.iconset/icon_256x256.png
	rsvg-convert -h 256 icon.svg > CSView.iconset/icon_128x128@2x.png
	rsvg-convert -h 128 icon.svg > CSView.iconset/icon_128x128.png
	rsvg-convert -h 128 icon.svg > CSView.iconset/icon_64x64@2x.png
	rsvg-convert -h 64 icon.svg > CSView.iconset/icon_64x64.png
	rsvg-convert -h 64 icon.svg > CSView.iconset/icon_32x32@2x.png
	rsvg-convert -h 32 icon.svg > CSView.iconset/icon_32x32.png
	rsvg-convert -h 32 icon.svg > CSView.iconset/icon_16x16@2x.png
	rsvg-convert -h 16 icon.svg > CSView.iconset/icon_16x16.png
	iconutil -c icns CSView.iconset
	cp CSView.icns package/macosx
	cp CSView.icns package/macosx/CSView-volume.icns
	rm -r CSView.icns CSView.iconset

package/windows/CSView.ico: icon.svg
	mkdir -p `dirname $@`
	rm -rf CSView.icoset
	mkdir -p CSView.icoset
	rsvg-convert -h 256 icon.svg > CSView.icoset/icon_2_256x256.png
	rsvg-convert -h 128 icon.svg > CSView.icoset/icon_3_128x128.png
	rsvg-convert -h 64 icon.svg > CSView.icoset/icon_4_64x64.png
	rsvg-convert -h 32 icon.svg > CSView.icoset/icon_5_32x32.png
	rsvg-convert -h 16 icon.svg > CSView.icoset/icon_6_16x16.png
	convert CSView.icoset/* -colors 256 $@
	rm -r CSView.icoset

package/windows/CSView-setup-icon.bmp: icon.svg
	mkdir -p `dirname $@`
	rsvg-convert -h 58 -b white icon.svg > setup-icon.png
	convert setup-icon.png $@
	rm setup-icon.png

jar: target/$(JAR_FILE)

target/$(JAR_FILE): $(SOURCES)
	mvn package

sandbox: $(APP_BUNDLE)
	codesign --entitlements package/macosx/CSView.entitlements  -f -s "$(APP_KEY)" $(APP_BUNDLE)

verify: $(APP_BUNDLE)
	codesign --verify --verbose --all-architectures $(APP_BUNDLE)
	codesign -vv --deep-verify $(APP_BUNDLE)
	codesign -dvv $(APP_BUNDLE)
	spctl --assess -v --type execute $(APP_BUNDLE)

sign $(APP_BUNDLE).signed: $(APP_BUNDLE)
	for item in `find "$<" -depth -type d -name "*.framework" -or -name "*.dylib" -or -name "*.bundle" -or -name CSView -or -name jspawnhelper | sed -e "s/\(.*framework\)/\1\/Versions\/A\//"`;\
	 	do codesign -vvvv --force --deep --options runtime --entitlements package/macosx/CSView.entitlements \
			--sign "$(APP_KEY)" --timestamp "$$item" --prefix net.kothar.csview. ;\
		done
	touch $(APP_BUNDLE).signed

deps: target/$(JAR_FILE)
	jdeps --list-deps target/$(JAR_FILE)

app: $(APP_BUNDLE)

$(APP_BUNDLE): package/macosx/CSView.icns target/$(JAR_FILE)
	rm -rf package/app
	mkdir -p package/app
	cp target/$(JAR_FILE) package/app
	$(JPACKAGER) -n CSView \
		--type app-image \
		--input package/app \
		--main-class net.kothar.csview.cocoa.MacLoader \
		--main-jar $(JAR_FILE) \
		--java-options "-XstartOnFirstThread" \
		--app-version $(VERSION) \
		--description "Fast viewer for CSV files" \
		--vendor "Kothar Labs" \
		--dest bundles \
		--icon package/macosx/CSView.icns \
		--mac-package-name CSView \
		--mac-package-identifier net.kothar.csview \
		--mac-app-category productivity \
		--mac-entitlements package/macosx/CSView.entitlements \
		--file-associations package/macosx/csv-files.properties \
		--file-associations package/macosx/tsv-files.properties \
		--add-modules java.base,java.compiler,java.desktop,java.logging,java.sql,java.xml,jdk.unsupported

appstore: $(APPSTORE_PKG)

$(APP_PKG): $(APP_BUNDLE)
	for item in `find "$<" -depth -type d -name "*.framework" -or -name "*.dylib" -or -name "*.bundle" -or -name CSView -or -name jspawnhelper | sed -e "s/\(.*framework\)/\1\/Versions\/A\//"`;\
		do codesign -vvvv --force --deep --options runtime --entitlements package/macosx/CSView.entitlements \
			--sign "$(APP_KEY)" --timestamp "$$item" --prefix net.kothar.csview. ;\
		done
	productbuild --component $(APP_BUNDLE) /Applications \
		--sign "$(INSTALLER_KEY)" \
		--product $(APP_BUNDLE)/Contents/Info.plist \
		$(APP_PKG)

$(APPSTORE_PKG): $(APP_BUNDLE)
	rm -rf package/app
	mkdir -p package/app
	cp target/$(JAR_FILE) package/app
	$(JPACKAGER) -n CSView \
		--verbose \
		--type pkg \
		--input package/app \
		--main-class net.kothar.csview.cocoa.MacLoader \
		--main-jar $(JAR_FILE) \
		--java-options "-XstartOnFirstThread" \
		--app-version $(VERSION) \
		--description "Fast viewer for CSV files" \
		--vendor "Kothar Labs" \
		--dest bundles \
		--icon package/macosx/CSView.icns \
		--mac-app-store \
		--mac-sign \
		--mac-signing-key-user-name "$(USER_NAME)" \
		--mac-package-name CSView \
		--mac-package-identifier net.kothar.csview \
		--mac-app-category productivity \
		--mac-entitlements package/macosx/CSView.entitlements \
		--file-associations package/macosx/csv-files.properties \
		--file-associations package/macosx/tsv-files.properties \
		--add-modules java.base,java.compiler,java.desktop,java.logging,java.sql,java.xml,jdk.unsupported

dmg: $(APP_DMG)

$(APP_DMG): $(APP_BUNDLE)
	rm -rf package/app
	mkdir -p package/app
	cp target/$(JAR_FILE) package/app
	$(JPACKAGER) -n CSView \
		--verbose \
		--type dmg \
		--input package/app \
		--main-class net.kothar.csview.cocoa.MacLoader \
		--main-jar $(JAR_FILE) \
		--java-options "-XstartOnFirstThread" \
		--app-version $(VERSION) \
		--description "Fast viewer for CSV files" \
		--vendor "Kothar Labs" \
		--dest bundles \
		--icon package/macosx/CSView.icns \
		--mac-sign \
		--mac-signing-key-user-name "$(USER_NAME)" \
		--mac-package-name CSView \
		--mac-package-identifier net.kothar.csview \
		--mac-app-category productivity \
		--mac-entitlements package/macosx/CSView.entitlements \
		--file-associations package/macosx/csv-files.properties \
		--file-associations package/macosx/tsv-files.properties \
		--add-modules java.base,java.compiler,java.desktop,java.logging,java.sql,java.xml,jdk.unsupported

zip: $(APP_BUNDLE).zip

$(APP_BUNDLE).zip: $(APP_BUNDLE).signed
	rm -f $@
	cd `dirname $(APP_BUNDLE)` && zip -r -D `basename $@` `basename $(APP_BUNDLE)`

notarize-dmg $(APP_DMG).notarized: $(APP_DMG)
	 xcrun altool --notarize-app --primary-bundle-id net.kothar.csview \
	 --file $< $(APPSTORE_CREDS) | grep RequestUUID | sed -e "s/.*= //" > $(APP_DMG).notarized

staple-dmg $(APP_DMG).stapled: $(APP_DMG).notarized
	xcrun stapler staple $(APP_DMG)
	touch $(APP_DMG).stapled

validate-appstore: $(APPSTORE_PKG)
	xcrun altool --validate-app --file $(APPSTORE_PKG) \
	$(APPSTORE_CREDS)

notarization-status:
	xcrun altool --notarization-history 0 $(APPSTORE_CREDS)

status-dmg:
	xcrun altool --notarization-info `cat $(APP_DMG).notarized` $(APPSTORE_CREDS)

upload-appstore: $(APPSTORE_PKG)
	xcrun altool --upload-app -f $(APPSTORE_PKG) $(APPSTORE_CREDS)

clean:
	# sqlite3 "~/Library/Application Support/com.apple.TCC/Tcc.db" 'delete from access where client like "%CSView%"'
	mvn clean
	rm -f *.log
	rm -rf $(APP_BUNDLE) $(APP_BUNDLE).zip package/app

.PHONY: package icons appstore dmg pkg zip resign verify sandbox app jar clean
