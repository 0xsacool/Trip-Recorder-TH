package org.triprecorderng;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Signer comparison and verified v1 APK fallback; independent of Android for regression tests. */
final class ApkSigners {
    private ApkSigners() {}

    enum Comparison { MATCH, MISMATCH, UNREADABLE }

    static Comparison compare(Set<String> installed, Set<String> downloaded) {
        if (installed == null || downloaded == null || installed.isEmpty() || downloaded.isEmpty()) {
            return Comparison.UNREADABLE;
        }
        return installed.equals(downloaded) ? Comparison.MATCH : Comparison.MISMATCH;
    }

    static String fingerprint(byte[] certificate) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate);
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(digits[(b & 0xff) >>> 4]);
            hex.append(digits[b & 0x0f]);
        }
        return hex.toString();
    }

    /** The EV TripLog releases include v1 signatures. Reading each entry to EOF makes JarFile
     * verify its signed digest before returning certificates. Reject unsigned entries, inconsistent
     * signers, duplicate names, and malformed archives; never merely trust META-INF certificate bytes.
     * Android's package installer still performs its own independent signature enforcement. */
    static Set<String> readVerifiedJar(File file) throws IOException, GeneralSecurityException {
        Set<String> expected = null;
        Set<String> names = new HashSet<>();
        long bytes = 0;
        try (JarFile jar = new JarFile(file, true)) {
            byte[] buf = new byte[8192];
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!names.add(entry.getName())) throw new GeneralSecurityException("duplicate APK entry");
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    int count;
                    while ((count = in.read(buf)) != -1) {
                        bytes += count;
                        if (bytes > (128L << 20)) throw new IOException("APK expanded size exceeds limit");
                    }
                }
                Certificate[] certs = entry.getCertificates();
                if (certs == null || certs.length == 0) throw new GeneralSecurityException("unsigned APK entry");
                Set<String> found = new HashSet<>();
                for (Certificate cert : certs) found.add(fingerprint(cert.getEncoded()));
                if (expected == null) expected = found;
                else if (!expected.equals(found)) throw new GeneralSecurityException("inconsistent APK signers");
            }
        }
        if (expected == null || expected.isEmpty()) throw new GeneralSecurityException("no verified APK signers");
        return expected;
    }
}
