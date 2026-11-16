package de.chesspro.auto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProjectionService extends Service {
    private static final String CHANNEL="chesspro_training";
    private MediaProjection projection;private ImageReader reader;private VirtualDisplay display;
    private WindowManager windowManager;private MoveOverlay overlay;
    private HandlerThread captureThread;private Handler captureHandler;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService engine=Executors.newSingleThreadExecutor();
    private PieceVision vision;
    private volatile BoardDetector.Board board;
    private ChessPosition position;
    private String lastCandidate="";private int candidateCount;
    private String analysisFen="";private volatile String confirmedMove="";
    private volatile int currentRequest=0;
    private int lastPlayer=0,lastManualTurn=0,failedAttempts=0;private long nextRetryAt=0;
    private volatile long lastRead=0;
    private volatile boolean busy=false;
    private volatile boolean done=false;
    private int screenW,screenH;
    private volatile float scale=1f;
    private volatile boolean whiteBottom=true;

    @Override public void onCreate(){super.onCreate();windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);vision=new PieceVision(this);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||!intent.hasExtra("projectionData")){stopSelf();return START_NOT_STICKY;}
        try{
            NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            mgr.createNotificationChannel(new NotificationChannel(CHANNEL,"ChessProAuto Training",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification note=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("ChessProAuto läuft").setContentText("Schachbrett wird für Training analysiert")
                .setOngoing(true).setContentIntent(open).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(77,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(77,note);
            createOverlay();
            int result=intent.getIntExtra("resultCode",0);
            Intent permission=(Intent)intent.getParcelableExtra("projectionData");
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(result,permission);
            if(projection==null)throw new IllegalStateException("Keine Bildaufnahme-Berechtigung");
            captureThread=new HandlerThread("chesspro-capture");captureThread.start();captureHandler=new Handler(captureThread.getLooper());
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},captureHandler);
            DisplayMetrics metrics=getResources().getDisplayMetrics();screenW=metrics.widthPixels;screenH=metrics.heightPixels;
            reader=ImageReader.newInstance(screenW,screenH,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->{
                Image image=r.acquireLatestImage();if(image==null)return;
                long now=SystemClock.elapsedRealtime();
                if(done||busy||now-lastRead<350){image.close();return;}
                lastRead=now;busy=true;
                try{processFrame(image);}catch(Exception e){showStatus("Scanfehler: "+e.getClass().getSimpleName());}finally{image.close();busy=false;}
            },captureHandler);
            display=projection.createVirtualDisplay("ChessProAuto",screenW,screenH,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,captureHandler);
            StateBus.running=true;showStatus("Suche Schachbrett …");
        }catch(Exception e){showStatus("Startfehler: "+e.getMessage());stopSelf();}
        return START_NOT_STICKY;
    }
    private void createOverlay(){
        overlay=new MoveOverlay(this);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,
            Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT);
        lp.gravity=android.view.Gravity.TOP|android.view.Gravity.LEFT;
        windowManager.addView(overlay,lp);
    }
    private void processFrame(Image image){
        Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
        int pixelStride=plane.getPixelStride(),rowStride=plane.getRowStride();
        // MediaProjection buffers have row padding; use a correctly cropped bitmap.
        int paddedWidth=screenW+(rowStride-pixelStride*screenW)/pixelStride;
        Bitmap padded=Bitmap.createBitmap(paddedWidth,screenH,Bitmap.Config.ARGB_8888);
        buffer.rewind();padded.copyPixelsFromBuffer(buffer);
        Bitmap raw=Bitmap.createBitmap(padded,0,0,screenW,screenH);padded.recycle();
        int width=Math.min(460,screenW);int height=Math.round((float)screenH*width/screenW);
        Bitmap down=Bitmap.createScaledBitmap(raw,width,height,false);raw.recycle();
        scale=(float)screenW/width;
        BoardDetector.Frame frame=new BoardDetector.Frame(down);down.recycle();
        board=BoardDetector.find(frame,board);
        if(board==null){showStatus("Suche Schachbrett …");mark("", "Suche Schachbrett …");return;}
        PieceVision.Matched match=vision.recognizeRefined(frame,board);
        if(match==null){vision.learnStartingPosition(frame,board);match=vision.recognizeRefined(frame,board);}
        if(match==null){showStatus("Figuren nicht eindeutig – neue Stellung abwarten");mark("","Figuren nicht eindeutig");return;}
        board=match.board;
        PieceVision.Recognition read=match.recognized;
        whiteBottom=read.whiteBottom;
        if(!read.placement.equals(lastCandidate)){lastCandidate=read.placement;candidateCount=1;}
        else candidateCount++;
        if(candidateCount<2){showStatus("Bestätige Stellung …");return;}
        int player=StateBus.playerColor!=0?StateBus.playerColor:(whiteBottom?1:-1);
        int observedTurn=vision.highlightedTurn(frame,board,read);
        if(StateBus.turnOverride!=0)observedTurn=StateBus.turnOverride;
        ChessPosition updated=null;
        if(position==null){
            if(read.placement.equals(ChessPosition.START))updated=ChessPosition.start();
            else if(observedTurn!=0)updated=ChessPosition.bootstrap(read.placement,observedTurn);
        }else if(read.placement.equals(position.placement())){
            updated=position;
        }else updated=position.track(read.placement);
        // Rejoin a game only with independently known turn information.
        if(updated==null && observedTurn!=0)updated=ChessPosition.bootstrap(read.placement,observedTurn);
        if(updated==null){showStatus("Zugrecht unklar. In der App Zugrecht festlegen.");mark("","Zugrecht unklar");return;}
        position=updated;
        // Manual turn selection corrects the current position once.
        // Subsequent observed moves are tracked normally rather than forced to a fixed side.
        if(lastManualTurn!=StateBus.turnOverride){
            lastManualTurn=StateBus.turnOverride;
            if(lastManualTurn!=0&&position.turn!=lastManualTurn){
                ChessPosition manual=ChessPosition.bootstrap(position.placement(),lastManualTurn);
                if(manual!=null)position=manual;
            }
            analysisFen="";
        }
        String fen=position.fen();
        if(player!=lastPlayer){lastPlayer=player;analysisFen="";currentRequest++;}
        if(!fen.equals(analysisFen)){
            analysisFen=fen;confirmedMove="";currentRequest++;failedAttempts=0;nextRetryAt=0;
            if(position.turn==player){
                showStatus("Stockfish berechnet Zug …");mark("","Stockfish berechnet …");
                final int request=currentRequest;final ChessPosition snapshot=position;
                engine.execute(()->analyzePosition(snapshot,request));
            }else{
                showStatus("Gegner am Zug ("+(position.turn>0?"Weiß":"Schwarz")+")");
                mark("","Gegner am Zug");
            }
        }else if(position.turn==player && !confirmedMove.isEmpty()){
            mark(confirmedMove,"Zug: "+confirmedMove);
        }else if(position.turn==player && confirmedMove.isEmpty() && failedAttempts>0 && failedAttempts<3 && SystemClock.elapsedRealtime()>=nextRetryAt){
            nextRetryAt=Long.MAX_VALUE;
            final int request=currentRequest;final ChessPosition snapshot=position;
            showStatus("Neuer Stockfish-Versuch …");
            engine.execute(()->analyzePosition(snapshot,request));
        }
        StateBus.detail="FEN: "+fen+"\nSeite: "+(player>0?"Weiß":"Schwarz")+" · Zugrecht: "+(position.turn>0?"Weiß":"Schwarz");
    }
    private void analyzePosition(ChessPosition snapshot,int id){
        try{
            EngineClient.Answer answer=EngineClient.analyze(snapshot.fen());
            if(done||id!=currentRequest)return;
            boolean legal=false;for(ChessPosition.Move m:snapshot.legal())if(m.uci().equals(answer.move)){legal=true;break;}
            if(!legal)throw new Exception("Serverzug nicht legal für erkanntes Brett");
            failedAttempts=0;confirmedMove=answer.move;
            showStatus("Stockfish: "+answer.move+(answer.score.isEmpty()?"":" · "+answer.score));
            mark(answer.move,"Zug: "+answer.move);
        }catch(Exception e){
            if(done||id!=currentRequest)return;
            failedAttempts++;nextRetryAt=SystemClock.elapsedRealtime()+2200;
            showStatus("Analyse fehlgeschlagen (Versuch "+failedAttempts+"): "+e.getMessage());
            mark("","Keine bestätigte Empfehlung");
        }
    }
    private void mark(String move,String text){
        BoardDetector.Board b=board;if(b==null)return;
        int x=Math.round(b.x*scale),y=Math.round(b.y*scale),size=Math.round(b.size*scale);
        boolean orient=whiteBottom;
        ui.post(()->{if(overlay!=null)overlay.update(x,y,size,orient,move,text);});
    }
    private void showStatus(String message){StateBus.status=message;}
    @Override public void onDestroy(){
        done=true;StateBus.running=false;
        currentRequest++;
        if(reader!=null){reader.setOnImageAvailableListener(null,null);reader.close();reader=null;}
        if(display!=null){display.release();display=null;}
        if(projection!=null){projection.stop();projection=null;}
        if(captureThread!=null){captureThread.quitSafely();captureThread=null;}
        if(overlay!=null){windowManager.removeView(overlay);overlay=null;}
        engine.shutdownNow();StateBus.status="Angehalten";
        super.onDestroy();
    }
}
