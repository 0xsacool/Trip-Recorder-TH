package org.triprecorderng;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-facing semantic versions with backward compatibility for the original dev-N/stable-N releases.
 * Keep the original GitHub tag/APK versionName intact for signature and versionName verification.
 */
final class VersionScheme {
    private VersionScheme() {}

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})(?:-dev\\.([1-9][0-9]{0,5}))?$");
    private static final Pattern LEGACY = Pattern.compile("^(stable|dev)-([1-9][0-9]{0,8})$");

    static final class Version {
        final String raw;
        final String channel;
        final int major, minor, patch, pre;
        final boolean legacy;
        final int legacyNumber;

        Version(String raw, String channel, int major, int minor, int patch, int pre,
                boolean legacy, int legacyNumber) {
            this.raw = raw;
            this.channel = channel;
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.pre = pre;
            this.legacy = legacy;
            this.legacyNumber = legacyNumber;
        }

        String display() {
            return major + "." + minor + "." + patch + (pre > 0 ? "-dev." + pre : "");
        }
    }

    static Version parse(String raw) {
        if (raw == null) return null;
        Matcher modern = SEMVER.matcher(raw);
        if (modern.matches()) {
            try {
                int major = Integer.parseInt(modern.group(1));
                int minor = Integer.parseInt(modern.group(2));
                int patch = Integer.parseInt(modern.group(3));
                String dev = modern.group(4);
                int pre = dev == null ? 0 : Integer.parseInt(dev);
                return new Version(raw, pre > 0 ? "dev" : "stable",
                        major, minor, patch, pre, false, 0);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher old = LEGACY.matcher(raw);
        if (!old.matches()) return null;
        int n;
        try {
            n = Integer.parseInt(old.group(2));
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (old.group(1).equals("stable")) {
            // stable-34 was the last old public Stable. Legacy bridge releases remain 1.0.1.
            return new Version(raw, "stable", 1, 0, n <= 34 ? 0 : 1, 0, true, n);
        }
        if (n <= 32) {
            // Dev 32 contains the same app features as Stable 34; it is a 1.0.0 prerelease.
            return new Version(raw, "dev", 1, 0, 0, n, true, n);
        }
        // The first compatibility bridge after Dev 32 maps to 1.1.0-dev.1.
        return new Version(raw, "dev", 1, 1, 0, Math.max(1, n - 34), true, n);
    }

    /** Positive when a is newer than b. A final release outranks its prereleases. */
    static int compare(Version a, Version b) {
        int c = Integer.compare(a.major, b.major);
        if (c != 0) return c;
        c = Integer.compare(a.minor, b.minor);
        if (c != 0) return c;
        c = Integer.compare(a.patch, b.patch);
        if (c != 0) return c;
        if (a.pre == 0 && b.pre != 0) return 1;
        if (b.pre == 0 && a.pre != 0) return -1;
        c = Integer.compare(a.pre, b.pre);
        if (c != 0) return c;
        if (a.legacy && b.legacy) return Integer.compare(a.legacyNumber, b.legacyNumber);
        if (a.legacy != b.legacy) return a.legacy ? -1 : 1;
        return 0;
    }
}
