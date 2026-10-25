package com.nuttyvr.explorer;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.format.Formatter;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native, resizable panel UI: the headset provides the tracked pointer and spatial window. */
public final class MainActivity extends Activity {
    private static final int BG=0xff10151e, PANEL=0xff151d28, CARD=0xff1b2533, BORDER=0xff2b3849;
    private static final int TEXT=0xffedf4fc, MUTED=0xff9baec4, MINT=0xff9af2c5, INK=0xff142d25, RED=0xffffa8b0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService listing=Executors.newSingleThreadExecutor(), operations=Executors.newSingleThreadExecutor();
    private final ArrayList<Storage.Item> trail=new ArrayList<>(), visible=new ArrayList<>(), clipboard=new ArrayList<>();
    private final LinkedHashMap<String,Storage.Item> selected=new LinkedHashMap<>();
    private List<Storage.Item> all=new ArrayList<>();
    private Storage storage;
    private android.content.SharedPreferences prefs;
    private LinearLayout shell, body, side, content, toolbar, accessBanner, clipboardBar;
    private TextView title, subtitle, count, emptyTitle, emptyNote;
    private EditText search;
    private GridView grid;
    private ProgressBar loading;
    private View empty;
    private FileAdapter adapter;
    private String query="", error="", rootLabel="Workspace";
    private boolean listMode, showHidden, busy, destroyed, clipboardMove, hadStorage;
    private int sortMode, loadVersion, filterVersion;
    private LocalFiles.Cancellation activeCancel;
    private Dialog activeDialog;
    private final Runnable searchAction=this::filter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        storage=new Storage(this); prefs=getSharedPreferences("nuttyvr",MODE_PRIVATE);
        listMode=prefs.getBoolean("list",false); showHidden=prefs.getBoolean("hidden",false); sortMode=prefs.getInt("sort",0);
        hadStorage=hasStorage();
        Storage.Item start=new Storage.Item(hadStorage ? Environment.getExternalStorageDirectory() : workspace());
        rootLabel=hadStorage ? "Headset storage" : "Workspace";
        if(state!=null){
            String path=state.getString("path");
            if(path!=null){File f=new File(path);try{if(f.isDirectory()&&(LocalFiles.inside(f,workspace())||hadStorage&&LocalFiles.inside(f,Environment.getExternalStorageDirectory()))){
                start=new Storage.Item(f); rootLabel=f.getName();
            }}catch(IOException ignored){}}
        }
        trail.add(start); buildUi(); refresh();
    }
    private File workspace(){File f=getExternalFilesDir(null);return f!=null?f:getFilesDir();}
    private boolean hasStorage(){return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    private Storage.Item current(){return trail.get(trail.size()-1);}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private boolean wide(){return getResources().getConfiguration().screenWidthDp>=820;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private android.graphics.drawable.Drawable interactive(int base,int hover,int radius,int stroke){
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},shape(hover,radius,MINT));
        states.addState(new int[]{android.R.attr.state_focused},shape(hover,radius,MINT));
        states.addState(new int[]{android.R.attr.state_hovered},shape(hover,radius,BORDER));
        states.addState(new int[]{},shape(base,radius,stroke));return states;
    }
    private LinearLayout button(String icon,String label,boolean primary,Runnable action){
        LinearLayout b=row();pad(b,14,0,14,0);b.setMinimumHeight(dp(52));b.setGravity(Gravity.CENTER);
        b.setBackground(interactive(primary?MINT:CARD,primary?0xffc0ffdd:0xff2a3c4d,13,primary?0:BORDER));
        if(icon!=null){Glyph g=new Glyph(this,icon,primary?INK:MUTED);b.addView(g,new LinearLayout.LayoutParams(dp(22),dp(22)));}
        if(label!=null){TextView t=text(label,14,primary?INK:TEXT);t.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=icon!=null?dp(8):0;b.addView(t,lp);}
        b.setFocusable(true);b.setClickable(true);b.setContentDescription(label!=null?label:icon);b.setOnClickListener(v->action.run());
        return b;
    }
    private View iconButton(String icon,String description,Runnable action){LinearLayout b=button(icon,null,false,action);b.setContentDescription(description);b.setTooltipText(description);b.setMinimumWidth(dp(52));return b;}
    private void addGap(LinearLayout parent,int width){parent.addView(new View(this),new LinearLayout.LayoutParams(dp(width),1));}
    private void addAction(LinearLayout parent,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(52));lp.rightMargin=dp(8);parent.addView(child,lp);}

    private void buildUi(){
        shell=column();shell.setBackgroundColor(BG);setContentView(shell);
        LinearLayout header=row();pad(header,wide()?24:16,14,wide()?24:16,14);header.setBackgroundColor(PANEL);
        Glyph logo=new Glyph(this,"logo",MINT);logo.setBackground(shape(0xff24382f,16,0));header.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(50)));addGap(header,14);
        LinearLayout brand=column();TextView brandName=text("NuttyVR",23,TEXT);brandName.setTypeface(null,Typeface.BOLD);brand.addView(brandName);
        TextView brandSub=text("FILE EXPLORER",10,MUTED);brandSub.setLetterSpacing(.16f);brand.addView(brandSub);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(wide()){TextView local=text("●  ON DEVICE  /  OFFLINE",10,MINT);local.setLetterSpacing(.08f);header.addView(local);addGap(header,20);}
        header.addView(iconButton("info","Help and storage access",this::help),new LinearLayout.LayoutParams(dp(52),dp(52)));
        shell.addView(header);
        body=row();body.setGravity(Gravity.TOP);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        side=column();side.setBackgroundColor(PANEL);pad(side,14,16,14,16);
        if(wide()){ScrollView sideScroll=new ScrollView(this);sideScroll.setFillViewport(true);sideScroll.setVerticalScrollBarEnabled(false);sideScroll.addView(side,new ScrollView.LayoutParams(-1,-2));body.addView(sideScroll,new LinearLayout.LayoutParams(dp(216),-1));}
        content=column();pad(content,wide()?24:16,18,wide()?24:16,12);body.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout heading=row();
        LinearLayout headingText=column();title=text("Your files.",28,TEXT);title.setTypeface(null,Typeface.BOLD);headingText.addView(title);
        subtitle=text("Everything in its place. A little more space.",12,MUTED);pad(subtitle,0,4,0,0);headingText.addView(subtitle);
        heading.addView(headingText,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(button("plus",wide()?"New folder":null,true,this::newFolder),new LinearLayout.LayoutParams(-2,dp(52)));heading.getChildAt(1).setContentDescription("New folder");
        content.addView(heading);if(getResources().getConfiguration().screenHeightDp<560)heading.setVisibility(View.GONE);
        LinearLayout pathRow=row();pad(pathRow,0,16,0,12);
        addAction(pathRow,iconButton(wide()?"up":"list",wide()?"Go up one folder":"Locations",()->{if(wide())up();else locations();}));
        if(!wide())addAction(pathRow,iconButton("up","Go up one folder",this::up));
        HorizontalScrollView crumbs=new HorizontalScrollView(this);crumbs.setHorizontalScrollBarEnabled(false);
        LinearLayout breadcrumb=row();
        for(int i=0;i<trail.size();i++){
            final int index=i;
            if(i>0){Glyph arrow=new Glyph(this,"arrow",MUTED);breadcrumb.addView(arrow,new LinearLayout.LayoutParams(dp(22),dp(22)));}
            TextView crumb=text(i==0?rootLabel:trail.get(i).name,13,i==trail.size()-1?TEXT:MUTED);pad(crumb,10,0,10,0);crumb.setGravity(Gravity.CENTER);crumb.setMinHeight(dp(52));crumb.setBackground(interactive(BG,CARD,10,0));crumb.setFocusable(true);crumb.setOnClickListener(v->{if(!busy){while(trail.size()>index+1)trail.remove(trail.size()-1);query="";selected.clear();buildUi();refresh();}});breadcrumb.addView(crumb);
        }
        crumbs.addView(breadcrumb);pathRow.addView(crumbs,new LinearLayout.LayoutParams(0,dp(52),1));
        addGap(pathRow,8);pathRow.addView(iconButton("refresh","Refresh files",()->{if(!busy)refresh();}),new LinearLayout.LayoutParams(dp(52),dp(52)));content.addView(pathRow);
        accessBanner=column();content.addView(accessBanner);renderAccess();
        LinearLayout searchRow=row();searchRow.setBackground(shape(PANEL,13,BORDER));pad(searchRow,14,0,6,0);
        Glyph magnify=new Glyph(this,"search",MUTED);searchRow.addView(magnify,new LinearLayout.LayoutParams(dp(22),dp(22)));addGap(searchRow,10);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setTextSize(15);search.setHint("Search this folder…");search.setBackgroundColor(Color.TRANSPARENT);search.setText(query);search.setSelectAllOnFocus(false);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(54),1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int n){query=s.toString();handler.removeCallbacks(searchAction);handler.postDelayed(searchAction,150);}public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v,action,event)->{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();filter();return true;});
        searchRow.addView(iconButton("close","Clear search",()->search.setText("")),new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(searchRow);
        HorizontalScrollView toolScroll=new HorizontalScrollView(this);toolScroll.setHorizontalScrollBarEnabled(false);toolbar=row();pad(toolbar,0,12,0,12);toolScroll.addView(toolbar);content.addView(toolScroll);renderToolbar();
        FrameLayout browser=new FrameLayout(this);content.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        grid=new GridView(this);grid.setNumColumns(listMode?1:GridView.AUTO_FIT);grid.setColumnWidth(dp(174));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setClipToPadding(false);grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));grid.setFastScrollEnabled(false);grid.setScrollingCacheEnabled(false);grid.setSmoothScrollbarEnabled(true);
        grid.setOnItemClickListener((p,v,pos,id)->activate(visible.get(pos)));grid.setOnItemLongClickListener((p,v,pos,id)->{toggle(visible.get(pos));return true;});
        adapter=new FileAdapter();grid.setAdapter(adapter);browser.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout emptyColumn=column();emptyColumn.setGravity(Gravity.CENTER);pad(emptyColumn,24,28,24,28);
        Glyph emptyGlyph=new Glyph(this,"folder",MINT);emptyColumn.addView(emptyGlyph,new LinearLayout.LayoutParams(dp(64),dp(64)));emptyTitle=text("A little room to grow.",21,TEXT);pad(emptyTitle,0,18,0,8);emptyTitle.setGravity(Gravity.CENTER);emptyColumn.addView(emptyTitle);
        emptyNote=text("Create a folder or paste files here to get started.",14,MUTED);emptyNote.setGravity(Gravity.CENTER);emptyColumn.addView(emptyNote);empty=emptyColumn;browser.addView(empty,new FrameLayout.LayoutParams(-1,-1));empty.setVisibility(View.GONE);
        loading=new ProgressBar(this);FrameLayout.LayoutParams loadParams=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER);browser.addView(loading,loadParams);loading.setVisibility(View.GONE);
        clipboardBar=row();pad(clipboardBar,12,8,8,8);clipboardBar.setBackground(shape(0xff22362f,13,0));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);content.addView(clipboardBar,cp);renderClipboard();
        count=text("Reading folder…",11,MUTED);pad(count,2,12,2,0);content.addView(count);
        renderSidebar(side,null);applyVisible();shell.setFocusableInTouchMode(true);shell.requestFocus();
    }

    private void renderAccess(){
        accessBanner.removeAllViews();
        if(hasStorage()){accessBanner.setVisibility(View.GONE);return;}
        accessBanner.setVisibility(View.VISIBLE);LinearLayout card=row();pad(card,14,10,10,10);card.setBackground(shape(0xff26342f,13,0));
        LinearLayout words=column();TextView a=text("Connect your storage",14,MINT);a.setTypeface(null,Typeface.BOLD);words.addView(a);words.addView(text("Choose a folder, or allow access to shared storage.",11,TEXT));card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        addGap(card,8);card.addView(button(null,"Connect",false,this::accessOptions),new LinearLayout.LayoutParams(-2,dp(52)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);accessBanner.addView(card,lp);
    }
    private void renderSidebar(LinearLayout target,Dialog dialog){
        target.removeAllViews();TextView section=text("PLACES",10,MUTED);section.setLetterSpacing(.14f);pad(section,12,4,0,12);target.addView(section);
        nav(target,"storage","Headset storage",()->goShared("", "Headset storage"),dialog);
        nav(target,"down","Downloads",()->goShared("Download","Downloads"),dialog);
        nav(target,"video","Videos",()->goShared("Movies","Videos"),dialog);
        nav(target,"image","Pictures",()->goShared("Pictures","Pictures"),dialog);
        nav(target,"music","Music",()->goShared("Music","Music"),dialog);
        nav(target,"file","Documents",()->goShared("Documents","Documents"),dialog);
        nav(target,"folder","Workspace",()->goRoot(new Storage.Item(workspace()),"Workspace"),dialog);
        String uri=prefs.getString("tree",null);
        if(uri!=null){
            TextView connected=text("CONNECTED",10,MUTED);connected.setLetterSpacing(.12f);pad(connected,12,20,0,10);target.addView(connected);
            nav(target,"folder",prefs.getString("treeName","Chosen folder"),()->openSavedTree(uri),dialog);
        }
        nav(target,"plus","Choose folder",this::chooseFolder,dialog);
        if(dialog==null){
            target.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
            try {StatFs stats=new StatFs(workspace().getPath());long total=stats.getTotalBytes(),free=stats.getAvailableBytes();
                TextView usage=text(Formatter.formatFileSize(this,free)+" available",12,TEXT);pad(usage,10,16,0,8);target.addView(usage);
                ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress((int)(100*(total-free)/Math.max(1,total)));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));target.addView(bar,new LinearLayout.LayoutParams(-1,dp(4)));
                TextView totalLabel=text(Formatter.formatFileSize(this,total)+" device storage",10,MUTED);pad(totalLabel,10,8,0,4);target.addView(totalLabel);
            }catch(IllegalArgumentException ignored){}
            TextView v=text("NUTTYVR  /  1.2",10,MUTED);pad(v,10,16,0,0);v.setLetterSpacing(.1f);target.addView(v);
        }
    }
    private void nav(LinearLayout parent,String icon,String label,Runnable action,Dialog dialog){
        LinearLayout n=row();pad(n,12,0,12,0);n.setMinimumHeight(dp(50));boolean active=rootLabel.equals(label);
        n.setBackground(interactive(active?0xff263c35:PANEL,0xff2a3b46,11,0));Glyph g=new Glyph(this,icon,active?MINT:MUTED);n.addView(g,new LinearLayout.LayoutParams(dp(22),dp(22)));addGap(n,12);
        TextView name=text(label,13,active?MINT:TEXT);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);n.addView(name,new LinearLayout.LayoutParams(0,-2,1));n.setFocusable(true);n.setContentDescription(label);n.setOnClickListener(v->{if(!busy){if(dialog!=null)dialog.dismiss();action.run();}});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.bottomMargin=dp(4);parent.addView(n,lp);
    }
    private void locations(){LinearLayout places=column();pad(places,16,10,16,18);AlertDialog d=new AlertDialog.Builder(this).setTitle("Locations").setView(places).setNegativeButton("Close",null).create();renderSidebar(places,d);show(d);}
    private void goShared(String path,String label){
        if(!hasStorage()){accessOptions();return;}
        File folder=new File(Environment.getExternalStorageDirectory(),path);
        if(!folder.isDirectory()){message("Folder unavailable",label+" does not exist yet. Create it in Headset storage.");return;}
        goRoot(new Storage.Item(folder),label);
    }
    private void goRoot(Storage.Item item,String label){if(busy)return;rootLabel=label;trail.clear();trail.add(item);selected.clear();query="";all=new ArrayList<>();visible.clear();error="";buildUi();refresh();}
    private void openSavedTree(String uri){
        listing.execute(()->{try{Storage.Item root=storage.tree(Uri.parse(uri));handler.post(()->{if(!destroyed)goRoot(root,root.name);});}catch(Exception e){handler.post(()->message("Reconnect this folder",readable(e)));}});
    }
    private void enter(Storage.Item folder){if(busy)return;trail.add(folder);selected.clear();query="";all=new ArrayList<>();visible.clear();error="";buildUi();refresh();}
    private void up(){if(busy)return;if(!selected.isEmpty()){selected.clear();renderToolbar();adapter.notifyDataSetChanged();return;}if(trail.size()>1){trail.remove(trail.size()-1);selected.clear();query="";all=new ArrayList<>();visible.clear();buildUi();refresh();}else toast("You’re at the top of this location.");}
    @Override public void onBackPressed(){if(!selected.isEmpty()){selected.clear();renderToolbar();adapter.notifyDataSetChanged();}else if(!query.isEmpty())search.setText("");else if(trail.size()>1)up();else super.onBackPressed();}
    private void refresh(){
        final int version=++loadVersion;final Storage.Item folder=current();selected.clear();renderToolbar();loading.setVisibility(View.VISIBLE);empty.setVisibility(View.GONE);error="";
        listing.execute(()->{
            List<Storage.Item> found=new ArrayList<>();String failure="";
            try{found=storage.list(folder);}catch(Exception e){failure=readable(e);}
            final List<Storage.Item> result=found;final String problem=failure;
            handler.post(()->{if(destroyed||version!=loadVersion)return;all=result;error=problem;filter();});
        });
    }
    private void filter(){
        final int version=++filterVersion;final int folderVersion=loadVersion;final List<Storage.Item> source=new ArrayList<>(all);
        final String term=query.toLowerCase(Locale.ROOT);final boolean hidden=showHidden;final int mode=sortMode;
        listing.execute(()->{
            ArrayList<Storage.Item> result=new ArrayList<>();
            for(Storage.Item item:source)if((hidden||!item.name.startsWith("."))&&item.name.toLowerCase(Locale.ROOT).contains(term))result.add(item);
            result.sort((a,b)->{if(a.directory!=b.directory)return a.directory?-1:1;int c;
                if(mode==1)c=Long.compare(b.modified,a.modified);else if(mode==2)c=Long.compare(b.size,a.size);else if(mode==3)c=a.mime.compareToIgnoreCase(b.mime);else c=0;
                return c!=0?c:a.name.compareToIgnoreCase(b.name);});
            handler.post(()->{if(destroyed||version!=filterVersion||folderVersion!=loadVersion)return;visible.clear();visible.addAll(result);loading.setVisibility(View.GONE);applyVisible();});
        });
    }
    private void applyVisible(){
        if(adapter==null)return;adapter.notifyDataSetChanged();boolean none=visible.isEmpty();empty.setVisibility(none?View.VISIBLE:View.GONE);
        if(!error.isEmpty()){emptyTitle.setText(R.string.access_empty_title);emptyNote.setText(error);}
        else if(!query.isEmpty()){emptyTitle.setText(none?getString(R.string.search_empty_title):"");emptyNote.setText(R.string.search_empty_note);}
        else{emptyTitle.setText(R.string.folder_empty_title);emptyNote.setText(R.string.folder_empty_note);}
        int folders=0;for(Storage.Item item:visible)if(item.directory)folders++;
        if(count!=null)count.setText(getString(R.string.items_summary,visible.size(),folders,visible.size()-folders,showHidden?"  ·  Hidden files shown":"",listMode?"List view":"Grid view"));
    }

    private void renderToolbar(){
        if(toolbar==null)return;toolbar.removeAllViews();
        if(selected.isEmpty()){
            addAction(toolbar,button("check","Select",false,()->{if(!busy){if(visible.isEmpty())toast("This folder has no files to select.");else{toggle(visible.get(0));}}}));
            addAction(toolbar,button("sort",new String[]{"Name","Newest","Largest","Type"}[sortMode],false,this::sortOptions));
            addAction(toolbar,iconButton(listMode?"grid":"list",listMode?"Switch to grid view":"Switch to list view",()->{listMode=!listMode;prefs.edit().putBoolean("list",listMode).apply();grid.setNumColumns(listMode?1:GridView.AUTO_FIT);adapter.notifyDataSetChanged();renderToolbar();applyVisible();}));
            addAction(toolbar,iconButton("more","Folder options",this::folderOptions));
        }else{
            addAction(toolbar,button("close",selected.size()+" selected",false,()->{selected.clear();adapter.notifyDataSetChanged();renderToolbar();}));
            addAction(toolbar,button("copy","Copy",false,()->setClipboard(false)));
            addAction(toolbar,button("cut","Move",false,()->setClipboard(true)));
            if(selected.size()==1){
                Storage.Item item=selected.values().iterator().next();
                if(!item.directory&&ArchiveFiles.supported(item.name))addAction(toolbar,button("zip","Extract",false,()->extract(item)));
                addAction(toolbar,button(null,"Rename",false,()->rename(item)));
            }
            addAction(toolbar,button("trash","Delete",false,()->delete(new ArrayList<>(selected.values()))));
            addAction(toolbar,button("check","Select all",false,this::selectAll));
        }
        toolbar.setAlpha(busy?.5f:1f);
    }
    private void sortOptions(){new AlertDialog.Builder(this).setTitle("Sort folders first, then…").setSingleChoiceItems(new String[]{"Name · A to Z","Date · newest first","Size · largest first","File type · A to Z"},sortMode,(d,n)->{sortMode=n;prefs.edit().putInt("sort",n).apply();d.dismiss();renderToolbar();filter();}).setNegativeButton("Cancel",null).show();}
    private void folderOptions(){
        new AlertDialog.Builder(this).setTitle("Folder options").setItems(new String[]{"Select all visible items",showHidden?"Hide hidden files":"Show hidden files","Choose a folder / USB storage","Storage access","About NuttyVR"},(d,n)->{
            if(n==0)selectAll();else if(n==1){showHidden=!showHidden;prefs.edit().putBoolean("hidden",showHidden).apply();filter();}else if(n==2)chooseFolder();else if(n==3)accessOptions();else help();
        }).setNegativeButton("Close",null).show();
    }
    private void selectAll(){if(busy)return;for(Storage.Item item:visible)selected.put(item.key(),item);adapter.notifyDataSetChanged();renderToolbar();}
    private void toggle(Storage.Item item){if(busy)return;if(selected.containsKey(item.key()))selected.remove(item.key());else selected.put(item.key(),item);adapter.notifyDataSetChanged();renderToolbar();}
    private void activate(Storage.Item item){if(busy)return;if(!selected.isEmpty())toggle(item);else if(item.directory)enter(item);else preview(item);}
    private void itemMenu(Storage.Item item){
        if(busy)return;
        new AlertDialog.Builder(this).setTitle(item.name).setItems(new String[]{item.directory?"Open folder":ApkFiles.isApk(item.name,item.mime)?"Install / open app":ArchiveFiles.supported(item.name)?"Extract archive":"Open / preview","Select","Copy","Move","Rename","Details","Delete"},(d,n)->{
            if(n==0){if(item.directory)enter(item);else preview(item);}else if(n==1)toggle(item);else if(n==2||n==3){selected.clear();selected.put(item.key(),item);setClipboard(n==3);}else if(n==4)rename(item);else if(n==5)details(item);else delete(Collections.singletonList(item));
        }).setNegativeButton("Close",null).show();
    }
    private void setClipboard(boolean move){
        if(busy)return;clipboard.clear();clipboard.addAll(selected.values());clipboardMove=move;selected.clear();adapter.notifyDataSetChanged();renderToolbar();renderClipboard();toast("Choose a destination folder, then Paste here.");
    }
    private void renderClipboard(){
        if(clipboardBar==null)return;clipboardBar.removeAllViews();clipboardBar.setVisibility(clipboard.isEmpty()?View.GONE:View.VISIBLE);if(clipboard.isEmpty())return;
        TextView note=text(clipboard.size()+" item"+(clipboard.size()==1?"":"s")+" to "+(clipboardMove?"move":"copy"),12,MINT);clipboardBar.addView(note,new LinearLayout.LayoutParams(0,-2,1));addGap(clipboardBar,8);
        clipboardBar.addView(button("down","Paste here",true,this::paste),new LinearLayout.LayoutParams(-2,dp(52)));addGap(clipboardBar,6);
        clipboardBar.addView(iconButton("close","Clear clipboard",()->{if(!busy){clipboard.clear();renderClipboard();}}),new LinearLayout.LayoutParams(dp(48),dp(52)));
    }
    private void paste(){
        if(busy||clipboard.isEmpty())return;final ArrayList<Storage.Item> sources=new ArrayList<>(clipboard);final Storage.Item destination=current();final boolean moving=clipboardMove;
        runTask(moving?"Moving files":"Copying files",(cancel,progress)->{
            int completed=0;
            try{for(Storage.Item source:sources){cancel.check();storage.transfer(source,destination,moving,cancel,progress);completed++;}}
            catch(Exception e){throw new IOException(completed+" of "+sources.size()+" items completed. "+readable(e),e);}
            return completed+" item"+(completed==1?"":"s")+" "+(moving?"moved":"copied")+".";
        },()->{clipboard.clear();renderClipboard();});
    }
    private interface Task {String run(LocalFiles.Cancellation cancel,LocalFiles.Progress progress)throws Exception;}
    private void runTask(String label,Task task,Runnable after){
        if(busy)return;busy=true;activeCancel=new LocalFiles.Cancellation();final LocalFiles.Cancellation cancel=activeCancel;renderToolbar();
        LinearLayout progressView=column();pad(progressView,24,12,24,20);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);progressView.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));
        TextView status=text("Preparing…",14,TEXT);pad(status,0,18,0,0);progressView.addView(status);TextView hint=text("Keep NuttyVR open until this finishes.",12,MUTED);pad(hint,0,10,0,0);progressView.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label).setView(progressView).setNegativeButton("Cancel",null).create();dialog.setCancelable(false);show(dialog);activeDialog=dialog;
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{cancel.cancelled=true;status.setText(R.string.stopping);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);});
        operations.execute(()->{
            final long[] bytes={0},last={0};String result;boolean failed=false;
            try{result=task.run(cancel,(n,name)->{bytes[0]+=n;long now=SystemClock.uptimeMillis();if(now-last[0]>120){last[0]=now;String update=Formatter.formatFileSize(this,bytes[0])+" transferred\n"+name;handler.post(()->{if(!destroyed)status.setText(update);});}});}
            catch(Exception e){result=readable(e);failed=true;}
            final String done=result;final boolean problem=failed;
            handler.post(()->{if(destroyed)return;busy=false;activeCancel=null;activeDialog=null;dialog.dismiss();if(after!=null)after.run();selected.clear();refresh();if(problem)message(cancel.cancelled?"Operation stopped":"Could not finish",done);else toast(done);});
        });
    }
    private void newFolder(){if(busy)return;nameDialog("New folder","Untitled folder",name->{Storage.Item parent=current();runTask("Creating folder",(c,p)->{storage.mkdir(parent,name);return "Folder created.";},null);});}
    private void rename(Storage.Item item){if(busy)return;final Storage.Item parent=current();nameDialog("Rename",item.name,name->{runTask("Renaming",(c,p)->{storage.rename(parent,item,name);return "Renamed.";},null);});}
    private interface NameAction{void run(String name);}
    private void nameDialog(String label,String value,NameAction action){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(value);input.setSelectAllOnFocus(true);input.setTextColor(TEXT);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout wrap=column();pad(wrap,24,12,24,8);wrap.addView(input,new LinearLayout.LayoutParams(-1,dp(60)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label).setView(wrap).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String name=LocalFiles.validName(input.getText().toString());dialog.dismiss();action.run(name);}catch(IOException e){input.setError(e.getMessage());}});input.requestFocus();
    }
    private void delete(List<Storage.Item> items){
        if(busy)return;String subject=items.size()==1?"“"+items.get(0).name+"”":items.size()+" selected items";
        new AlertDialog.Builder(this).setTitle("Delete "+subject+"?").setMessage("This permanently deletes the selected items and everything inside selected folders. This cannot be undone.")
            .setNegativeButton("Keep files",null).setPositiveButton("Delete",(d,n)->runTask("Deleting",(c,p)->{int done=0;try{for(Storage.Item item:items){c.check();storage.delete(item,c);done++;}}catch(Exception e){throw new IOException(done+" items fully deleted. Some files may already be removed. "+readable(e),e);}return done+" items deleted.";},null)).show();
    }

    private String size(long n){return n<0?"Unknown size":Formatter.formatFileSize(this,n);}
    private String date(long time){return time<=0?"Unknown date":DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(time));}
    private void details(Storage.Item item){message(item.name,(item.directory?"Folder":item.mime)+"\n\n"+(item.directory?"":"Size: "+size(item.size)+"\n")+"Modified: "+date(item.modified)+"\n\n"+(item.file!=null?item.file.getAbsolutePath():item.uri.toString()));}
    private boolean isText(Storage.Item item){String n=item.name.toLowerCase(Locale.ROOT);return item.mime.startsWith("text/")||n.endsWith(".json")||n.endsWith(".md")||n.endsWith(".log")||n.endsWith(".xml")||n.endsWith(".csv");}
    private void preview(Storage.Item item){
        if(ApkFiles.isApk(item.name,item.mime)){startActivity(ApkActivity.forItem(this,item));return;}
        if(ArchiveFiles.supported(item.name)){extract(item);return;}
        if(item.mime.startsWith("image/")){previewImage(item);return;}
        if(isText(item)){previewText(item);return;}
        openExternal(item);
    }
    private void extract(Storage.Item item){
        if(busy)return;
        final Storage.Item parent=current();
        show(new AlertDialog.Builder(this).setTitle("Extract "+item.name+"?")
            .setMessage("Create a new folder here with the archive’s contents. Existing files and the original archive are kept.\n\nSupports ZIP, TAR, TAR.GZ and single-file RAR4/RAR5. Password protected archives are not supported yet.")
            .setPositiveButton("Extract",(d,n)->runTask("Extracting archive",(cancel,progress)->{
                Storage.Item folder=storage.extract(item,parent,getCacheDir(),cancel,progress);
                return "Extracted to “"+folder.name+"”.";
            },null)).setNeutralButton("Open with…",(d,n)->openExternal(item)).setNegativeButton("Cancel",null).create());
    }
    private void previewText(Storage.Item item){
        toast("Opening preview…");listing.execute(()->{
            try(InputStream stream=storage.input(item);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;boolean truncated=false;while((n=stream.read(buffer))!=-1){int remaining=128*1024-bytes.size();if(remaining<=0){truncated=true;break;}bytes.write(buffer,0,Math.min(remaining,n));if(n>remaining){truncated=true;break;}}
                String value=bytes.toString(StandardCharsets.UTF_8.name())+(truncated?"\n\n[Preview limited to 128 KB]":"");
                handler.post(()->{if(destroyed)return;ScrollView scroll=new ScrollView(this);TextView view=text(value,14,TEXT);view.setTypeface(Typeface.MONOSPACE);view.setTextIsSelectable(true);pad(view,24,16,24,24);scroll.addView(view);show(new AlertDialog.Builder(this).setTitle(item.name).setView(scroll).setPositiveButton("Open with…",(d,w)->openExternal(item)).setNegativeButton("Close",null).create());});
            }catch(Exception e){handler.post(()->message("Preview unavailable",readable(e)));}
        });
    }
    private void previewImage(Storage.Item item){
        toast("Opening preview…");listing.execute(()->{
            try{
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;try(InputStream in=storage.input(item)){BitmapFactory.decodeStream(in,null,opts);}
                if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("This image format cannot be previewed. Use Open with another app.");
                opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>1600||opts.outHeight/opts.inSampleSize>1200)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;
                Bitmap bitmap;try(InputStream in=storage.input(item)){bitmap=BitmapFactory.decodeStream(in,null,opts);}if(bitmap==null)throw new IOException("This image could not be decoded.");
                handler.post(()->{if(destroyed){bitmap.recycle();return;}ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(item.name);pad(image,12,12,12,12);
                    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.name).setView(image).setPositiveButton("Open with…",(d,n)->openExternal(item)).setNegativeButton("Close",null).create();show(dialog);image.getLayoutParams().height=dp(Math.min(400,getResources().getConfiguration().screenHeightDp-180));image.requestLayout();dialog.setOnDismissListener(d->{image.setImageDrawable(null);bitmap.recycle();});});
            }catch(Exception e){handler.post(()->message("Preview unavailable",readable(e)));}
        });
    }
    private void openExternal(Storage.Item item){
        try{
            Uri uri=item.file!=null?ShareProvider.uri(item.file):item.uri;
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(item.name,uri));startActivity(Intent.createChooser(intent,"Open "+item.name));
        }catch(ActivityNotFoundException e){message("No app available","Install an app that can open this file type on your headset.");}catch(RuntimeException e){message("Cannot open file",readable(e));}
    }

    private void accessOptions(){
        new AlertDialog.Builder(this).setTitle("Connect storage").setMessage("Allow shared storage to browse Downloads, videos and other headset files. Or choose a specific folder or connected USB drive.\n\nAndroid may still block other apps’ private data, including Android/data and Android/obb.")
            .setPositiveButton("Allow storage",(d,n)->requestStorage()).setNeutralButton("Choose folder",(d,n)->chooseFolder()).setNegativeButton("Later",null).show();
    }
    private void requestStorage(){
        if(Build.VERSION.SDK_INT<30){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},44);return;}
        try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}
        catch(ActivityNotFoundException e){try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}catch(ActivityNotFoundException unavailable){message("Use a chosen folder","Your headset does not expose the storage access screen. Use Choose folder instead, or follow the ADB setup command in the included README.");}}
    }
    private void chooseFolder(){
        if(busy)return;
        try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(intent,45);}
        catch(ActivityNotFoundException e){message("Folder picker unavailable","Your headset does not provide Android’s folder picker. Use Allow storage, or grant access using the ADB command in the included README.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==45&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();try{
                if((data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                else getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("tree",uri.toString()).apply();listing.execute(()->{try{Storage.Item item=storage.tree(uri);handler.post(()->{if(destroyed)return;prefs.edit().putString("treeName",item.name).apply();goRoot(item,item.name);});}catch(Exception e){handler.post(()->message("Could not connect folder",readable(e)));}});
            }catch(SecurityException e){message("Permission unavailable","Choose the folder again and allow access.");}
        }
    }
    @Override protected void onResume(){super.onResume();boolean available=hasStorage();if(storage!=null&&available!=hadStorage){hadStorage=available;if(available&&!busy)goRoot(new Storage.Item(Environment.getExternalStorageDirectory()),"Headset storage");else if(!available&&!busy)goRoot(new Storage.Item(workspace()),"Workspace");}else if(accessBanner!=null)renderAccess();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==44){hadStorage=hasStorage();if(hadStorage)goRoot(new Storage.Item(Environment.getExternalStorageDirectory()),"Headset storage");else accessOptions();}}
    private void help(){
        show(new AlertDialog.Builder(this).setTitle("A little space for everything.").setMessage("NuttyVR File Explorer 1.2\n\nBuilt for a resizable window in your headset. Point and click with the headset’s system pointer, controller or hand input.\n\nTap a folder to open it. Use the selection circle, or hold an item, to select. Copy or Move, open a destination, then Paste here. Existing files are kept with numbered names.\n\nSearch filters the current folder. Images and text have built-in previews. Tap an APK to install it through Android, or open its installed app. Tap a ZIP, TAR or RAR to extract into a new folder here. Other files open in installed apps. Choose folder can connect a USB drive if Android exposes it.\n\nYour files stay on your device. No account, ads or network permission. Android’s private-folder restrictions still apply.")
            .setPositiveButton("Got it",null).setNeutralButton("Storage access",(d,n)->accessOptions()).create());
    }
    private void show(AlertDialog dialog){if(destroyed)return;dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(shape(PANEL,22,BORDER));}
    private void message(String title,String body){if(!destroyed&&!isFinishing())show(new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK",null).create());}
    private void toast(String value){if(!destroyed)Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    private static String readable(Throwable e){String value=e.getMessage();return value==null||value.isEmpty()?"The operation could not finish. Check folder access and available storage.":value;}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);buildUi();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(current().file!=null)state.putString("path",current().file.getAbsolutePath());}
    @Override protected void onDestroy(){destroyed=true;if(activeCancel!=null)activeCancel.cancelled=true;if(activeDialog!=null)activeDialog.dismiss();handler.removeCallbacksAndMessages(null);listing.shutdownNow();operations.shutdown();super.onDestroy();}

    private String kind(Storage.Item item){if(item.directory)return "folder";if(item.mime.startsWith("image/"))return "image";if(item.mime.startsWith("video/"))return "video";if(item.mime.startsWith("audio/"))return "music";String n=item.name.toLowerCase(Locale.ROOT);if(n.endsWith(".apk"))return "apk";if(ArchiveFiles.supported(n)||n.endsWith(".7z")||n.endsWith(".gz"))return "zip";return "file";}
    private int itemColor(Storage.Item item){switch(kind(item)){case "folder":return 0xffe8c58b;case "image":return 0xffb7a6f2;case "video":return 0xff9cbff7;case "music":return 0xffefa2bf;case "apk":return MINT;case "zip":return 0xffe1b281;default:return 0xffa9bdcf;}}
    private final class FileAdapter extends BaseAdapter {
        @Override public int getCount(){return visible.size();}
        @Override public Object getItem(int pos){return visible.get(pos);}
        @Override public long getItemId(int pos){return visible.get(pos).key().hashCode();}
        @Override public int getViewTypeCount(){return 2;}
        @Override public int getItemViewType(int pos){return listMode?1:0;}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
            Storage.Item item=visible.get(position);Tile holder;
            if(recycled==null){holder=new Tile(listMode);recycled=holder.root;recycled.setTag(holder);}else holder=(Tile)recycled.getTag();
            boolean marked=selected.containsKey(item.key());holder.icon.kind=kind(item);holder.icon.tint=itemColor(item);holder.icon.invalidate();holder.name.setText(item.name);
            holder.meta.setText(item.directory?"Folder":size(item.size));holder.root.setBackground(interactive(marked?0xff253e37:CARD,0xff29394a,15,marked?MINT:BORDER));
            holder.select.kind=marked?"check":"";holder.select.tint=marked?INK:MUTED;holder.select.setBackground(shape(marked?MINT:CARD,8,marked?0:BORDER));holder.select.invalidate();
            holder.selectionTarget.setContentDescription((marked?"Deselect ":"Select ")+item.name);holder.selectionTarget.setOnClickListener(v->toggle(item));holder.more.setContentDescription("Actions for "+item.name);holder.more.setOnClickListener(v->itemMenu(item));
            holder.root.setOnClickListener(v->activate(item));holder.root.setOnLongClickListener(v->{toggle(item);return true;});holder.root.setFocusable(true);
            holder.root.setContentDescription(item.name+", "+(item.directory?"folder":size(item.size))+(marked?", selected":""));return recycled;
        }
    }
    private final class Tile {
        final LinearLayout root;final Glyph icon,select;final TextView name,meta;final FrameLayout selectionTarget;final View more;
        Tile(boolean isList){
            root=isList?row():column();pad(root,14,12,10,12);root.setLayoutParams(new AbsListView.LayoutParams(-1,dp(isList?82:176)));
            icon=new Glyph(MainActivity.this,"folder",MINT);name=text("",14,TEXT);name.setTypeface(null,Typeface.BOLD);name.setEllipsize(TextUtils.TruncateAt.END);name.setMaxLines(isList?1:2);meta=text("",11,MUTED);
            select=new Glyph(MainActivity.this,"check",MINT);selectionTarget=new FrameLayout(MainActivity.this);selectionTarget.setFocusable(true);selectionTarget.setClickable(true);selectionTarget.setBackground(interactive(Color.TRANSPARENT,0xff31483f,10,0));FrameLayout.LayoutParams check=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);selectionTarget.addView(select,check);
            more=iconButton("more","File actions",()->{});more.setBackground(interactive(Color.TRANSPARENT,0xff314352,10,0));
            if(isList){
                root.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));addGap(root,14);LinearLayout labels=column();labels.addView(name,new LinearLayout.LayoutParams(-1,-2));pad(meta,0,5,0,0);labels.addView(meta);root.addView(labels,new LinearLayout.LayoutParams(0,-2,1));root.addView(selectionTarget,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
            }else{
                LinearLayout top=row();top.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));top.addView(new View(MainActivity.this),new LinearLayout.LayoutParams(0,1,1));top.addView(selectionTarget,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top,new LinearLayout.LayoutParams(-1,dp(48)));
                LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(-1,0,1);nameLp.topMargin=dp(8);root.addView(name,nameLp);
                LinearLayout bottom=row();bottom.addView(meta,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(48)));
            }
        }
    }
}
