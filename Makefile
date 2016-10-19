
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
		-Bmac.category=public.app-category.productivity \
		-Bmac.CFBundleIdentifier=net.kothar.csview \
		-BjvmOptions=-XstartOnFirstThread \
		-Bmac.signing-key-developer-id-app="Mac Developer: Michael Houston (4JB33XB5VR)" \
		-Bmac.signing-key-developer-id-installer="Mac Developer: Michael Houston (4JB33XB5VR)" 

log: app
	tail -f /tmp/csview.log

.PHONY: package
