
VERSION = 0.0.1

app:
	jar2app \
		-j -XstartOnFirstThread \
		-i icon.icns \
		-b net.kothar.csview \
		-v $(VERSION) \
		-s $(VERSION) \
		-c "(c) 2016 Kothar Labs" \
		csview-$(VERSION).jar CSView.app
	cp Info.plist CSView.app/Contents

log: app
	tail -f /tmp/csview.log