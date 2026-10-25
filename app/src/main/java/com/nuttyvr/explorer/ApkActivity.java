package com.nuttyvr.explorer;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** APK inspection, user-confirmed installation, and installed-app launching. */
public final class ApkActivity extends Activity {
    private static final int UNKNOWN_SOURCES = 61, INSTALL = 62;
    private static final int BG=0xff10151e, PANEL=0xff1b2533, TEXT=0xffedf4fc, MUTED=0xff9baec4, MINT=0xff9af2c5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LocalFiles.Cancellation cancel=new LocalFiles.Cancellation();
    private volatile File staged;
    private volatile boolean destroyed, preserveSnapshot;
    private PackageInfo archive;
    private String appName="", sourceName="APK", status="Preparing APK…", failure="";
    private boolean waitingPermission, waitingInstaller, installedSuccessfully;
    private int deferredRequest, deferredResult;
    private TextView statusView;

    static Intent forItem(Context context,Storage.Item item) {
        Intent intent=new Intent(context,ApkActivity.class).putExtra("sourceName",item.name).putExtra("sourceSize",item.size);
        if(item.file!=null)intent.putExtra("sourcePath",item.file.getAbsolutePath());
        else intent.putExtra("sourceUri",item.uri.toString());
        return intent;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        sourceName=getIntent().getStringExtra("sourceName");if(sourceName==null)sourceName="APK";
        String snapshot=state==null?null:state.getString("snapshot");
        if(state!=null){waitingPermission=state.getBoolean("waitingPermission");waitingInstaller=state.getBoolean("waitingInstaller");installedSuccessfully=state.getBoolean("success");status=state.getString("status",status);}
        buildUi();
        worker.execute(()->load(snapshot));
    }
    private File cacheFolder(){return new File(getCacheDir(),"apk-installs");}
    private void load(String snapshot) {
        try {
            if(snapshot!=null)staged=ApkFiles.resolve(cacheFolder(),snapshot);
            else {
                String path=getIntent().getStringExtra("sourcePath"),uri=getIntent().getStringExtra("sourceUri");
                InputStream input;
                if(path!=null)input=new FileInputStream(path);
                else if(uri!=null)input=getContentResolver().openInputStream(Uri.parse(uri));
                else throw new IOException("Choose an APK in the file browser first.");
                if(input==null)throw new IOException("This APK cannot be read. Reconnect its folder and select it again.");
                long free;
                try{free=new StatFs(getCacheDir().getPath()).getAvailableBytes();}
                catch(RuntimeException e){input.close();throw e;}
                final long[] transferred={0},last={0};
                staged=ApkFiles.stage(input,cacheFolder(),getIntent().getLongExtra("sourceSize",-1),free,cancel,(n,name)->{
                    transferred[0]+=n;long now=SystemClock.uptimeMillis();if(now-last[0]>120){last[0]=now;String progress="Preparing APK · "+Formatter.formatFileSize(this,transferred[0]);handler.post(()->{if(!destroyed&&archive==null&&statusView!=null)statusView.setText(progress);});}
                });
            }
            cancel.check();
            PackageInfo info=getPackageManager().getPackageArchiveInfo(staged.getAbsolutePath(),0);
            if(info==null||info.applicationInfo==null)throw new IOException("This file is not a readable Android APK. Select a complete, standalone .apk file.");
            info.applicationInfo.sourceDir=staged.getAbsolutePath();info.applicationInfo.publicSourceDir=staged.getAbsolutePath();
            String label=getPackageManager().getApplicationLabel(info.applicationInfo).toString();
            cancel.check();
            handler.post(()->{
                if(destroyed){if(!preserveSnapshot)removeSnapshot();return;}
                archive=info;appName=label;
                if(!waitingInstaller&&!waitingPermission&&!installedSuccessfully)status="Choose Install APK to continue. Android will ask you to confirm the installation.";
                buildUi();
                if(deferredRequest!=0){int request=deferredRequest,result=deferredResult;deferredRequest=0;handleResult(request,result);}
            });
        }catch(Exception e){
            if(!preserveSnapshot)removeSnapshot();String message=e.getMessage()==null?"The APK could not be prepared.":e.getMessage();
            handler.post(()->{if(!destroyed){failure=message;buildUi();}});
        }
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private View action(String label,boolean primary,Runnable callback){
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(primary?0xff142d25:TEXT);b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));pad(b,18,0,18,0);
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed},shape(primary?0xffc0ffdd:0xff35475a));
        background.addState(new int[]{android.R.attr.state_focused},shape(primary?0xffc0ffdd:0xff35475a));
        background.addState(new int[]{android.R.attr.state_hovered},shape(primary?0xffc0ffdd:0xff35475a));
        background.addState(new int[]{},shape(primary?MINT:PANEL));b.setBackground(background);b.setOnClickListener(v->callback.run());return b;
    }
    private void addAction(LinearLayout layout,String label,boolean primary,Runnable callback){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.topMargin=dp(12);layout.addView(action(label,primary,callback),lp);}
    private PackageInfo installed(){if(archive==null)return null;try{return getPackageManager().getPackageInfo(archive.packageName,0);}catch(PackageManager.NameNotFoundException e){return null;}}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);pad(layout,32,28,32,28);scroll.addView(layout);setContentView(scroll);
        TextView brand=text("NUTTYVR  /  APK",12,MINT);brand.setLetterSpacing(.12f);layout.addView(brand);
        TextView heading=text(installedSuccessfully?"Ready to launch.":"Install an app.",28,TEXT);heading.setTypeface(null,Typeface.BOLD);pad(heading,0,18,0,20);layout.addView(heading);
        Glyph icon=new Glyph(this,"apk",MINT);layout.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
        TextView name=text(archive==null?sourceName:appName,23,TEXT);name.setTypeface(null,Typeface.BOLD);pad(name,0,16,0,8);layout.addView(name);
        if(!failure.isEmpty()){
            TextView error=text(failure,16,0xffffa8b0);pad(error,0,12,0,12);layout.addView(error);addAction(layout,"Back to files",false,this::finish);return;
        }
        if(archive==null){ProgressBar progress=new ProgressBar(this);layout.addView(progress,new LinearLayout.LayoutParams(dp(36),dp(36)));statusView=text(status,15,MUTED);pad(statusView,0,14,0,0);layout.addView(statusView);addAction(layout,"Cancel",false,this::finish);return;}
        TextView packageName=text(archive.packageName,14,MUTED);packageName.setTextIsSelectable(true);layout.addView(packageName);
        String version=archive.versionName==null?Long.toString(archive.getLongVersionCode()):archive.versionName;
        TextView detail=text("Version "+version+"  ·  "+Formatter.formatFileSize(this,staged.length()),14,MUTED);pad(detail,0,8,0,16);layout.addView(detail);
        PackageInfo existing=installed();
        if(existing!=null){String installedVersion=existing.versionName==null?Long.toString(existing.getLongVersionCode()):existing.versionName;TextView current=text("Installed version: "+installedVersion,14,MINT);pad(current,0,0,0,12);layout.addView(current);}
        statusView=text(status,16,TEXT);statusView.setBackground(shape(PANEL));pad(statusView,18,16,18,16);layout.addView(statusView,new LinearLayout.LayoutParams(-1,-2));
        boolean compatible=archive.applicationInfo.minSdkVersion<=Build.VERSION.SDK_INT;
        if(!compatible){TextView unsupported=text("This APK requires Android API "+archive.applicationInfo.minSdkVersion+". Your headset uses API "+Build.VERSION.SDK_INT+".",14,0xffffa8b0);pad(unsupported,0,14,0,0);layout.addView(unsupported);}
        if(!waitingInstaller&&!waitingPermission){
            if(existing!=null||installedSuccessfully)addAction(layout,"Open app",true,this::launchInstalled);
            if(!installedSuccessfully&&compatible)addAction(layout,existing==null?"Install APK":"Install / update APK",existing==null,this::requestInstall);
            addAction(layout,"Back to files",false,this::finish);
        }
    }
    private void setStatus(String value){status=value;if(statusView!=null)statusView.setText(value);}
    private void requestInstall(){
        if(archive==null||waitingInstaller||waitingPermission)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            setStatus("Allow NuttyVR to install apps on the next screen, then return here.");waitingPermission=true;
            try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),UNKNOWN_SOURCES);buildUi();return;}
            catch(ActivityNotFoundException e){waitingPermission=false;/* The headset installer may provide its own permission screen. */}
            catch(SecurityException e){waitingPermission=false;setStatus("App installation is blocked by your headset settings.");buildUi();return;}
        }
        startInstaller();
    }
    private void startInstaller(){
        try{
            File snapshot=ApkFiles.resolve(cacheFolder(),staged.getName());Uri uri=ShareProvider.apkUri(snapshot);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,ApkFiles.MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);
            intent.setClipData(ClipData.newRawUri(sourceName,uri));waitingInstaller=true;
            setStatus("Finish or cancel the installation in Android’s system installer.");
            try{startActivityForResult(intent,INSTALL);}
            catch(ActivityNotFoundException e){intent.setAction(Intent.ACTION_VIEW);startActivityForResult(intent,INSTALL);}
            buildUi();
        }catch(ActivityNotFoundException e){waitingInstaller=false;setStatus("Your headset does not expose an APK installer. You can install this APK from a computer using a sideloading tool.");buildUi();}
        catch(Exception e){waitingInstaller=false;setStatus("Could not start installation. "+(e.getMessage()==null?"Check the headset’s app installation settings.":e.getMessage()));buildUi();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=UNKNOWN_SOURCES&&request!=INSTALL)return;
        if(archive==null){deferredRequest=request;deferredResult=result;return;}
        handleResult(request,result);
    }
    private void handleResult(int request,int result){
        if(request==UNKNOWN_SOURCES){waitingPermission=false;if(getPackageManager().canRequestPackageInstalls()){startInstaller();return;}setStatus("App installation was not enabled. Tap Install APK to try again.");}
        else{waitingInstaller=false;installedSuccessfully=result==RESULT_OK;
            if(installedSuccessfully)setStatus("Installation completed. Select Open app to launch it.");
            else if(installed()!=null)setStatus("Installation was cancelled or did not complete. You can open the version already installed, or try again.");
            else setStatus("Installation was cancelled or did not complete. Tap Install APK to try again.");
        }
        buildUi();
    }
    private void launchInstalled(){
        if(archive==null)return;PackageManager pm=getPackageManager();
        try{
            Intent launch=pm.getLaunchIntentForPackage(archive.packageName);
            if(launch==null)launch=pm.getLeanbackLaunchIntentForPackage(archive.packageName);
            if(launch==null){Intent vr=new Intent(Intent.ACTION_MAIN).addCategory("com.oculus.intent.category.VR").setPackage(archive.packageName);
                for(ResolveInfo candidate:pm.queryIntentActivities(vr,0)){ActivityInfo a=candidate.activityInfo;if(a!=null&&a.exported&&a.enabled&&a.applicationInfo.enabled){launch=new Intent(vr).setComponent(new ComponentName(a.packageName,a.name));break;}}}
            if(launch==null){setStatus("This package has no launchable app activity, or is not installed. Check the headset’s app library.");return;}
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);
        }catch(RuntimeException e){setStatus("The headset could not launch this app. Try opening it from the app library.");}
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);buildUi();}
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);File file=staged;if(file!=null)state.putString("snapshot",file.getName());
        state.putBoolean("waitingPermission",waitingPermission);state.putBoolean("waitingInstaller",waitingInstaller);state.putBoolean("success",installedSuccessfully);state.putString("status",status);
    }
    private void removeSnapshot(){File file=staged;if(file!=null)file.delete();}
    @Override protected void onDestroy(){preserveSnapshot=!isFinishing()||waitingInstaller;destroyed=true;cancel.cancelled=true;handler.removeCallbacksAndMessages(null);worker.shutdown();if(!preserveSnapshot)removeSnapshot();super.onDestroy();}
}
