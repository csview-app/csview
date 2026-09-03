/*
 * Copyright 2016 - 2026 Kothar Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.kothar.csview.ui.update;

import org.eclipse.swt.SWT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

/**
 * Details of the CSView 2 release, and a record of whether the user has already seen the notice
 * advertising it.
 * <p>
 * The notice is only offered where CSView 2 is a sensible thing to point at: an Apple Silicon Mac
 * running macOS 13 or later, which is what the App Store build of CSView 1.x targets. That includes
 * the Intel build of CSView running under Rosetta, which is exactly the user who stands to gain
 * most from a native app, so an Intel architecture is checked against Rosetta before giving up.
 * Once the user
 * has opened the notice it is not shown again; the flag is stored in the user preferences so it
 * survives a restart, but a failure to read or write those preferences only costs us the memory of
 * having shown it, so it is never allowed to reach the UI as an error.
 */
public class UpdateNotice {

    public static final String NAME = "CSView 2 for macOS";
    public static final String APP_STORE_URL = "https://apps.apple.com/us/app/csview-2/id1540184805";
    public static final String REQUIREMENTS = "\u00a31.99 one-time purchase \u00b7 macOS 12 or later \u00b7 Apple Silicon and Intel";

    public static final String[] FEATURES = {
            "Ground-up native macOS rewrite",
            "Apple Silicon optimised",
            "New high-performance indexing engine",
            "Handles 300M+ row files",
            "Full-file search, custom delimiters and encodings",
            "No subscription, no cloud, no telemetry",
    };

    /** Matches LSMinimumSystemVersion in the packaged Info.plist. */
    private static final int MIN_MACOS_VERSION = 13;

    /** Apple's flag for a process running under Rosetta translation. Absent on an Intel Mac. */
    private static final String TRANSLATED_FLAG = "sysctl.proc_translated";
    private static final long TRANSLATED_TIMEOUT_SECONDS = 5;

    private static final String PREFERENCE_NODE = "net/kothar/csview";
    private static final String SEEN_KEY = "update.notice.csview2.seen";

    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Fallback for when the preference store is unavailable, e.g. a locked-down sandbox. */
    private static boolean seenThisSession;

    /** Cached answer from sysctl, so the notice never costs more than one process. */
    private static Boolean translated;

    private UpdateNotice() {
    }

    /**
     * @return true if this platform has a CSView 2 release to advertise
     */
    public static boolean isSupportedPlatform() {
        return isSupportedPlatform(SWT.getPlatform(), System.getProperty("os.arch"),
                System.getProperty("os.version"), UpdateNotice::isTranslated);
    }

    /**
     * Split out from the system properties so that it can be tested off a Mac. Whether we are being
     * translated is supplied lazily, because answering it costs a process.
     */
    static boolean isSupportedPlatform(String swtPlatform, String architecture, String osVersion,
            BooleanSupplier translationCheck) {
        if (!"cocoa".equals(swtPlatform)) {
            return false;
        }

        if (majorVersion(osVersion) < MIN_MACOS_VERSION) {
            return false;
        }

        // A JDK running under Rosetta reports an Intel architecture, but is on an Apple Silicon Mac
        return isArm(architecture) || translationCheck.getAsBoolean();
    }

    private static boolean isArm(String architecture) {
        return "aarch64".equals(architecture) || "arm64".equals(architecture);
    }

    /**
     * @return true if this process is an Intel binary being translated by Rosetta
     */
    private static synchronized boolean isTranslated() {
        if (translated == null) {
            translated = readTranslatedFlag();
        }
        return translated;
    }

    private static boolean readTranslatedFlag() {
        return readFlag("/usr/sbin/sysctl", "-n", TRANSLATED_FLAG);
    }

    /**
     * Run a command that prints a single sysctl value, and report whether it printed 1. The command
     * is a parameter so that the reading can be tested off a Mac.
     *
     * @return false for any command that cannot be run, times out, or prints anything else
     */
    static boolean readFlag(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectError(Redirect.DISCARD)
                    .start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {
                output = reader.readLine();
            }

            if (!process.waitFor(TRANSLATED_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return false;
            }

            // An Intel Mac has no such flag, and reports an error rather than a value
            return output != null && "1".equals(output.trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            // Reading the flag is a nicety; the sandbox may well refuse us the process
            System.out.println("Unable to run " + command[0] + ": " + e);
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * @return the leading component of a macOS version such as "13.5.2", or -1 if it cannot be read
     */
    private static int majorVersion(String osVersion) {
        if (osVersion == null) {
            return -1;
        }

        int dot = osVersion.indexOf('.');
        try {
            return Integer.parseInt((dot < 0 ? osVersion : osVersion.substring(0, dot)).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return true if the notice should currently be shown to the user
     */
    public static boolean isVisible() {
        return isSupportedPlatform() && !hasBeenSeen();
    }

    public static boolean hasBeenSeen() {
        if (seenThisSession) {
            return true;
        }
        try {
            return preferences().getBoolean(SEEN_KEY, false);
        } catch (RuntimeException e) {
            // No usable preference store; fall back to the in-memory flag
            return false;
        }
    }

    /**
     * Record that the user has seen the notice, so that it is not offered again, and let any
     * displayed notices know that they should hide themselves.
     */
    public static void markSeen() {
        setSeen(true);
    }

    /**
     * Show the notice again. Only reachable from the debug menu, to check the first-run appearance.
     */
    public static void reset() {
        setSeen(false);
    }

    private static void setSeen(boolean seen) {
        seenThisSession = seen;
        try {
            Preferences preferences = preferences();
            preferences.putBoolean(SEEN_KEY, seen);
            preferences.flush();
        } catch (Exception e) {
            // The notice state is not worth interrupting the user for
            System.out.println("Unable to store update notice state: " + e);
        }

        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private static Preferences preferences() {
        return Preferences.userRoot().node(PREFERENCE_NODE);
    }

    /**
     * Register a callback to be run whenever {@link #isVisible()} may have changed.
     */
    public static void addVisibilityListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeVisibilityListener(Runnable listener) {
        listeners.remove(listener);
    }
}
