
APP_NAME = CSView
VERSION = 1.0.0

all: app

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

app:
	BUNDLES=image make package

sandbox:
	codesign --entitlements package/macosx/CSView.entitlements  -f -s "Mac Developer: Michael Houston (4JB33XB5VR)" package/bundles/CSView.app/

dmg:
	BUNDLES=dmg make package

appstore:
	BUNDLES=mac.appStore make package

package: package/macosx/CSView.icns
	javapackager -deploy -native $(BUNDLES) \
		-srcdir build/macos \
		-outdir package -outfile $(APP_NAME) \
		-name $(APP_NAME) \
		-appclass net.kothar.csview.cocoa.MacLoader \
		-BmainJar=csview.jar \
		-BappVersion=$(VERSION) \
		-Bmac.category=public.app-category.productivity \
		-Bmac.CFBundleIdentifier=net.kothar.csview \
		-BjvmOptions=-XstartOnFirstThread \
		-Bmac.signing-key-developer-id-app="Mac Developer: Michael Houston (4JB33XB5VR)" \
		-Bmac.signing-key-developer-id-installer="Developer ID Installer: Michael Houston (D5HSL8R3CY)" 

log: app
	tail -f /tmp/csview.log

.PHONY: package
