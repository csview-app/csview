package net.kothar.csview.ui.update;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateNoticeTest {

    @Test
    public void appleSiliconOnMacOs13OrLaterIsSupported() {
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "13.0"));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "13.5.2"));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "26.1"));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "arm64", "14.2"));
    }

    @Test
    public void olderMacOsIsNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "12.7.4"));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "11.0"));
        // Big Sur reported through the compatibility version, and every earlier release
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "10.16"));
    }

    @Test
    public void intelIsNotSupported() {
        // Including a JDK running under Rosetta on an Apple Silicon Mac
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "x86_64", "14.2"));
    }

    @Test
    public void otherPlatformsAreNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("gtk", "aarch64", "6.18.44"));
        assertFalse(UpdateNotice.isSupportedPlatform("win32", "x86_64", "10.0"));
    }

    @Test
    public void unreadableVersionsAreNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", null));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", ""));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "unknown"));
    }
}
