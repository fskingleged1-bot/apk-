package de.chesspro.auto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Theme-normalized 20x20 piece masks, based on v14 computer vision. */
final class PieceVision {
    static final String START=ChessPosition.START;
    static final class Feature {boolean[] mask=new boolean[400];short[] brightness=new short[400];int count;double foreground;}
    static final class Recognition {final String placement;final boolean whiteBottom;final double score;
        Recognition(String p,boolean w,double s){placement=p;whiteBottom=w;score=s;}}
    final Map<Character,ArrayList<Feature>> samples=new HashMap<>();
    static final class Matched {
        final Recognition recognized;final BoardDetector.Board board;
        Matched(Recognition r,BoardDetector.Board b){recognized=r;board=b;}
    }
    int minimum=22;
    PieceVision(Context context){
        try(InputStream in=context.getAssets().open("known_board.png")){
            Bitmap image=BitmapFactory.decodeStream(in);BoardDetector.Frame f=new BoardDetector.Frame(image);
            BoardDetector.Board board=new BoardDetector.Board(0,0,784,0xffebecd0,0xff739552,254);
            char[] placement=START.toCharArray();placement[52]='.';placement[36]='P';
            for(int r=0;r<8;r++)for(int c=0;c<8;c++){
                int idx=63-(r*8+c);char piece=placement[idx];
                if(piece!='.')samples.computeIfAbsent(piece,k->new ArrayList<>()).add(feature(f,board,r,c));
            }
            image.recycle();
        }catch(Exception e){StateBus.detail="Vorlagen konnten nicht geladen werden: "+e.getMessage();}
    }
    private static int[] pixelComponents(int x){return new int[]{(x>>16)&255,(x>>8)&255,x&255};}
    static Feature feature(BoardDetector.Frame frame,BoardDetector.Board b,int row,int col){
        Feature f=new Feature();int base=((row+col)&1)==0?b.even:b.odd;
        int[] colors=new int[8];double[][] corners={{.09,.09},{.09,.91},{.91,.09},{.91,.91},{.06,.35},{.94,.35},{.35,.06},{.35,.94}};
        for(int i=0;i<8;i++)colors[i]=BoardDetector.sample(frame,b.x,b.y,b.size,row,col,corners[i][0],corners[i][1]);
        int best=0,occ=0;
        for(int x=0;x<8;x++){int hits=0;for(int y=0;y<8;y++)if(BoardDetector.colorDist(colors[x],colors[y])<260)hits++;if(hits>occ){occ=hits;best=colors[x];}}
        if(occ>=3)base=best;
        double light=0;
        for(int y=0;y<20;y++)for(int x=0;x<20;x++){
            double fx=.16+(x+.5)*.68/20.,fy=.16+(y+.5)*.68/20.;
            int px=BoardDetector.sample(frame,b.x,b.y,b.size,row,col,fx,fy);
            boolean active=BoardDetector.colorDist(px,base)>1152;
            int index=y*20+x;
            f.mask[index]=active;
            int luminance=(299*(((px>>16)&255)-((base>>16)&255))+587*(((px>>8)&255)-((base>>8)&255))+114*((px&255)-(base&255)))/1000;
            f.brightness[index]=(short)Math.max(-127,Math.min(127,luminance));
            if(active){f.count++;light+=luminance;}
        }
        if(f.count>0)f.foreground=light/f.count;
        return f;
    }
    static double similarity(Feature a,Feature b){
        int common=0,difference=0;
        for(int i=0;i<400;i++)if(a.mask[i]&&b.mask[i]){common++;difference+=Math.abs(a.brightness[i]-b.brightness[i]);}
        if(common==0)return 0;
        double dice=2.*common/Math.max(1,a.count+b.count);
        double light=Math.max(0,1.-difference/(common*140.));
        return .82*dice+.18*light;
    }
    private Recognition recognize(BoardDetector.Frame frame,BoardDetector.Board board,boolean whiteBottom){
        char[] pieces=new char[64];Arrays.fill(pieces,'.');double total=0;int occupied=0;
        for(int row=0;row<8;row++)for(int col=0;col<8;col++){
            Feature f=feature(frame,board,row,col);
            int pos=whiteBottom?row*8+col:63-(row*8+col);
            if(f.count<minimum)continue;
            double best=0,second=0;char symbol='.';
            for(Map.Entry<Character,ArrayList<Feature>> entry:samples.entrySet()){
                double score=0;for(Feature reference:entry.getValue())score=Math.max(score,similarity(f,reference));
                if(score>best){second=best;best=score;symbol=entry.getKey();}else if(score>second)second=score;
            }
            if(best<.57||best-second<.007)return null;
            pieces[pos]=symbol;total+=best;occupied++;
        }
        if(occupied<2)return null;
        String placement=new String(pieces);
        if(!ChessPosition.valid(placement))return null;
        return new Recognition(placement,whiteBottom,total/occupied);
    }
    private static int orientationScore(String board){
        int score=0;for(int i=0;i<64;i++){char p=board.charAt(i);if(p=='.')continue;
            int v=Character.toLowerCase(p)=='k'?3:Character.toLowerCase(p)=='p'?2:1;
            score+=(Character.isUpperCase(p)?1:-1)*v*(i/8-3);
        }return score;
    }
    Recognition recognizeFlexible(BoardDetector.Frame frame,BoardDetector.Board board){
        Recognition white=recognize(frame,board,true),black=recognize(frame,board,false);
        if(white!=null&&(black==null||orientationScore(white.placement)>orientationScore(black.placement)))return white;
        return black;
    }
    /** Improve exact board alignment if a valid grid was found but pieces were unclear. */
    Matched recognizeRefined(BoardDetector.Frame frame,BoardDetector.Board board) {
        Recognition initial=recognizeFlexible(frame,board);
        if(initial!=null)return new Matched(initial,board);
        Matched best=null;
        for(int ds=-2;ds<=2;ds++)for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
            if(ds==0&&dy==0&&dx==0)continue;
            BoardDetector.Board adjusted=new BoardDetector.Board(board.x+dx,board.y+dy,board.size+ds,board.even,board.odd,board.score);
            if(adjusted.x<0||adjusted.y<0||adjusted.x+adjusted.size>frame.width||adjusted.y+adjusted.size>frame.height)continue;
            Recognition read=recognizeFlexible(frame,adjusted);
            if(read!=null&&(best==null||read.score>best.recognized.score))best=new Matched(read,adjusted);
        }
        return best;
    }
    void learnStartingPosition(BoardDetector.Frame f,BoardDetector.Board board) {
        Feature[][] all=new Feature[8][8];int[] occupied=new int[32],empty=new int[32];int no=0,ne=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            all[r][c]=feature(f,board,r,c);
            if(r<=1||r>=6)occupied[no++]=all[r][c].count;else empty[ne++]=all[r][c].count;
        }
        Arrays.sort(occupied);Arrays.sort(empty);
        int lower=occupied[16],upper=empty[16];
        if(lower<25||upper>lower*.55)return;
        int threshold=Math.max(16,(lower+upper)/2);
        int numOccupied=0,numEmpty=0;double top=0,bottom=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            Feature fe=all[r][c];
            if(r<=1||r>=6){if(fe.count>=threshold)numOccupied++;}else if(fe.count<threshold)numEmpty++;
            if(r<=1)top+=fe.foreground;if(r>=6)bottom+=fe.foreground;
        }
        if(numOccupied<32||numEmpty<32||Math.abs(bottom-top)<60)return;
        boolean whiteBottom=bottom>top;
        HashMap<Character,ArrayList<Feature>> learned=new HashMap<>();
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            if(r>1&&r<6)continue;
            int idx=whiteBottom?r*8+c:63-(r*8+c);char piece=START.charAt(idx);
            if(piece!='.')learned.computeIfAbsent(piece,k->new ArrayList<>()).add(all[r][c]);
        }
        if(learned.size()==12){samples.clear();samples.putAll(learned);minimum=threshold;}
    }
    int highlightedTurn(BoardDetector.Frame f,BoardDetector.Board board, Recognition view){
        int[] found=new int[3];int cnt=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            int color=BoardDetector.sample(f,board.x,board.y,board.size,r,c,.07,.07);
            int base=((r+c)&1)==0?board.even:board.odd;
            int rr=(color>>16)&255,gg=(color>>8)&255,bb=color&255;
            int dist=Math.abs(rr-((base>>16)&255))+Math.abs(gg-((base>>8)&255))+Math.abs(bb-(base&255));
            if(rr>179&&gg>170&&bb<185&&dist>85){if(cnt==3)return 0;found[cnt++]=r*8+c;}
        }
        if(cnt!=2)return 0;
        int mover=0;
        for(int i=0;i<2;i++){
            int idx=view.whiteBottom?found[i]:63-found[i];char p=view.placement.charAt(idx);
            if(p=='.')continue;
            if(mover!=0)return 0;
            mover=Character.isUpperCase(p)?1:-1;
        }
        return -mover;
    }
}
