package com.nuttyvr.explorer;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** Local transfers stage their output before publishing it, and never replace a file. */
public final class LocalFiles {
    public interface Progress { void update(long bytes, String name); }
    public static final class Cancellation {
        public volatile boolean cancelled;
        public void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Operation cancelled");
        }
    }
    private LocalFiles() {}

    public static String validName(String input) throws IOException {
        if (input == null || input.trim().isEmpty() || input.equals(".") || input.equals("..")
                || input.indexOf('/') >= 0 || input.indexOf('\\') >= 0 || input.indexOf('\0') >= 0
                || input.codePoints().anyMatch(c -> c < 32) || input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 240) {
            throw new IOException("Use a name of 1–240 bytes without slashes or control characters.");
        }
        return input;
    }

    public static boolean inside(File candidate, File parent) throws IOException {
        Path c = candidate.getCanonicalFile().toPath(), p = parent.getCanonicalFile().toPath();
        return c.startsWith(p);
    }

    public static File unique(File directory, String name) throws IOException {
        validName(name);
        File target = new File(directory, name);
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; target.exists() || Files.isSymbolicLink(target.toPath()); n++) {
            if (n > 10000) throw new IOException("Too many files with that name.");
            target = new File(directory, stem + " (" + n + ")" + suffix);
        }
        return target;
    }

    public static File copy(File source, File directory, Cancellation cancel, Progress progress) throws IOException {
        validateTransfer(source, directory);
        cancel.check();
        File target = unique(directory, source.getName());
        File stage = new File(directory, ".nuttyvr-" + UUID.randomUUID() + ".part");
        try {
            copyContents(source, stage, cancel, progress, 0);
            cancel.check();
            Files.move(stage.toPath(), target.toPath());
            return target;
        } catch (IOException | RuntimeException failure) {
            try { delete(stage, new Cancellation()); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static File move(File source, File directory, Cancellation cancel, Progress progress) throws IOException {
        validateTransfer(source, directory);
        cancel.check();
        File target = unique(directory, source.getName());
        try {
            Files.move(source.toPath(), target.toPath());
            return target;
        } catch (FileAlreadyExistsException e) { throw e;
        } catch (IOException moveFailure) {
            // Cross-volume moves are completed as copy, then delete. Cancellation never deletes the source.
            File copied = copy(source, directory, cancel, progress);
            cancel.check();
            try { delete(source, new Cancellation()); }
            catch (IOException e) { throw new IOException("Copy completed at " + copied + ", but the source could not be fully removed. Check both locations.", e); }
            return copied;
        }
    }

    private static void validateTransfer(File source, File directory) throws IOException {
        if (!source.exists()) throw new IOException("The source no longer exists.");
        if (!directory.isDirectory()) throw new IOException("Choose an existing destination folder.");
        if (Files.isSymbolicLink(source.toPath())) throw new IOException("Symbolic links are not supported.");
        if (source.isDirectory() && inside(directory, source)) throw new IOException("A folder cannot be placed inside itself.");
    }

    private static void copyContents(File source, File target, Cancellation cancel, Progress progress, int depth) throws IOException {
        cancel.check();
        if (depth > 128) throw new IOException("This folder has too many nested levels.");
        if (Files.isSymbolicLink(source.toPath())) throw new IOException("Symbolic links are not copied: " + source.getName());
        if (source.isDirectory()) {
            if (!target.mkdir()) throw new IOException("Cannot create " + target.getName());
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot read " + source.getName());
            for (File child : children) copyContents(child, new File(target, child.getName()), cancel, progress, depth + 1);
        } else {
            if (!source.isFile()) throw new IOException("Unsupported file: " + source.getName());
            long copied = 0;
            try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    cancel.check();
                    out.write(buffer, 0, n);
                    copied += n;
                    progress.update(n, source.getName());
                }
                out.getFD().sync();
            }
            if (copied != source.length()) throw new IOException("The source changed during transfer. Try again.");
        }
        target.setLastModified(source.lastModified());
    }

    public static void delete(File file, Cancellation cancel) throws IOException { delete(file, cancel, 0); }
    private static void delete(File file, Cancellation cancel, int depth) throws IOException {
        cancel.check();
        if (depth > 128) throw new IOException("Too many nested folders to delete.");
        if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return;
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file.getName());
            for (File child : children) delete(child, cancel, depth + 1);
        }
        Files.delete(file.toPath());
    }

    public static File rename(File source, String name) throws IOException {
        validName(name);
        File target = new File(source.getParentFile(), name);
        if (source.equals(target)) return source;
        if (target.exists()) throw new IOException("A file with that name already exists.");
        Files.move(source.toPath(), target.toPath());
        return target;
    }
}
