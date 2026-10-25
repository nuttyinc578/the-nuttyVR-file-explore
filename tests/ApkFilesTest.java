package com.nuttyvr.explorer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** APK snapshot integrity and the private-cache URI boundary; no Android stubs or mock installs. */
public class ApkFilesTest {
    static int passed;
    static final long SPACE = 64L*1024*1024;
    static final LocalFiles.Progress NONE=(n,name)->{};
    interface Check{void run()throws Exception;}
    static void test(String name,Check check)throws Exception{check.run();passed++;System.out.println("PASS "+name);}
    static void require(boolean ok)throws Exception{if(!ok)throw new Exception("Assertion failed");}
    static void rejects(Check check)throws Exception{try{check.run();}catch(IOException expected){return;}throw new Exception("Expected rejection");}
    static LocalFiles.Cancellation token(){return new LocalFiles.Cancellation();}
    static InputStream data(){return new ByteArrayInputStream(new byte[]{80,75,3,4,5,6});}
    static void empty(File folder)throws Exception{require(!folder.exists()||Objects.requireNonNull(folder.list()).length==0);}
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("nuttyvr-apk-").toFile();File cache=new File(root,"apk-installs");
        try{
            test("APK recognized by uppercase extension or Android MIME",()->{require(ApkFiles.isApk("APP.APK","application/octet-stream"));require(ApkFiles.isApk("download",ApkFiles.MIME));require(!ApkFiles.isApk("archive.apks","application/zip"));require(!ApkFiles.isApk("archive.xapk","application/zip"));});
            test("snapshot preserves exact bytes in a new private file",()->{File f=ApkFiles.stage(data(),cache,6,SPACE,token(),NONE);require(Arrays.equals(Files.readAllBytes(f.toPath()),new byte[]{80,75,3,4,5,6}));require(f.getName().endsWith(".apk"));require(ApkFiles.resolve(cache,f.getName()).equals(f));Files.delete(f.toPath());});
            test("unknown provider size is supported",()->{File f=ApkFiles.stage(data(),cache,-1,SPACE,token(),NONE);require(f.length()==6);Files.delete(f.toPath());});
            test("snapshots do not overwrite each other",()->{File a=ApkFiles.stage(data(),cache,6,SPACE,token(),NONE),b=ApkFiles.stage(data(),cache,6,SPACE,token(),NONE);require(!a.equals(b));require(a.length()==6);Files.delete(a.toPath());Files.delete(b.toPath());});
            test("insufficient device storage creates no snapshot",()->{rejects(()->ApkFiles.stage(data(),cache,6,ApkFiles.RESERVE+5,token(),NONE));empty(cache);});
            test("unknown size cannot exceed available storage",()->{rejects(()->ApkFiles.stage(data(),cache,-1,ApkFiles.RESERVE+5,token(),NONE));empty(cache);});
            test("empty APK is rejected and removed",()->{rejects(()->ApkFiles.stage(new ByteArrayInputStream(new byte[0]),cache,0,SPACE,token(),NONE));empty(cache);});
            test("changed or truncated source is rejected",()->{rejects(()->ApkFiles.stage(data(),cache,7,SPACE,token(),NONE));empty(cache);});
            test("cancelled snapshot leaves no partial file",()->{LocalFiles.Cancellation c=token();rejects(()->ApkFiles.stage(data(),cache,6,SPACE,c,(n,name)->c.cancelled=true));empty(cache);});
            test("failed reads clean up partial output",()->{InputStream failing=new InputStream(){int n;public int read()throws IOException{if(n++<4)return 80;throw new IOException("disconnected provider");}};rejects(()->ApkFiles.stage(failing,cache,-1,SPACE,token(),NONE));empty(cache);});
            test("failed source close cleans up snapshot",()->{InputStream failing=new ByteArrayInputStream(new byte[]{1}){public void close()throws IOException{throw new IOException("close failed");}};rejects(()->ApkFiles.stage(failing,cache,1,SPACE,token(),NONE));empty(cache);});
            test("source is closed when space is insufficient",()->{final boolean[] closed={false};InputStream stream=new ByteArrayInputStream(new byte[]{1}){public void close(){closed[0]=true;}};rejects(()->ApkFiles.stage(stream,cache,1,0,token(),NONE));require(closed[0]);});
            test("private provider rejects path traversal and other types",()->{for(String name:new String[]{"../outside.apk","nested/file.apk","nested\\file.apk","password","notes.txt","."})rejects(()->ApkFiles.resolve(cache,name));});
            test("private provider rejects symbolic links",()->{File outside=new File(root,"outside.apk");Files.write(outside.toPath(),new byte[]{1});File link=new File(cache,"linked.apk");Files.createSymbolicLink(link.toPath(),outside.toPath());rejects(()->ApkFiles.resolve(cache,link.getName()));Files.delete(link.toPath());require(outside.exists());});
            test("missing cached APK asks for reselection",()->rejects(()->ApkFiles.resolve(cache,"missing.apk")));
        }finally{LocalFiles.delete(root,token());}
        System.out.println(passed+" APK snapshot tests passed.");
    }
}
