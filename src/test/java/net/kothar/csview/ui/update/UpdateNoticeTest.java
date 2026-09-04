package net.kothar.csview.ui.update;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateNoticeTest {

    private static final BooleanSupplier NATIVE = () -> false;
    private static final BooleanSupplier ROSETTA = () -> true;

    /** Counts how often the translation check is consulted, since answering it costs a process. */
    private final AtomicInteger translationChecks = new AtomicInteger();

    private final BooleanSupplier counted = () -> {
        translationChecks.incrementAndGet();
        return false;
    };

    @Test
    public void appleSiliconOnMacOs12OrLaterIsSupported() {
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "12.0", NATIVE));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "12.7.4", NATIVE));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "13.0", NATIVE));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "13.5.2", NATIVE));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "26.1", NATIVE));
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "arm64", "14.2", NATIVE));
    }

    @Test
    public void anIntelBuildUnderRosettaIsSupported() {
        assertTrue(UpdateNotice.isSupportedPlatform("cocoa", "x86_64", "14.2", ROSETTA));
    }

    @Test
    public void anIntelMacIsNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "x86_64", "14.2", NATIVE));
    }

    @Test
    public void olderMacOsIsNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "11.7.10", ROSETTA));
        // The floor the DMG build declares, so the oldest macOS CSView 1.x actually runs on
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "11.0", ROSETTA));
        // Big Sur reported through the compatibility version, and every earlier release
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "10.16", ROSETTA));
    }

    @Test
    public void otherPlatformsAreNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("gtk", "aarch64", "6.18.44", ROSETTA));
        assertFalse(UpdateNotice.isSupportedPlatform("win32", "x86_64", "10.0", ROSETTA));
    }

    @Test
    public void unreadableVersionsAreNotSupported() {
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", null, ROSETTA));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "", ROSETTA));
        assertFalse(UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "unknown", ROSETTA));
    }

    @Test
    public void aFlagOfOneIsRead() {
        assertTrue(UpdateNotice.readFlag("/bin/echo", "1"));
    }

    @Test
    public void anyOtherFlagValueIsNotTranslation() {
        assertFalse(UpdateNotice.readFlag("/bin/echo", "0"));
        assertFalse(UpdateNotice.readFlag("/bin/echo", "11"));
        assertFalse(UpdateNotice.readFlag("/bin/echo"));
    }

    @Test
    public void aFailedCommandIsNotTranslation() {
        // An Intel Mac has no such sysctl, and reports the error on stderr with no value
        assertFalse(UpdateNotice.readFlag("/bin/sh", "-c", "echo 'unknown oid' >&2; exit 1"));
        assertFalse(UpdateNotice.readFlag("/no/such/binary"));
    }

    @Test
    public void rosettaIsOnlyCheckedWhenItCanChangeTheAnswer() {
        UpdateNotice.isSupportedPlatform("gtk", "x86_64", "6.18.44", counted);
        UpdateNotice.isSupportedPlatform("cocoa", "aarch64", "14.2", counted);
        UpdateNotice.isSupportedPlatform("cocoa", "x86_64", "11.7.10", counted);
        assertEquals(0, translationChecks.get());

        UpdateNotice.isSupportedPlatform("cocoa", "x86_64", "14.2", counted);
        assertEquals(1, translationChecks.get());
    }
}
