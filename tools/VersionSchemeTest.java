package org.triprecorderng;

public final class VersionSchemeTest {
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
    private static VersionScheme.Version v(String text) {
        VersionScheme.Version v = VersionScheme.parse(text);
        check(v != null, "parse " + text);
        return v;
    }
    private static void newer(String higher, String lower) {
        check(VersionScheme.compare(v(higher), v(lower)) > 0, higher + " must outrank " + lower);
    }
    public static void main(String[] args) {
        check(v("stable-34").display().equals("1.0.0"), "stable legacy display");
        check(v("dev-32").display().equals("1.0.0-dev.32"), "dev legacy display");
        check(v("dev-35").display().equals("1.1.0-dev.1"), "beta bridge display");
        check(v("stable-36").display().equals("1.0.1"), "stable bridge display");
        check(v("1.1.0-dev.2").channel.equals("dev"), "beta channel");
        check(v("1.1.0").channel.equals("stable"), "stable channel");
        check(v("1.1.0-dev.2").raw.equals("1.1.0-dev.2"), "keep exact APK versionName");
        newer("stable-34", "dev-32");
        newer("dev-35", "stable-34");
        newer("stable-36", "stable-34");
        newer("1.1.0-dev.2", "dev-35");
        newer("1.1.0", "1.1.0-dev.2");
        newer("1.0.2", "1.0.1");
        newer("1.0.1", "stable-36");
        check(VersionScheme.compare(v("1.1.0-dev.2"), v("1.1.0-dev.2")) == 0, "same version");
        check(VersionScheme.compare(v("stable-34"), v("1.0.1")) < 0, "older legacy stable");
        check(VersionScheme.parse("0.1") == null, "local build not a release");
        check(VersionScheme.parse("stable-x") == null, "bad legacy tag");
        check(VersionScheme.parse("1.1.0-dev.0") == null, "bad prerelease zero");
        check(VersionScheme.parse("1.1.0-beta.1") == null, "unsupported prerelease");
        check(VersionScheme.parse("01.1.0") == null, "leading zero");
        System.out.println("PASS: semantic/legacy migration, ordering, channel switching");
    }
}
