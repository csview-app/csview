
VERSION = 0.0.1

app:
	jar2app \
		-j -XstartOnFirstThread \
		-b net.kothar.csview \
		-v $(VERSION) \
		-s $(VERSION) \
		-c "(c) 2016 Kothar Labs" \
		csview-$(VERSION).jar CSView.app

log: app
	tail -f /tmp/csview.log