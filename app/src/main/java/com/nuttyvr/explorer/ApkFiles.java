package com.nuttyvr.explorer;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/** Private, immutable-by-other-apps snapshots for parsing and handing APKs to Android. */
final class ApkFiles {
    static final String MIME = "application/vnd.android.package-archive";
    static final long RESERVE = 8L * 1024 * 1024;
    private ApkFiles() {}

    static boolean isApk(String name, String mime) {
        return MIME.equals(mime) || name != null && name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    /** Takes ownership of input, including on failure. Incomplete snapshots are removed. */
    static File stage(InputStream input, File directory, long expectedSize, long availableBytes,
                      LocalFiles.Cancellation cancel, LocalFiles.Progress progress) throws IOException {
        File target = null;
        boolean complete = false;
        try {
          try (InputStream in = input) {
            cancel.check();
            long limit = Math.max(0, availableBytes - RESERVE);
            if (expectedSize > limit || limit == 0) throw new IOException("Not enough free space to prepare this APK. Free some device storage and try again.");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare the APK cache.");
            target = File.createTempFile("nuttyvr-", ".apk", directory);
            long copied = 0;
            try (FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    cancel.check();
                    if (n > limit - copied) throw new IOException("Not enough free space to prepare this APK.");
                    out.write(buffer, 0, n);
                    copied += n;
                    progress.update(n, "Preparing APK");
                }
                out.getFD().sync();
            }
            cancel.check();
            if (copied == 0) throw new IOException("This APK is empty.");
            if (expectedSize >= 0 && copied != expectedSize) throw new IOException("The APK changed while being read. Select it again.");
          }
            complete = true;
            return target;
        } finally {
            if (!complete && target != null) Files.deleteIfExists(target.toPath());
        }
    }

    /** Only a direct, regular APK child may be granted to the system installer. */
    static File resolve(File directory, String name) throws IOException {
        if (name == null || name.contains("/") || name.contains("\\") || !name.endsWith(".apk")) {
            throw new FileNotFoundException("APK snapshot is unavailable.");
        }
        File file = new File(directory, name);
        if (Files.isSymbolicLink(file.toPath()) || !file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()) || !file.isFile()) {
            throw new FileNotFoundException("APK snapshot is unavailable. Select the file again.");
        }
        return file;
    }
}
