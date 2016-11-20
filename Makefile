
APP_NAME = CSView
VERSION = 1.1.0
DEVELOPER_KEY = Developer ID Application: Michael Houston (D5HSL8R3CY)
INSTALLER_KEY = Developer ID Installer: Michael Houston (D5HSL8R3CY)
APP_BUNDLE = package/bundles/$(APP_NAME).app

all: app

icons: package/macosx/CSView.icns package/windows/CSView.ico package/windows/CSView-setup-icon.bmp

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
	rsvg-convert -h 58 icon.svg > setup-icon.png
	convert setup-icon.png $@
	rm setup-icon.png

app:
	BUNDLES=image make package

$(APP_BUNDLE): build/csview.jar
	make app

sandbox: $(APP_BUNDLE)
	codesign --entitlements package/macosx/CSView.entitlements  -f -s "$(DEVELOPER_KEY)" $(APP_BUNDLE)

verify: $(APP_BUNDLE)
	codesign --verify --verbose --all-architectures $(APP_BUNDLE)
	codesign -vv --deep-verify $(APP_BUNDLE)
	codesign -dvv $(APP_BUNDLE)
	spctl --assess -v --type execute $(APP_BUNDLE)

resign: $(APP_BUNDLE)
	codesign --verbose --force --verify --deep --sign "$(DEVELOPER_KEY)" $(APP_BUNDLE)

dmg:
	BUNDLES=dmg make package

appstore:
	BUNDLES=mac.appStore make package

package: package/macosx/CSView.icns
	javapackager -deploy -native $(BUNDLES) \
		-srcdir build -srcfiles csview.jar \
		-outdir package -outfile $(APP_NAME) \
		-name $(APP_NAME) \
		-appclass net.kothar.csview.cocoa.MacLoader \
		-BmainJar=csview.jar \
		-BappVersion=$(VERSION) \
		-Bmac.category=public.app-category.productivity \
		-Bmac.CFBundleIdentifier=net.kothar.csview \
		-BjvmOptions=-XstartOnFirstThread \
		-Bmac.signing-key-developer-id-app="$(DEVELOPER_KEY)" \
		-Bmac.signing-key-developer-id-installer="$(INSTALLER_KEY)" 

.PHONY: package icons appstore dmg resign verify sandbox app
