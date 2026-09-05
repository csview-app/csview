package net.kothar.csview.ui.update;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateNoticeTest {

    @Test
    public void macOs12OrLaterIsSupported() {
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "12.0"));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "12.7.4"));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "13.5.2"));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "26.1"));
    }

    @Test
    public void olderMacOsIsNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "11.7.10"));
        // The floor the DMG build declares, so the oldest macOS CSView 1.x actually runs on
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "11.0"));
        // Big Sur reported through the compatibility version, and every earlier release
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "10.16"));
    }

    @Test
    public void otherPlatformsAreNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("gtk", "6.18.44"));
        assertFalse(UpdateNotice.isSupportedPlatform("win32", "10.0"));
    }

    @Test
    public void unreadableVersionsAreNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", null));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", ""));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "unknown"));
    }
}
