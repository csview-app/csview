
APP = CSView.app
VERSION = 0.1.0
JDK = jdk1.8.0_40.jdk
APP_JDK = $(APP)/Contents/Plugins/$(JDK)/Contents
APP_JRE = $(APP_JDK)/Home/jre

app:
	rm -rf $(APP)
	jar2app \
		-j -XstartOnFirstThread \
		-i MacOS/icon.icns \
		-r /Library/Java/JavaVirtualMachines/$(JDK) \
		csview.jar $(APP)
	sed -e "s/\$${VERSION}/$(VERSION)/" -e "s/\$${JVMRuntime}/$(JDK)/" MacOS/Info.plist > $(APP)/Contents/Info.plist
	rm -rf $(APP_JDK)/Home/{bin,db,include,javafx-src.zip,lib,man,src.zip,THIRDPARTYLICENSEREADME-JAVAFX.txt}
	rm -rf $(APP_JRE)/{bin,THIRDPARTYLICENSEREADME-JAVAFX.txt}
	rm -rf $(APP_JRE)/lib/{applet,ext,images,jce.jar,javafx.properties,libjfxwebkit.dylib,libgstreamer-lite.dylib}
	rm -rf $(APP_JRE)/lib/{libjavafx_font_t2k.dylib,jsse.jar,plugin.jar,libjfxmedia.dylib,libjfxmedia_avf.dylib}
	rm -rf $(APP_JRE)/lib/{libjavafx_iio.dylib,libfxplugins.dylib,libjfxmedia_qtkit.dylib,libjavafx_font.dylib,jfxswt.jar}

log: app
	tail -f /tmp/csview.log