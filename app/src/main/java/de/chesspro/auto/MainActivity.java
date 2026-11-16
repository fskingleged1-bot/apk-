package de.chesspro.auto;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST=412;
    private TextView status, details;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Button autoColor, blackColor, whiteColor, turnAuto, turnWhite, turnBlack;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(38,38,38,30); root.setBackgroundColor(Color.rgb(10,19,26));
        scroll.addView(root);
        addText(root,"♞ CHESS PRO AUTO",27,Color.rgb(34,224,166),true);
        addText(root,"ANDROID · TRAINING · V1",12,Color.rgb(151,167,183),true);
        addText(root,"Automatische Schachbrettsuche, grüne und rote Zugmarkierung und Stockfish-Cloud-Analyse.",15,Color.WHITE,false);
        Button start=button(root,"▶  TRAINING STARTEN");
        start.setOnClickListener(v->startCapture());
        Button stop=button(root,"■  ANALYSE STOPPEN");
        stop.setOnClickListener(v->{stopService(new Intent(this,ProjectionService.class)); StateBus.status="Gestoppt";StateBus.running=false;});
        addText(root,"DEINE SPIELFARBE",13,Color.rgb(34,224,166),true);
        autoColor=button(root,"Auto: Figuren unten"); blackColor=button(root,"Ich spiele SCHWARZ"); whiteColor=button(root,"Ich spiele WEISS");
        autoColor.setOnClickListener(v->setPlayer(0)); blackColor.setOnClickListener(v->setPlayer(-1)); whiteColor.setOnClickListener(v->setPlayer(1));
        addText(root,"ZUGRECHT EINMALIG KORRIGIEREN",13,Color.rgb(34,224,166),true);
        turnAuto=button(root,"Zugrecht automatisch"); turnWhite=button(root,"Weiß ist am Zug"); turnBlack=button(root,"Schwarz ist am Zug");
        turnAuto.setOnClickListener(v->setTurn(0)); turnWhite.setOnClickListener(v->setTurn(1)); turnBlack.setOnClickListener(v->setTurn(-1));
        addText(root,"STATUS",13,Color.rgb(34,224,166),true);
        status=addText(root,"",15,Color.WHITE,false);
        details=addText(root,"",12,Color.rgb(174,186,197),false);
        addText(root,"Beim Start zeigt Android eine Freigabe für Bildschirmaufnahmen. Bitte den gesamten Bildschirm wählen. Die App speichert keine Screenshots. Die Cloud sendet nur die erkannte FEN-Stellung an chess-api.com.",12,Color.rgb(159,174,182),false);
        addText(root,"Nur für Bot-Partien, selbstständiges Training und erlaubte Analysen. Erste Android-Version: andere Brettdesigns und laufende Partien können noch fehlschlagen.",12,Color.rgb(159,174,182),false);
        setContentView(scroll);
        handler.post(new Runnable(){@Override public void run(){
            status.setText(StateBus.status);details.setText(StateBus.detail);
            styleSelected(autoColor,StateBus.playerColor==0);styleSelected(whiteColor,StateBus.playerColor==1);styleSelected(blackColor,StateBus.playerColor==-1);
            styleSelected(turnAuto,StateBus.turnOverride==0);styleSelected(turnWhite,StateBus.turnOverride==1);styleSelected(turnBlack,StateBus.turnOverride==-1);
            handler.postDelayed(this,500);
        }});
    }
    private TextView addText(LinearLayout parent,String text,int size,int color,boolean bold){
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,14,0,14); if(bold)t.setTypeface(null, Typeface.BOLD);parent.addView(t);return t;
    }
    private Button button(LinearLayout root,String text){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);root.addView(b);return b;
    }
    private void styleSelected(Button b, boolean selected) { b.setAlpha(selected?1f:.60f); }
    private void setPlayer(int color){StateBus.playerColor=color;getPreferences(MODE_PRIVATE).edit().putInt("player",color).apply();}
    private void setTurn(int turn){StateBus.turnOverride=turn;getPreferences(MODE_PRIVATE).edit().putInt("turn",turn).apply();}
    private void startCapture() {
        if(!Settings.canDrawOverlays(this)){
            StateBus.status="Overlay erlauben und anschließend erneut Start drücken.";
            Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);return;
        }
        StateBus.playerColor=getPreferences(MODE_PRIVATE).getInt("player",0);
        StateBus.turnOverride=getPreferences(MODE_PRIVATE).getInt("turn",0);
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(),CAPTURE_REQUEST);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=CAPTURE_REQUEST)return;
        if(result!=RESULT_OK||data==null){StateBus.status="Aufnahme nicht erlaubt.";return;}
        Intent service=new Intent(this,ProjectionService.class);
        service.putExtra("resultCode",result);
        service.putExtra("projectionData",data);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
        StateBus.status="Bildschirmaufnahme startet …";
        // Let the user open the chess website; no manual selection needed.
        moveTaskToBack(true);
    }
}
