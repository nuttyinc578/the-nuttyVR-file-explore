package com.nuttyvr.explorer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.archivers.tar.*;
import java.util.zip.GZIPOutputStream;

/** Real archive decoding, output integrity and hostile entry boundaries, without Android mocks. */
public class ArchiveFilesTest {
    static int passed;
    static File root;
    static final LocalFiles.Progress NONE = (n,name)->{};
    interface Check { void run() throws Exception; }
    static void test(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + name); }
    static void require(boolean b) throws Exception { if (!b) throw new Exception("Assertion failed"); }
    static void rejects(Check check) throws Exception {
        try { check.run(); } catch (IOException expected) { return; }
        throw new Exception("Expected archive rejection");
    }
    static File folder() throws Exception { return Files.createTempDirectory(root.toPath(), "output-").toFile(); }
    static LocalFiles.Cancellation token() { return new LocalFiles.Cancellation(); }
    static int extract(File source, File target) throws Exception {
        return ArchiveFiles.extract(source, source.getName(), ArchiveFiles.localTarget(target), token(), NONE);
    }
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static String read(File folder, String path) throws Exception { return new String(Files.readAllBytes(new File(folder, path).toPath()), StandardCharsets.UTF_8); }
    static File zip(String[] paths, byte[][] data, int mode) throws Exception {
        File archive = File.createTempFile("sample-", ".zip", root);
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(archive)) {
            for (int i = 0; i < paths.length; i++) {
                ZipArchiveEntry entry = new ZipArchiveEntry(paths[i]);
                if (mode != 0) entry.setUnixMode(mode);
                out.putArchiveEntry(entry); out.write(data[i]); out.closeArchiveEntry();
            }
        }
        return archive;
    }
    static File zip(String path, String data) throws Exception { return zip(new String[]{path}, new byte[][]{bytes(data)}, 0); }
    static File tar(String path, byte[] data, byte type, boolean gzip) throws Exception {
        File archive = File.createTempFile("sample-", gzip ? ".tar.gz" : ".tar", root);
        try (OutputStream raw = new FileOutputStream(archive);
             OutputStream decoded = gzip ? new GZIPOutputStream(raw) : raw;
             TarArchiveOutputStream out = new TarArchiveOutputStream(decoded)) {
            out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            out.setAddPaxHeadersForNonAsciiNames(true);
            TarArchiveEntry entry = new TarArchiveEntry(path, type);
            if (type == TarConstants.LF_NORMAL) entry.setSize(data.length);
            else if (type == TarConstants.LF_SYMLINK || type == TarConstants.LF_LINK) entry.setLinkName("../../outside");
            out.putArchiveEntry(entry);
            if (type == TarConstants.LF_NORMAL) out.write(data);
            out.closeArchiveEntry();
        }
        return archive;
    }
    static File fixture(String name) { return new File("tests/fixtures", name); }
    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("nuttyvr-archive-").toFile();
        try {
            test("extensions and extraction folder names", () -> {
                for (String name : new String[]{"a.ZIP", "a.tar", "a.RAR", "a.tar.gz", "a.tgz"}) require(ArchiveFiles.supported(name));
                require(!ArchiveFiles.supported("a.apk")); require(!ArchiveFiles.supported("a.7z"));
                require(ArchiveFiles.folderName("a.tar.gz").equals("a")); require(ArchiveFiles.folderName("a.zip").equals("a"));
                require(ArchiveFiles.folderName(".zip").equals("Extracted files"));
            });
            test("ZIP nested files, binary bytes, hidden files and empty directories", () -> {
                byte[] payload = new byte[200000]; new Random(123).nextBytes(payload);
                File archive = zip(new String[]{"folder/data.bin", ".hidden", "empty/"}, new byte[][]{payload, bytes("hidden"), new byte[0]}, 0), out = folder();
                require(extract(archive, out) == 2);
                require(Arrays.equals(payload, Files.readAllBytes(new File(out, "folder/data.bin").toPath())));
                require(read(out, ".hidden").equals("hidden")); require(new File(out, "empty").isDirectory());
            });
            test("empty ZIP is valid", () -> require(extract(zip(new String[0], new byte[0][], 0), folder()) == 0));
            test("ZIP UTF-8 and Windows relative separators", () -> {
                File out = folder(); extract(zip("folder\\こんにちは.txt", "hello"), out); require(read(out, "folder/こんにちは.txt").equals("hello"));
            });
            test("ZIP traversal, absolute and drive paths are rejected", () -> {
                for (String path : new String[]{"../outside", "a/../../outside", "/tmp/outside", "C:\\outside", "\\\\server\\share"})
                    rejects(() -> extract(zip(path, "bad"), folder()));
                require(!new File(root, "outside").exists());
            });
            test("duplicate normalized ZIP names do not overwrite", () -> rejects(() -> extract(zip(new String[]{"a.txt", "./a.txt"}, new byte[][]{bytes("first"), bytes("second")}, 0), folder())));
            test("file and folder conflicts are rejected", () -> rejects(() -> extract(zip(new String[]{"a", "a/b"}, new byte[][]{bytes("first"), bytes("second")}, 0), folder())));
            test("ZIP symbolic links are rejected", () -> rejects(() -> extract(zip(new String[]{"link"}, new byte[][]{bytes("../outside")}, 0120777), folder())));
            test("ZIP device files are rejected", () -> rejects(() -> extract(zip(new String[]{"device"}, new byte[][]{new byte[0]}, 0020600), folder())));
            test("existing files are never replaced", () -> {
                File out = folder(); Files.write(new File(out, "keep.txt").toPath(), bytes("keep"));
                rejects(() -> extract(zip("keep.txt", "replace"), out)); require(read(out, "keep.txt").equals("keep"));
            });
            test("existing symbolic output links are never followed", () -> {
                File out = folder(), outside = folder(); Files.createSymbolicLink(new File(out, "link").toPath(), outside.toPath());
                rejects(() -> extract(zip("link/pwned", "bad"), out)); require(!new File(outside, "pwned").exists());
            });
            test("ZIP missing central directory is rejected", () -> {
                File a = zip("one", "data"); byte[] data = Files.readAllBytes(a.toPath()); Files.write(a.toPath(), Arrays.copyOf(data, data.length - 30)); rejects(() -> extract(a, folder()));
            });
            test("ZIP CRC damage is rejected", () -> {
                File a = zip("unique-name.txt", "data"); byte[] data = Files.readAllBytes(a.toPath());
                for (int i = 0; i < data.length - 20; i++) if (data[i] == 80 && data[i+1] == 75 && data[i+2] == 1 && data[i+3] == 2) { data[i+16] ^= 1; break; }
                Files.write(a.toPath(), data); rejects(() -> extract(a, folder()));
            });
            test("TAR preserves nested file bytes", () -> { File out = folder(); require(extract(tar("./nested/file.txt", bytes("tar bytes"), TarConstants.LF_NORMAL, false), out) == 1); require(read(out, "nested/file.txt").equals("tar bytes")); });
            test("TAR PAX long Unicode path", () -> { String name = String.join("", Collections.nCopies(60, "長")) + ".txt"; File out = folder(); extract(tar("nested/" + name, bytes("PAX"), TarConstants.LF_NORMAL, false), out); require(read(out, "nested/" + name).equals("PAX")); });
            test("TAR.GZ extraction and gzip checksum", () -> {
                File a = tar("a.txt", bytes("gzip"), TarConstants.LF_NORMAL, true), out = folder(); extract(a, out); require(read(out, "a.txt").equals("gzip"));
                byte[] data = Files.readAllBytes(a.toPath()); data[data.length-8] ^= 1; Files.write(a.toPath(), data); rejects(() -> extract(a, folder()));
            });
            test("TAR links and devices are rejected", () -> { for (byte type : new byte[]{TarConstants.LF_SYMLINK, TarConstants.LF_LINK, TarConstants.LF_CHR, TarConstants.LF_FIFO}) rejects(() -> extract(tar("bad", new byte[0], type, false), folder())); });
            test("TAR traversal is rejected", () -> rejects(() -> extract(tar("../outside", bytes("bad"), TarConstants.LF_NORMAL, false), folder())));
            test("truncated TAR data is rejected", () -> { File a = tar("a.bin", new byte[4096], TarConstants.LF_NORMAL, false); try (RandomAccessFile f = new RandomAccessFile(a, "rw")) { f.setLength(1024); } rejects(() -> extract(a, folder())); });
            test("invalid TAR checksum is rejected", () -> { File a = tar("a", bytes("a"), TarConstants.LF_NORMAL, false); byte[] b = Files.readAllBytes(a.toPath()); b[0] ^= 1; Files.write(a.toPath(), b); rejects(() -> extract(a, folder())); });
            for (String name : new String[]{"rar4.rar", "rar5.rar"}) test(name + " file contents", () -> {
                File out = folder(); require(extract(fixture(name), out) == 2); require(read(out, "FILE1.TXT").equals("file1\r\n")); require(read(out, "FILE2.TXT").equals("file2\r\n"));
            });
            for (String name : new String[]{"rar4-solid.rar", "rar5-solid.rar"}) test(name + " solid contents", () -> {
                File out = folder(); require(extract(fixture(name), out) == 9); for (int i = 1; i <= 9; i++) require(read(out, "file" + i + ".txt").equals("file" + i + "\n"));
            });
            test("RAR Unicode names", () -> { File out = folder(); extract(fixture("unicode.rar"), out); require(read(out, "新建文本文档.txt").equals("aaaaaaaaaa")); require(read(out, "ウニコド.txt").equals("このファイルにはUnicodeテキストが含まれています")); });
            test("RAR5 compressed and stored payloads", () -> { File out = folder(); extract(fixture("m0-plain-128k.rar"), out); require(new File(out, "small.bin").length() == 8400); extract(fixture("m3-plain-128k.rar"), out); require(new File(out, "med.bin").length() == 307200); });
            test("encrypted RAR4 and RAR5 return an error", () -> { for (String name : new String[]{"rar4-password-junrar.rar", "rar5-encrypted-junrar.rar"}) rejects(() -> extract(fixture(name), folder())); });
            test("RAR5 links return an error", () -> rejects(() -> extract(fixture("rar5-links.rar"), folder())));
            test("large RAR dictionary is rejected before allocation", () -> rejects(() -> extract(fixture("m3-1gb-claim.rar"), folder())));
            test("expanded size limit stops ZIP and TAR", () -> { for (File a : new File[]{zip("a", "123456"), tar("a", bytes("123456"), TarConstants.LF_NORMAL, false)}) rejects(() -> ArchiveFiles.extract(a, a.getName(), ArchiveFiles.localTarget(folder()), token(), NONE, 5, 10)); });
            test("entry limit stops extraction", () -> { File a = zip(new String[]{"a", "b"}, new byte[][]{bytes("a"), bytes("b")}, 0); rejects(() -> ArchiveFiles.extract(a, a.getName(), ArchiveFiles.localTarget(folder()), token(), NONE, 100, 1)); });
            test("cancellation interrupts real extraction", () -> { File a = zip(new String[]{"large.bin"}, new byte[][]{new byte[300000]}, 0), out = folder(); LocalFiles.Cancellation c = token(); rejects(() -> ArchiveFiles.extract(a, a.getName(), ArchiveFiles.localTarget(out), c, (n,name)-> { if (n > 0) c.cancelled = true; })); require(new File(out, "large.bin").length() < 300000); });
            test("cancellation before extraction creates nothing", () -> { LocalFiles.Cancellation c = token(); c.cancelled = true; File out = folder(); rejects(() -> ArchiveFiles.extract(fixture("rar4.rar"), "rar4.rar", ArchiveFiles.localTarget(out), c, NONE)); require(out.list().length == 0); });
            test("depth and control character boundaries", () -> { rejects(() -> ArchiveFiles.safePath(String.join("/", Collections.nCopies(66, "a")), false)); rejects(() -> ArchiveFiles.safePath("bad\u0000name", false)); require(ArchiveFiles.safePath("./safe//file", false).equals("safe/file")); });
        } finally { LocalFiles.delete(root, token()); }
        System.out.println(passed + " archive tests passed.");
    }
}
