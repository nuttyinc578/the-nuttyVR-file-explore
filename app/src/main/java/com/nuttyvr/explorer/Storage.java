package com.nuttyvr.explorer;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

final class Storage {
    static final String DIRECTORY = DocumentsContract.Document.MIME_TYPE_DIR;
    static final class Item {
        final File file;
        final Uri uri;
        final String name, mime;
        final boolean directory;
        final long size, modified;
        Item(File f) {
            file = f; uri = null; name = f.getName().isEmpty() ? f.getPath() : f.getName();
            directory = f.isDirectory(); size = directory ? -1 : f.length(); modified = f.lastModified();
            mime = directory ? DIRECTORY : mime(name);
        }
        Item(Uri u, String n, String m, long s, long time) {
            file = null; uri = u; name = n; mime = m; directory = DIRECTORY.equals(m); size = s; modified = time;
        }
        String key() { return file != null ? file.getAbsolutePath() : uri.toString(); }
    }
    private final ContentResolver resolver;
    Storage(Context context) { resolver = context.getContentResolver(); }
    static String mime(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.equals("apk")) return "application/vnd.android.package-archive";
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type == null ? "application/octet-stream" : type;
    }

    Item tree(Uri tree) throws IOException {
        return document(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)));
    }
    Item document(Uri uri) throws IOException {
        try (Cursor c = resolver.query(uri, projection(), null, null, null)) {
            if (c == null || !c.moveToFirst()) throw new IOException("This folder is no longer available. Connect it again.");
            return read(c, uri);
        } catch (RuntimeException e) { throw new IOException("Folder permission is unavailable. Connect the folder again.", e); }
    }
    private String[] projection() { return new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED}; }
    private Item read(Cursor c, Uri uri) {
        return new Item(uri, c.getString(1), c.getString(2), c.isNull(3) ? -1 : c.getLong(3), c.isNull(4) ? 0 : c.getLong(4));
    }
    List<Item> list(Item folder) throws IOException {
        List<Item> items = new ArrayList<>();
        if (folder.file != null) {
            File[] files = folder.file.listFiles();
            if (files == null) throw new IOException("Cannot read this folder. Enable storage access or choose another folder. Android/data and Android/obb may be restricted by the headset.");
            for (File f : files) if (!Files.isSymbolicLink(f.toPath())) items.add(new Item(f));
        } else {
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, DocumentsContract.getDocumentId(folder.uri));
                try (Cursor c = resolver.query(children, projection(), null, null, null)) {
                    if (c == null) throw new IOException("The storage provider did not return this folder.");
                    while (c.moveToNext()) items.add(read(c, DocumentsContract.buildDocumentUriUsingTree(folder.uri, c.getString(0))));
                }
            } catch (RuntimeException e) { throw new IOException("Cannot read this folder. Reconnect it using Choose folder.", e); }
        }
        return items;
    }
    Item mkdir(Item parent, String name) throws IOException { return create(parent, name, DIRECTORY); }
    Item create(Item parent, String name, String type) throws IOException {
        LocalFiles.validName(name);
        if (parent.file != null) {
            File f = new File(parent.file, name);
            if (f.exists()) throw new IOException("That name already exists.");
            boolean ok = DIRECTORY.equals(type) ? f.mkdir() : f.createNewFile();
            if (!ok) throw new IOException("Cannot create " + name + ". Check storage access and free space.");
            return new Item(f);
        }
        try {
            Uri uri = DocumentsContract.createDocument(resolver, parent.uri, type, name);
            if (uri == null) throw new IOException("The storage provider could not create " + name);
            return document(uri);
        } catch (RuntimeException e) { throw new IOException("This storage provider does not allow creating files here.", e); }
    }
    Item rename(Item parent, Item item, String name) throws IOException {
        LocalFiles.validName(name);
        if(item.name.equals(name))return item;
        for(Item sibling:list(parent))if(sibling.name.equals(name))throw new IOException("A file with that name already exists.");
        if (item.file != null) return new Item(LocalFiles.rename(item.file, name));
        try {
            Uri uri = DocumentsContract.renameDocument(resolver, item.uri, name);
            if (uri == null) throw new IOException("The storage provider could not rename this item.");
            return document(uri);
        } catch (RuntimeException e) { throw new IOException("Rename is unavailable in this folder.", e); }
    }
    void delete(Item item, LocalFiles.Cancellation cancel) throws IOException {
        cancel.check();
        if (item.file != null) { LocalFiles.delete(item.file, cancel); return; }
        try {
            if (!DocumentsContract.deleteDocument(resolver, item.uri)) throw new IOException("The item could not be deleted.");
        } catch (RuntimeException e) { throw new IOException("Delete is unavailable for " + item.name, e); }
    }
    InputStream input(Item item) throws IOException {
        InputStream stream = item.file != null ? new FileInputStream(item.file) : resolver.openInputStream(item.uri);
        if (stream == null) throw new IOException("Cannot open " + item.name);
        return stream;
    }
    private OutputStream output(Item item) throws IOException {
        OutputStream stream = item.file != null ? new FileOutputStream(item.file) : resolver.openOutputStream(item.uri, "wt");
        if (stream == null) throw new IOException("Cannot write " + item.name);
        return stream;
    }

    Item extract(Item source, Item parent, File cache, LocalFiles.Cancellation cancel, LocalFiles.Progress progress) throws IOException {
        File snapshot = null;
        Item stage = null;
        try {
            File archive = source.file;
            if (archive == null) {
                // Random access is required by ZIP and RAR. Provider files get a bounded private snapshot.
                final long limit = 2L << 30;
                if (source.size > limit) throw new IOException("Archives from a chosen folder are limited to 2 GB. Use shared storage for larger archives.");
                snapshot = File.createTempFile("nuttyvr-archive-", ".part", cache);
                long copied = 0;
                try (InputStream in = input(source); OutputStream out = new FileOutputStream(snapshot)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        cancel.check();
                        if (n > limit - copied) throw new IOException("Archives from a chosen folder are limited to 2 GB.");
                        out.write(buffer, 0, n); copied += n; progress.update(n, "Preparing " + source.name);
                    }
                }
                if (source.size >= 0 && copied != source.size) throw new IOException("The archive changed while being prepared. Try again.");
                archive = snapshot;
            }
            cancel.check();
            stage = mkdir(parent, ".nuttyvr-extract-" + UUID.randomUUID());
            final Item outputRoot = stage;
            ArchiveFiles.Target target;
            if (stage.file != null) target = ArchiveFiles.localTarget(stage.file);
            else target = new ArchiveFiles.Target() {
                private final Map<String, Item> folders = new HashMap<>();
                { folders.put("", outputRoot); }
                private Item parent(String path) throws IOException {
                    int slash = path.lastIndexOf('/');
                    Item folder = folders.get(slash < 0 ? "" : path.substring(0, slash));
                    if (folder == null) throw new IOException("The archive folder is unavailable.");
                    return folder;
                }
                private String name(String path) { return path.substring(path.lastIndexOf('/') + 1); }
                public void directory(String path) throws IOException { folders.put(path, mkdir(parent(path), name(path))); }
                public OutputStream file(String path) throws IOException { return output(create(parent(path), name(path), mime(name(path)))); }
            };
            ArchiveFiles.extract(archive, source.name, target, cancel, progress);
            cancel.check();
            Set<String> names = new HashSet<>();
            for (Item sibling : list(parent)) names.add(sibling.name);
            String stem = ArchiveFiles.folderName(source.name), name = stem;
            for (int n = 1; names.contains(name); n++) {
                if (n > 10000) throw new IOException("Too many folders with this name.");
                name = stem + " (" + n + ")";
            }
            Item result = rename(parent, stage, name);
            stage = null;
            return result;
        } catch (IOException | RuntimeException failure) {
            if (stage != null) {
                try { delete(stage, new LocalFiles.Cancellation()); }
                catch (IOException cleanup) {
                    throw new IOException("Extraction stopped. A partial folder remains: " + stage.name + ". " + failure.getMessage(), failure);
                }
            }
            throw failure;
        } finally {
            if (snapshot != null) snapshot.delete();
        }
    }

    Item transfer(Item source, Item dest, boolean move, LocalFiles.Cancellation cancel, LocalFiles.Progress progress) throws IOException {
        if (source.file != null && dest.file != null) {
            return new Item(move ? LocalFiles.move(source.file, dest.file, cancel, progress)
                    : LocalFiles.copy(source.file, dest.file, cancel, progress));
        }
        // Check the complete source tree BEFORE creating anything, including SAF/local aliases.
        rejectDescendant(source, identity(dest), cancel, new HashSet<>(), 0);
        Item copied = copyRecursive(source, dest, cancel, progress, 0);
        cancel.check();
        if (move) {
            try { delete(source, new LocalFiles.Cancellation()); }
            catch (IOException e) { throw new IOException("Copy completed, but the source could not be fully removed. Check both folders before retrying.", e); }
        }
        return copied;
    }
    private String identity(Item item) throws IOException {
        if (item.file != null) return item.file.getCanonicalPath();
        String id = DocumentsContract.getDocumentId(item.uri);
        if ("com.android.externalstorage.documents".equals(item.uri.getAuthority()) && id.startsWith("primary:")) {
            return new File(Environment.getExternalStorageDirectory(), id.substring(8)).getCanonicalPath();
        }
        return item.uri.getAuthority() + ":" + id;
    }
    private void rejectDescendant(Item source, String dest, LocalFiles.Cancellation cancel, Set<String> visited, int depth) throws IOException {
        cancel.check();
        if (depth > 128 || visited.size() > 100000) throw new IOException("This folder is too large or too deeply nested for a provider transfer.");
        String id = identity(source);
        if (id.equals(dest)) throw new IOException("A folder cannot be placed inside itself.");
        if (!visited.add(id)) throw new IOException("A circular folder link was found.");
        if (source.directory) for (Item child : list(source)) rejectDescendant(child, dest, cancel, visited, depth + 1);
    }
    private Item copyRecursive(Item source, Item dest, LocalFiles.Cancellation cancel, LocalFiles.Progress progress, int depth) throws IOException {
        cancel.check();
        if (depth > 128) throw new IOException("Too many nested folders.");
        Set<String> names = new HashSet<>();
        for (Item existing : list(dest)) names.add(existing.name);
        String name = source.name;
        int dot = source.directory ? -1 : name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; names.contains(name); n++) {
            if (n > 10000) throw new IOException("Too many files with this name.");
            name = stem + " (" + n + ")" + ext;
        }
        Item result = create(dest, name, source.mime);
        try {
            if (source.directory) {
                for (Item child : list(source)) copyRecursive(child, result, cancel, progress, depth + 1);
            } else {
                long copied = 0;
                try (InputStream in = input(source); OutputStream out = output(result)) {
                    byte[] buffer = new byte[128 * 1024]; int n;
                    while ((n = in.read(buffer)) != -1) { cancel.check(); out.write(buffer, 0, n); copied += n; progress.update(n, source.name); }
                    out.flush();
                }
                if (source.size >= 0 && copied != source.size) throw new IOException("The source changed during transfer. Try again.");
            }
            cancel.check();
            return result;
        } catch (IOException | RuntimeException e) {
            try { delete(result, new LocalFiles.Cancellation()); } catch (IOException ignored) { /* Provider may refuse cleanup. */ }
            throw e;
        }
    }
}
