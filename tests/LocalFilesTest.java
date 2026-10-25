package com.nuttyvr.explorer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Host-side regression tests for destructive file operations. No Android device is required. */
public class LocalFilesTest {
    static int passed;
    interface Check {void run() throws Exception;}
    static void test(String label,Check check)throws Exception{check.run();passed++;System.out.println("PASS "+label);}
    static void require(boolean ok)throws Exception{if(!ok)throw new Exception("Assertion failed");}
    static void rejects(Check action)throws Exception{try{action.run();}catch(IOException expected){return;}throw new Exception("Expected rejection");}
    static void put(File file,String text)throws Exception{Files.writeString(file.toPath(),text);}
    static String read(File file)throws Exception{return Files.readString(file.toPath());}
    static LocalFiles.Cancellation token(){return new LocalFiles.Cancellation();}
    static final LocalFiles.Progress NONE=(n,name)->{};
    public static void main(String[] args)throws Exception{
        File base=Files.createTempDirectory("nuttyvr-tests-").toFile();
        try{
            File from=new File(base,"from"),to=new File(base,"to");require(from.mkdir());require(to.mkdir());
            File source=new File(from,"notes.txt");put(source,"NuttyVR 🥜\nExact bytes.\n");
            test("copy preserves bytes and the source",()->{File result=LocalFiles.copy(source,to,token(),NONE);require(read(result).equals(read(source)));require(source.exists());});
            test("existing files are never overwritten",()->{File result=LocalFiles.copy(source,to,token(),NONE);require(result.getName().equals("notes (1).txt"));require(read(new File(to,"notes.txt")).equals(read(source)));});
            test("same-folder copy receives a new name",()->{File result=LocalFiles.copy(source,from,token(),NONE);require(result.getName().equals("notes (1).txt"));});
            File folder=new File(from,"Folder");require(folder.mkdir());File nested=new File(folder,"nested");require(nested.mkdir());put(new File(nested,"hidden.txt"),"nested");put(new File(folder,".hidden"),"secret");
            test("recursive copy includes nested and hidden files",()->{File result=LocalFiles.copy(folder,to,token(),NONE);require(read(new File(result,"nested/hidden.txt")).equals("nested"));require(read(new File(result,".hidden")).equals("secret"));});
            test("copy into self is rejected",()->rejects(()->LocalFiles.copy(folder,folder,token(),NONE)));
            test("copy into descendant is rejected",()->rejects(()->LocalFiles.copy(folder,nested,token(),NONE)));
            test("move into descendant keeps original",()->{rejects(()->LocalFiles.move(folder,nested,token(),NONE));require(new File(folder,"nested/hidden.txt").exists());});
            test("cancel before transfer keeps source",()->{LocalFiles.Cancellation c=token();c.cancelled=true;rejects(()->LocalFiles.copy(source,to,c,NONE));require(source.exists());});
            test("mid-copy cancellation removes staging output",()->{LocalFiles.Cancellation c=token();Set<String> before=new HashSet<>(Arrays.asList(to.list()));rejects(()->LocalFiles.copy(folder,to,c,(n,name)->c.cancelled=true));require(before.equals(new HashSet<>(Arrays.asList(to.list()))));require(source.exists());});
            test("source size change rejects copy and cleans staging",()->{File changing=new File(from,"changing.bin");put(changing,"old");Set<String> before=new HashSet<>(Arrays.asList(to.list()));final boolean[] changed={false};rejects(()->LocalFiles.copy(changing,to,token(),(n,name)->{if(!changed[0]){changed[0]=true;try{put(changing,"x");}catch(Exception e){throw new RuntimeException(e);}}}));require(before.equals(new HashSet<>(Arrays.asList(to.list()))));});
            test("move keeps colliding destination",()->{File moving=new File(from,"notes.txt");File result=LocalFiles.move(moving,to,token(),NONE);require(!moving.exists());require(result.getName().equals("notes (2).txt"));require(new File(to,"notes.txt").exists());});
            test("rename conflict preserves both items",()->{File a=new File(from,"a"),b=new File(from,"b");put(a,"a");put(b,"b");rejects(()->LocalFiles.rename(a,"b"));require(read(a).equals("a")&&read(b).equals("b"));});
            test("rename succeeds with Unicode and spaces",()->{File a=new File(from,"a");File result=LocalFiles.rename(a,"my 文件.txt");require(read(result).equals("a"));});
            test("unsafe names are rejected",()->{for(String s:new String[]{""," ",".","..","../escape","bad/name","bad\\name","bad\0name","line\nname","x".repeat(241)})rejects(()->LocalFiles.validName(s));});
            test("canonical directory boundary is enforced",()->{require(LocalFiles.inside(nested,folder));require(!LocalFiles.inside(new File(from,"Folder2"),folder));require(!LocalFiles.inside(new File(folder,"../escape"),folder));});
            test("symbolic link copying is rejected",()->{File link=new File(from,"link");Files.createSymbolicLink(link.toPath(),folder.toPath());rejects(()->LocalFiles.copy(link,to,token(),NONE));});
            test("deleting a symlink preserves its target",()->{File link=new File(from,"link");LocalFiles.delete(link,token());require(!Files.isSymbolicLink(link.toPath()));require(new File(folder,".hidden").exists());});
            test("cancelled delete keeps files",()->{LocalFiles.Cancellation c=token();c.cancelled=true;rejects(()->LocalFiles.delete(folder,c));require(folder.exists());});
            test("recursive deletion removes full subtree",()->{LocalFiles.delete(folder,token());require(!folder.exists());});
            test("rename rejects path traversal",()->rejects(()->LocalFiles.rename(new File(from,"b"),"../escape")));
            test("missing source does not produce output",()->rejects(()->LocalFiles.copy(new File(from,"missing"),to,token(),NONE)));
            test("transfer requires a real folder",()->rejects(()->LocalFiles.copy(new File(from,"b"),new File(from,"missing"),token(),NONE)));
        }finally{LocalFiles.delete(base,token());}
        System.out.println(passed+" file-operation tests passed.");
    }
}
