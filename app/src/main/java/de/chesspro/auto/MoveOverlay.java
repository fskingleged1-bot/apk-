package de.chesspro.auto;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Transparent click-through HUD drawn in display coordinates. */
public class MoveOverlay extends View {
    private int boardX,boardY,boardSize;
    private boolean whiteBottom=true;
    private String move="",label="Suche Brett …";
    private final Paint paint=new Paint(3);
    MoveOverlay(Context context){super(context);setBackgroundColor(Color.TRANSPARENT);}
    void update(int x,int y,int size,boolean wb,String recommendation,String text){
        boardX=x;boardY=y;boardSize=size;whiteBottom=wb;move=recommendation==null?"":recommendation;label=text==null?"":text;postInvalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        float left=12*density,top=19*density,right=Math.min(getWidth()-10*density,340*density),height=57*density;
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xe7101822);canvas.drawRoundRect(new RectF(left,top,right,top+height),13*density,13*density,paint);
        paint.setColor(0xff28dfa3);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextSize(13*density);
        canvas.drawText("♞ CHESS PRO · ANDROID",left+12*density,top+21*density,paint);
        paint.setColor(Color.WHITE);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(12*density);
        String desc=label.length()>43?label.substring(0,42)+"…":label;
        canvas.drawText(desc,left+12*density,top+43*density,paint);
        if(boardSize<80||!move.matches("[a-h][1-8][a-h][1-8][qrbn]?"))return;
        drawSquare(canvas,move.substring(0,2),0xff00e59b,0x5500e59b,density);
        drawSquare(canvas,move.substring(2,4),0xffff4764,0x55ff4764,density);
    }
    private void drawSquare(Canvas canvas,String square,int stroke,int fill,float density){
        int file=square.charAt(0)-'a',rank=square.charAt(1)-'1';
        if(file<0||file>7||rank<0||rank>7)return;
        int col=whiteBottom?file:7-file,row=whiteBottom?7-rank:rank;
        float cell=boardSize/8f;float x=boardX+col*cell,y=boardY+row*cell;
        float pad=Math.max(2*density,cell*.07f);
        RectF rect=new RectF(x+pad,y+pad,x+cell-pad,y+cell-pad);
        paint.setStyle(Paint.Style.FILL);paint.setColor(fill);canvas.drawRoundRect(rect,5*density,5*density,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2*density,cell*.08f));paint.setColor(stroke);
        canvas.drawRoundRect(rect,5*density,5*density,paint);paint.setStyle(Paint.Style.FILL);
    }
}
