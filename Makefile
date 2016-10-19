
APP_NAME = CSView
VERSION = 1.0.0

package: package/macosx/Info.plist
	javapackager -deploy -native \
		-srcdir . -srcfiles csview.jar \
		-outdir package -outfile $(APP_NAME) \
		-name $(APP_NAME) \
		-appclass net.kothar.csview.cocoa.MacLoader \
		-BmainJar=csview.jar \
		-BappVersion=$(VERSION) \
		-Bmac.category=Productivity \
		-Bmac.CFBundleIdentifier=net.kothar.csview \
		-BjvmOptions=-XstartOnFirstThread \
		-Bmac.signing-key-developer-id-app="3rd Party Mac Developer Application: Michael Houston (D5HSL8R3CY)" \
		-Bmac.signing-key-developer-id-installer="3rd Party Mac Developer Installer: Michael Houston (D5HSL8R3CY)" 

log: app
	tail -f /tmp/csview.log

.PHONY: package
