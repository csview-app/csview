APP_NAME = CSView
# Read from the pom in one Maven run, since each costs a couple of seconds: the version, and the
# modules for the bundled runtime, which the Windows build also uses.
POM_VALUES := $(shell mvn -q -Dexec.executable="echo" -Dexec.args='$${project.version} $${runtime.modules}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)
VERSION := $(word 1,$(POM_VALUES))
RUNTIME_MODULES := $(word 2,$(POM_VALUES))
USER_NAME = Michael Houston (D5HSL8R3CY)
APP_KEY = Developer ID Application: $(USER_NAME)
INSTALLER_KEY = Developer ID Installer: $(USER_NAME)
APPSTORE_INSTALLER_KEY = 3rd Party Mac Developer Installer: $(USER_NAME)
APPSTORE_APP_KEY = Apple Distribution: $(USER_NAME)
APPSTORE_CREDS = --username "michael@overuse.org" --password "@keychain:ac_notarize_pw"

# jpackage picks the app icon, entitlements, DMG volume icon and Info.plist out of this directory.
# Info.plist is held here as a template because the two distribution channels declare different
# minimum macOS versions; stage_mac_resources below copies this directory and substitutes the
# version, and jpackage is pointed at the staged copy.
MAC_RESOURCES = package/macosx

# The App Store only accepts a single-architecture macOS build when it is declared for macOS 13 or
# later, and CSView 1.x is built one architecture at a time.
APPSTORE_MIN_OS = 13.0

# The floor for direct distribution (DMG, zip, Developer ID pkg): the lowest macOS the shipped
# binaries can actually run on. Every Mach-O in the bundle - the SWT cocoa natives for both
# architectures, the bundled Temurin runtime and the jpackage launcher - carries an LC_BUILD_VERSION
# minos of 11.0, so Big Sur is as far back as this build can reach. (CSView once declared 10.7.4;
# that was several SWT and JDK generations ago.) Verify after a JDK or SWT bump with:
#   vtool -show-build bundles/<arch>/CSView.app/Contents/MacOS/CSView
DIRECT_MIN_OS = 11.0

# Stage the macOS resource directory for one minimum-OS target.
#   $(1) destination directory   $(2) LSMinimumSystemVersion to declare
define stage_mac_resources
	rm -rf $(1)
	mkdir -p $(1)
	cp $(MAC_RESOURCES)/* $(1)/
	rm -f $(1)/Info.plist.template
	sed -e 's/@MIN_SYSTEM_VERSION@/$(2)/' $(MAC_RESOURCES)/Info.plist.template > $(1)/Info.plist
endef

JAR_FILE = csview-$(VERSION)-jar-with-dependencies.jar
ARCH = $(shell . "$(JAVA_HOME)/release" 2>/dev/null && echo $$OS_ARCH)

# Each architecture builds into its own directories, so an arm64 and an Intel build can sit side
# by side rather than the second replacing the first. A bundle has to be called CSView.app, so the
# architecture goes in its directory; the single-file artifacts below carry it in their names.
#   target/$(ARCH)/input      the jar jpackage packages, with that architecture's SWT natives
#   target/$(ARCH)/resources  the staged macOS resources, one directory per distribution channel
#   target/$(ARCH)/jpackage   where jpackage writes a pkg or DMG before it is renamed into bundles/
#   bundles/$(ARCH)/          the app bundle
BUILD_DIR = target/$(ARCH)
APP_INPUT = $(BUILD_DIR)/input
ARCH_JAR = $(APP_INPUT)/$(JAR_FILE)
STAGED_RESOURCES = $(BUILD_DIR)/resources
JPACKAGE_OUT = $(BUILD_DIR)/jpackage
APP_BUNDLE = bundles/$(ARCH)/$(APP_NAME).app
APP_ZIP = bundles/CSView-$(VERSION)-$(ARCH).zip

# The two pkg artifacts are different things and must not share a filename: one is the App Store
# submission, the other the Developer ID installer for direct download. They were both
# bundles/CSView-$(VERSION).pkg, which made them a single make target with two recipes - make kept
# the App Store one and silently discarded the other, so the Developer ID pkg could never be built.
# jpackage names its own output, so the App Store pkg is renamed after the fact, the way the DMG is.
APPSTORE_PKG = bundles/CSView-$(VERSION)-$(ARCH)-appstore.pkg
APP_PKG = bundles/CSView-$(VERSION)-$(ARCH).pkg
APP_DMG = bundles/CSView-$(VERSION)-$(ARCH).dmg

# What jpackage writes before it is renamed to the arch- or channel-qualified name above.
JPACKAGE_PKG = $(JPACKAGE_OUT)/CSView-$(VERSION).pkg
JPACKAGE_DMG = $(JPACKAGE_OUT)/CSView-$(VERSION).dmg

# jpackage ships inside the JDK, so locate a JDK rather than trusting whatever
# `jpackage` happens to be on the PATH (version managers install shims that fail
# unless a version is selected for this directory). An explicit JAVA_HOME wins;
# otherwise ask macOS for a JDK matching the release the pom compiles against.
#
# java_home picks the newest matching JDK whatever the architecture, even under Rosetta, so it is
# asked for one matching the architecture make is running as. `arch -x86_64 make app` therefore
# builds an Intel bundle on Apple silicon, given an x86_64 JDK installed alongside the arm64 one.
JAVA_VERSION = 25
HOST_ARCH := $(shell uname -m)
JAVA_HOME ?= $(shell /usr/libexec/java_home -a $(HOST_ARCH) -v $(JAVA_VERSION) 2>/dev/null)
JPACKAGER = $(JAVA_HOME)/bin/jpackage

# Maven must run on the same JDK: the pom picks the SWT natives (and the SWT-Arch manifest entry)
# from the architecture of the JVM running the build, and jpackage bundles a runtime cut from its
# own JDK. Left unexported, mvn falls back to whatever JDK its launcher finds, which on Apple
# silicon is arm64, and an Intel bundle would ship the arm64 SWT natives.
export JAVA_HOME

# The oldest runtime a release may bundle. jpackage copies its own JDK into the app, so building
# with an older update installed would ship that update's vulnerabilities. 25.0.5.1 carries the
# fix for CVE-2026-114938 (the x86-64 Base64 decode intrinsic). For a test build on an older
# update, override it: make MIN_JAVA_RUNTIME=25.0.5 app
MIN_JAVA_RUNTIME = 25.0.5.1
JAVA_RUNTIME = $(shell . "$(JAVA_HOME)/release" 2>/dev/null && echo $${JAVA_RUNTIME_VERSION%%[+-]*})

# Prefix for any recipe that shells out to the JDK, so a missing toolchain fails
# with an explanation rather than "/bin/jpackage: No such file or directory".
REQUIRE_JDK = @test -x "$(JPACKAGER)" || $(MISSING_JDK); $(CHECK_JDK_UPDATE)

define CHECK_JDK_UPDATE
if [ "$$(printf '%s\n%s\n' "$(MIN_JAVA_RUNTIME)" "$(JAVA_RUNTIME)" | sort -V | head -n 1)" != "$(MIN_JAVA_RUNTIME)" ]; then \
	echo "JDK at '$(JAVA_HOME)' is $(JAVA_RUNTIME); a release needs $(MIN_JAVA_RUNTIME) or later."; \
	echo "Install a newer JDK $(JAVA_VERSION), or for a test build: make MIN_JAVA_RUNTIME=$(JAVA_RUNTIME) <target>"; \
	exit 1; fi
endef

define MISSING_JDK
{ \
	echo "jpackage not found at '$(JPACKAGER)'."; \
	echo ""; \
	echo "This build needs a full JDK $(JAVA_VERSION); jpackage is not a separate install."; \
	echo "Installed JDKs:"; \
	/usr/libexec/java_home -V 2>&1 | sed -e "s/^/  /"; \
	echo ""; \
	echo "Install one with:  brew install --cask temurin@$(JAVA_VERSION)"; \
	echo "Or point the build at an existing JDK:  make JAVA_HOME=/path/to/jdk <target>"; \
	exit 1; }
endef
SOURCES := $(shell find src)
PKG_SOURCES := $(shell find package)

all: app

# Report the resolved toolchain; handy when a build fails to find jpackage.
toolchain:
	@echo "JAVA_HOME = $(JAVA_HOME)"
	@echo "JPACKAGER = $(JPACKAGER)"
	@echo "ARCH      = $(ARCH)"
	@echo "RUNTIME   = $(JAVA_RUNTIME) (release minimum $(MIN_JAVA_RUNTIME))"

check-jdk:
	$(REQUIRE_JDK)
	@echo "jpackage $(shell "$(JPACKAGER)" --version 2>/dev/null) at $(JPACKAGER)"

icons: package/macosx/CSView.icns package/windows/CSView.ico

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

jar: $(ARCH_JAR)

# Maven always writes target/$(JAR_FILE), whichever architecture it built for, so each
# architecture keeps its own copy for jpackage. The manifest check catches a jar built by a JVM of
# the other architecture, which would otherwise ship SWT natives that cannot load.
$(ARCH_JAR): $(SOURCES) pom.xml
	$(REQUIRE_JDK)
	mvn package
	@unzip -p target/$(JAR_FILE) META-INF/MANIFEST.MF | grep -q "^SWT-Arch: $(ARCH)" || { \
		echo "target/$(JAR_FILE) holds SWT for another architecture than $(ARCH)."; \
		echo "Is mvn running on the JDK at $(JAVA_HOME)?"; exit 1; }
	rm -rf $(APP_INPUT)
	mkdir -p $(APP_INPUT)
	cp target/$(JAR_FILE) $@

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

deps: $(ARCH_JAR)
	jdeps --list-deps $(ARCH_JAR)

app: $(APP_BUNDLE)

# jpackage refuses to write an app image over an existing one ("Application destination directory
# ... already exists"), so clear the previous bundle rather than leaving the build to fail. The
# same rm also stops a failed build leaving the last version's bundle in place looking current.
$(APP_BUNDLE): package/macosx/CSView.icns $(ARCH_JAR)
	$(REQUIRE_JDK)
	rm -rf $@
	$(call stage_mac_resources,$(STAGED_RESOURCES)/direct,$(DIRECT_MIN_OS))
	$(JPACKAGER) -n CSView \
		--type app-image \
		--input $(APP_INPUT) \
		--main-class net.kothar.csview.cocoa.MacLoader \
		--main-jar $(JAR_FILE) \
		--java-options "-XstartOnFirstThread" \
		--app-version $(VERSION) \
		--description "Fast viewer for CSV files" \
		--vendor "Kothar Labs" \
		--dest $(dir $(APP_BUNDLE)) \
		--icon package/macosx/CSView.icns \
		--resource-dir $(STAGED_RESOURCES)/direct \
		--mac-package-name CSView \
		--mac-package-identifier net.kothar.csview \
		--mac-app-category productivity \
		--mac-entitlements package/macosx/CSView.entitlements \
		--file-associations package/macosx/csv-files.properties \
		--file-associations package/macosx/tsv-files.properties \
		--add-modules $(RUNTIME_MODULES)

appstore: $(APPSTORE_PKG)

pkg: $(APP_PKG)

$(APP_PKG): $(APP_BUNDLE)
	for item in `find "$<" -depth -type d -name "*.framework" -or -name "*.dylib" -or -name "*.bundle" -or -name CSView -or -name jspawnhelper | sed -e "s/\(.*framework\)/\1\/Versions\/A\//"`;\
		do codesign -vvvv --force --deep --options runtime --entitlements package/macosx/CSView.entitlements \
			--sign "$(APP_KEY)" --timestamp "$$item" --prefix net.kothar.csview. ;\
		done
	productbuild --component $(APP_BUNDLE) /Applications \
		--sign "$(INSTALLER_KEY)" \
		--product $(APP_BUNDLE)/Contents/Info.plist \
		$(APP_PKG)

# Unlike the DMG this cannot reuse $(APP_BUNDLE), so it builds its own app image: the App Store
# bundle declares a different LSMinimumSystemVersion and is signed with the App Store identities
# under --mac-app-store. It used to depend on $(APP_BUNDLE) anyway, which built a bundle that was
# then thrown away.
$(APPSTORE_PKG): package/macosx/CSView.icns $(ARCH_JAR)
	$(REQUIRE_JDK)
	rm -f $@
	rm -rf $(JPACKAGE_OUT)
	$(call stage_mac_resources,$(STAGED_RESOURCES)/appstore,$(APPSTORE_MIN_OS))
	$(JPACKAGER) -n CSView \
		--verbose \
		--type pkg \
		--input $(APP_INPUT) \
		--main-class net.kothar.csview.cocoa.MacLoader \
		--main-jar $(JAR_FILE) \
		--java-options "-XstartOnFirstThread" \
		--app-version $(VERSION) \
		--description "Fast viewer for CSV files" \
		--vendor "Kothar Labs" \
		--dest $(JPACKAGE_OUT) \
		--icon package/macosx/CSView.icns \
		--resource-dir $(STAGED_RESOURCES)/appstore \
		--mac-app-store \
		--mac-sign \
		--mac-signing-key-user-name "$(USER_NAME)" \
		--mac-package-name CSView \
		--mac-package-identifier net.kothar.csview \
		--mac-app-category productivity \
		--mac-entitlements package/macosx/CSView.entitlements \
		--file-associations package/macosx/csv-files.properties \
		--file-associations package/macosx/tsv-files.properties \
		--add-modules $(RUNTIME_MODULES)
	mv $(JPACKAGE_PKG) $@

dmg: $(APP_DMG)

# Wraps the app image built above instead of building a second one from the jar, so the DMG ships
# exactly the bundle that "make app" produced and "make sandbox"/"make sign" signed. Every option
# that shapes an app image (--input, --main-jar, --java-options, --add-modules) is rejected
# alongside --app-image, and the icon, file associations, identifier and minimum OS version are
# already baked into the bundle. --app-version here only names the output file.
$(APP_DMG): $(APP_BUNDLE)
	$(REQUIRE_JDK)
	rm -f $@
	rm -rf $(JPACKAGE_OUT)
	$(call stage_mac_resources,$(STAGED_RESOURCES)/direct,$(DIRECT_MIN_OS))
	$(JPACKAGER) -n CSView \
		--verbose \
		--type dmg \
		--app-image $(APP_BUNDLE) \
		--app-version $(VERSION) \
		--dest $(JPACKAGE_OUT) \
		--resource-dir $(STAGED_RESOURCES)/direct \
		--mac-sign \
		--mac-signing-key-user-name "$(USER_NAME)"
	mv $(JPACKAGE_DMG) $@

zip: $(APP_ZIP)

$(APP_ZIP): $(APP_BUNDLE).signed
	rm -f $@
	cd $(dir $(APP_BUNDLE)) && zip -r -D $(abspath $@) $(APP_NAME).app

notarize-dmg $(APP_DMG).notarized: $(APP_DMG)
	 xcrun altool --notarize-app --primary-bundle-id net.kothar.csview \
	 --file $< $(APPSTORE_CREDS) | grep RequestUUID | sed -e "s/.*= //" > $(APP_DMG).notarized

staple-dmg $(APP_DMG).stapled: $(APP_DMG).notarized
	xcrun stapler staple $(APP_DMG)
	touch $(APP_DMG).stapled

validate-appstore: $(APPSTORE_PKG)
	xcrun altool --validate-app --file $(APPSTORE_PKG) -t osx \
	$(APPSTORE_CREDS)

notarization-status:
	xcrun altool --notarization-history 0 $(APPSTORE_CREDS)

status-dmg:
	xcrun altool --notarization-info `cat $(APP_DMG).notarized` $(APPSTORE_CREDS)

upload-appstore: $(APPSTORE_PKG)
	xcrun altool --upload-app -f $(APPSTORE_PKG) -t osx $(APPSTORE_CREDS)

# Removes this version's build outputs. Older versions are left alone in bundles/, since they may
# be uploaded or notarized artifacts; use clean-bundles to clear those out too.
clean:
	# sqlite3 "~/Library/Application Support/com.apple.TCC/Tcc.db" 'delete from access where client like "%CSView%"'
	mvn clean
	rm -f *.log
	rm -rf bundles/*/$(APP_NAME).app bundles/*/$(APP_NAME).app.signed
	rm -f bundles/CSView-$(VERSION)-*
	# Where earlier versions of this Makefile put the bundle and jpackage's input.
	rm -rf bundles/$(APP_NAME).app bundles/$(APP_NAME).app.zip bundles/$(APP_NAME).app.signed package/app

# Clears every packaged artifact, including older versions left over from previous releases.
clean-bundles:
	rm -rf bundles

.PHONY: package icons appstore dmg pkg zip resign verify sandbox app jar clean clean-bundles toolchain check-jdk
