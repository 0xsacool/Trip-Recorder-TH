package org.triprecorderng;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ApkSignersTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void reject(File file, String test) throws Exception {
        try {
            ApkSigners.readVerifiedJar(file);
            throw new AssertionError(test + " was accepted");
        } catch (GeneralSecurityException | IOException | SecurityException expected) {
            System.out.println("PASS: " + test + " rejected");
        }
    }

    private static File rewrite(File original, boolean tamper) throws IOException {
        File changed = File.createTempFile("signer-test-", ".apk");
        try (ZipFile zip = new ZipFile(original);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(changed.toPath()))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        byte[] data = in.readAllBytes();
                        if (tamper && entry.getName().equals("classes.dex")) data[0] ^= 1;
                        out.write(data);
                    }
                }
                out.closeEntry();
            }
            if (!tamper) {
                out.putNextEntry(new ZipEntry("unsigned-payload.dex"));
                out.write(new byte[] {1, 2, 3});
                out.closeEntry();
            }
        }
        return changed;
    }

    public static void main(String[] args) throws Exception {
        Set<String> a = Collections.singleton(ApkSigners.fingerprint(new byte[] {1, 2, 3}));
        Set<String> b = Collections.singleton(ApkSigners.fingerprint(new byte[] {4, 5, 6}));
        check(ApkSigners.compare(a, a) == ApkSigners.Comparison.MATCH, "matching signers");
        check(ApkSigners.compare(a, b) == ApkSigners.Comparison.MISMATCH, "different signers");
        check(ApkSigners.compare(a, Collections.emptySet()) == ApkSigners.Comparison.UNREADABLE, "missing download signer");
        check(ApkSigners.compare(Collections.emptySet(), a) == ApkSigners.Comparison.UNREADABLE, "missing installed signer");
        check(ApkSigners.compare(null, null) == ApkSigners.Comparison.UNREADABLE, "both missing");
        System.out.println("PASS: signer matching/mismatch/missing");
        File unsigned = File.createTempFile("unsigned-apk-", ".apk");
        try {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(unsigned.toPath()))) {
                out.putNextEntry(new ZipEntry("classes.dex"));
                out.write(new byte[] {1});
                out.closeEntry();
            }
            reject(unsigned, "unsigned archive");
        } finally {
            unsigned.delete();
        }
        Set<String> previous = null;
        for (String path : args) {
            File apk = new File(path);
            Set<String> verified = ApkSigners.readVerifiedJar(apk);
            if (previous != null) check(ApkSigners.compare(previous, verified) == ApkSigners.Comparison.MATCH, "release signers differ");
            previous = verified;
            System.out.println("PASS: verified " + apk.getName() + " " + verified);
            File tampered = rewrite(apk, true);
            File injected = rewrite(apk, false);
            try {
                reject(tampered, "modified classes.dex");
                reject(injected, "injected unsigned entry");
            } finally {
                tampered.delete();
                injected.delete();
            }
        }
    }
}
