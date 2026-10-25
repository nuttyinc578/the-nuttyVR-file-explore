package com.nuttyvr.explorer;

import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.HostSystem;
import com.github.junrar.volume.FileVolume;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/** Streaming extraction into a fresh folder. Archive names never become unchecked storage paths. */
public final class ArchiveFiles {
    public interface Target {
        void directory(String path) throws IOException;
        OutputStream file(String path) throws IOException;
    }
    public static final long MAX_BYTES = 8L << 30;
    public static final int MAX_ENTRIES = 100000;
    public static final long MAX_DICTIONARY = 64L << 20;
    private ArchiveFiles() {}

    public static boolean supported(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".rar")
                || n.endsWith(".tar.gz") || n.endsWith(".tgz");
    }
    public static String folderName(String name) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        int suffix = lower.endsWith(".tar.gz") ? 7 : 4;
        String stem = name.substring(0, Math.max(0, name.length() - suffix));
        if (stem.trim().isEmpty() || stem.equals(".") || stem.equals("..")) stem = "Extracted files";
        return LocalFiles.validName(stem);
    }

    public static int extract(File source, String name, Target target, LocalFiles.Cancellation cancel,
                              LocalFiles.Progress progress) throws IOException {
        return extract(source, name, target, cancel, progress, MAX_BYTES, MAX_ENTRIES);
    }
    static int extract(File source, String name, Target target, LocalFiles.Cancellation cancel,
                       LocalFiles.Progress progress, long maxBytes, int maxEntries) throws IOException {
        cancel.check();
        if (!supported(name)) throw new IOException("Choose a ZIP, TAR or RAR archive.");
        if (!source.isFile() || Files.isSymbolicLink(source.toPath())) throw new IOException("The archive is unavailable.");
        Extraction job = new Extraction(target, cancel, progress, maxBytes, maxEntries);
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".zip")) unzip(source, job);
            else if (lower.endsWith(".rar")) unrar(source, job);
            else untar(source, lower.endsWith(".gz") || lower.endsWith(".tgz"), job);
            cancel.check();
            return job.files.size();
        } catch (RarException e) {
            cancel.check();
            throw new IOException("Cannot extract this RAR: it may be damaged, password protected, multipart, or require a dictionary larger than 64 MB. " + e.getClass().getSimpleName(), e);
        } catch (IllegalArgumentException e) {
            throw new IOException("This archive contains invalid or unsupported data.", e);
        }
    }

    private static void unzip(File source, Extraction job) throws IOException {
        try (ZipFile zip = ZipFile.builder().setFile(source).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                job.cancel.check();
                if (entry.getGeneralPurposeBit().usesEncryption()) throw new IOException("Password protected ZIP archives are not supported yet.");
                int type = entry.getUnixMode() & 0170000;
                if (entry.isUnixSymlink() || type != 0 && type != 0100000 && type != 0040000)
                    throw new IOException("Archive links and special files are not extracted.");
                if (!zip.canReadEntryData(entry)) throw new IOException("This ZIP uses an unsupported compression method.");
                String path = job.entry(entry.getName(), entry.isDirectory());
                if (!entry.isDirectory()) {
                    CRC32 crc = new CRC32();
                    try (InputStream in = zip.getInputStream(entry); OutputStream out = job.output(path, entry.getSize())) {
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); crc.update(buffer, 0, n); }
                    }
                    if (entry.getCrc() < 0 || crc.getValue() != entry.getCrc()) throw new IOException("The ZIP checksum failed for " + path + ". The archive may be damaged.");
                }
            }
        }
    }
    private static void untar(File source, boolean gzip, Extraction job) throws IOException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(source));
             InputStream decoded = gzip ? new GZIPInputStream(raw) : raw;
             TarArchiveInputStream tar = new TarArchiveInputStream(decoded)) {
            // Empty files are not TAR archives. Inspect the first complete record before parsing.
            // TarArchiveInputStream validates record checksums and truncated entry data below.
            if (!gzip && source.length() < 1024) throw new IOException("This TAR is incomplete.");
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                job.cancel.check();
                if (!entry.isCheckSumOK()) throw new IOException("This TAR has a damaged header.");
                byte type = entry.getLinkFlag();
                if ((type != TarConstants.LF_NORMAL && type != TarConstants.LF_OLDNORM && type != TarConstants.LF_DIR) || entry.isSparse())
                    throw new IOException("Archive links, sparse files and special files are not extracted.");
                if (!tar.canReadEntryData(entry)) throw new IOException("This TAR uses an unsupported entry format.");
                String path = job.entry(entry.getName(), entry.isDirectory());
                if (!entry.isDirectory()) {
                    try (OutputStream out = job.output(path, entry.getSize())) {
                        byte[] buffer = new byte[65536]; int n;
                        while ((n = tar.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
            }
            // Consume gzip's trailer too, so a damaged gzip checksum is not silently accepted.
            if (gzip) { byte[] discard = new byte[8192]; int n;
                while ((n = decoded.read(discard)) != -1) { job.cancel.check(); job.consume(n); }
            }
        }
    }
    private static void unrar(File source, Extraction job) throws IOException, RarException {
        ArchiveOptions options = ArchiveOptions.builder().maxDictionarySize(MAX_DICTIONARY).build();
        // Supply only the selected file; never let an archive resolve another path as a volume.
        try (Archive rar = new Archive((archive, previous) -> {
            job.cancel.check();
            if (previous != null) throw new IOException("Multipart RAR archives are not supported yet.");
            return new FileVolume(archive, source);
        }, options)) {
            if (rar.isPasswordProtected()) throw new IOException("Password protected RAR archives are not supported yet.");
            FileHeader entry;
            while ((entry = rar.nextFileHeader()) != null) {
                job.cancel.check();
                if (entry.isSplitBefore() || entry.isSplitAfter()) throw new IOException("Multipart RAR archives are not supported yet.");
                if (entry.isEncrypted()) throw new IOException("Password protected RAR archives are not supported yet.");
                int type = entry.getFileAttr() & 0170000;
                boolean unix = entry.getHostOS() == HostSystem.unix || entry.isRar5Family() && entry.getRar5HostOsValue() == 1;
                if (entry.getRedirection() != null || unix && type != 0 && type != 0100000 && type != 0040000)
                    throw new IOException("Archive links and special files are not extracted.");
                String path = job.entry(entry.getFileName(), entry.isDirectory());
                if (!entry.isDirectory()) {
                    long size = entry.isUnpSizeUnknown() ? -1 : entry.getFullUnpackSize();
                    try (OutputStream out = job.output(path, size)) { rar.extractFile(entry, out); }
                }
            }
        }
    }

    static String safePath(String name, boolean directory) throws IOException {
        if (name == null || name.length() > 4096) throw new IOException("An archive path is too long.");
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) throw new IOException("Absolute archive paths are not allowed.");
        ArrayList<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) throw new IOException("Archive paths cannot leave the extraction folder.");
            parts.add(LocalFiles.validName(part));
            if (parts.size() > 64) throw new IOException("This archive has too many nested folders.");
        }
        if (parts.isEmpty() && !directory) throw new IOException("An archive file has an empty name.");
        return String.join("/", parts);
    }
    private static final class Extraction {
        final Target target;
        final LocalFiles.Cancellation cancel;
        final LocalFiles.Progress progress;
        final Set<String> directories = new HashSet<>(), files = new HashSet<>();
        final long maxBytes; final int maxEntries;
        int entries; long bytes;
        Extraction(Target t, LocalFiles.Cancellation c, LocalFiles.Progress p, long b, int e) {
            target = t; cancel = c; progress = p; maxBytes = b; maxEntries = e; directories.add("");
        }
        String entry(String name, boolean directory) throws IOException {
            cancel.check();
            if (++entries > maxEntries) throw new IOException("This archive exceeds the extraction entry limit.");
            String path = safePath(name, directory);
            progress.update(0, path);
            String[] parts = path.split("/"); String parent = "";
            for (int i = 0; i < parts.length - (directory ? 0 : 1); i++) {
                if (parts[i].isEmpty()) continue;
                parent = parent.isEmpty() ? parts[i] : parent + "/" + parts[i];
                if (files.contains(parent)) throw new IOException("An archive file conflicts with a folder: " + parent);
                if (directories.add(parent)) target.directory(parent);
            }
            if (!directory && (directories.contains(path) || !files.add(path)))
                throw new IOException("The archive contains duplicate or conflicting files: " + path);
            return path;
        }
        void consume(long n) throws IOException {
            if (n < 0 || n > maxBytes - bytes) throw new IOException("This archive exceeds the extraction size limit (8 GB).");
            bytes += n;
        }
        OutputStream output(String path, long expected) throws IOException {
            if (expected < -1 || expected > maxBytes - bytes) throw new IOException("This archive exceeds the extraction size limit (8 GB).");
            return new OutputStream() {
                final OutputStream out = target.file(path);
                long written;
                @Override public void write(int b) throws IOException { write(new byte[]{(byte)b}, 0, 1); }
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    cancel.check(); consume(len);
                    if (expected >= 0 && len > expected - written) throw new IOException("An archive entry is larger than its declared size.");
                    out.write(b, off, len); written += len; progress.update(len, path);
                }
                @Override public void close() throws IOException {
                    out.close();
                    if (expected >= 0 && written != expected) throw new IOException("An archive entry is incomplete: " + path);
                }
            };
        }
    }

    /** Local output uses create-new and rejects existing paths, including links. */
    public static Target localTarget(File root) throws IOException {
        if (!root.isDirectory() || Files.isSymbolicLink(root.toPath())) throw new IOException("Choose a new extraction folder.");
        final Path base = root.getCanonicalFile().toPath();
        return new Target() {
            private Path path(String relative) throws IOException {
                Path candidate = base.resolve(relative);
                if (!candidate.normalize().startsWith(base) || !LocalFiles.inside(candidate.toFile(), base.toFile()))
                    throw new IOException("Archive path leaves the extraction folder.");
                Path cursor = candidate;
                while (cursor != null && !cursor.equals(base)) {
                    if (Files.isSymbolicLink(cursor)) throw new IOException("Archive output cannot follow links.");
                    cursor = cursor.getParent();
                }
                return candidate;
            }
            public void directory(String relative) throws IOException { Files.createDirectory(path(relative)); }
            public OutputStream file(String relative) throws IOException {
                return Files.newOutputStream(path(relative), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            }
        };
    }
}
