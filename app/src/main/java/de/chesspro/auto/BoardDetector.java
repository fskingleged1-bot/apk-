package de.chesspro.auto;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/** Finds a checkerboard in a downsized screenshot without DOM access. */
final class BoardDetector {
    static final class Frame {
        final int width,height;final int[] pixels;
        Frame(Bitmap bmp){width=bmp.getWidth();height=bmp.getHeight();pixels=new int[width*height];bmp.getPixels(pixels,0,width,0,0,width,height);}
        int rgb(int x,int y){return x<0||y<0||x>=width||y>=height?0:pixels[y*width+x];}
    }
    static final class Board {
        int x,y,size;int even,odd;double score;
        Board(int xx,int yy,int ss,int a,int b,double sc){x=xx;y=yy;size=ss;even=a;odd=b;score=sc;}
    }
    private static int quant(int rgb){return ((rgb>>19)&31)<<10|((rgb>>11)&31)<<5|((rgb>>3)&31);}
    private static int fromQuant(int k){return 0xff000000 | (((k>>10)&31)*8+4)<<16 | (((k>>5)&31)*8+4)<<8 | ((k&31)*8+4);}
    static int colorDist(int a,int b){int r=((a>>16)&255)-((b>>16)&255),g=((a>>8)&255)-((b>>8)&255),bb=(a&255)-(b&255);return r*r+g*g+bb*bb;}
    static int sample(Frame f,int x,int y,int s,int row,int col,double fx,double fy){return f.rgb(x+(int)((col+fx)*s/8.),y+(int)((row+fy)*s/8.));}
    private static int[] mode(int[] colors){HashMap<Integer,Integer> map=new HashMap<>();int best=0,n=0;for(int rgb:colors){int k=quant(rgb),cnt=map.getOrDefault(k,0)+1;map.put(k,cnt);if(cnt>n){n=cnt;best=k;}}return new int[]{best,n};}
    private static Board rough(Frame f,int x,int y,int s){
        if(x<0||y<0||x+s>f.width||y+s>f.height)return null;
        boolean checker=false;
        int[][] patches={{2,2},{2,4},{4,2},{4,4},{3,1},{3,5}};
        for(int[] patch:patches){int r=patch[0],c=patch[1];
            int a=sample(f,x,y,s,r,c,.10,.10),b=sample(f,x,y,s,r,c+1,.10,.10),cc=sample(f,x,y,s,r+1,c,.10,.10),d=sample(f,x,y,s,r+1,c+1,.10,.10);
            if(colorDist(a,b)>2300&&colorDist(a,cc)>2300&&colorDist(a,d)<2400&&colorDist(b,cc)<2400){checker=true;break;}
        }
        if(!checker)return null;
        int[] even=new int[32],odd=new int[32];int e=0,o=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(((r+c)&1)==0)even[e++]=sample(f,x,y,s,r,c,.10,.10);else odd[o++]=sample(f,x,y,s,r,c,.10,.10);
        int[] me=mode(even),mo=mode(odd);
        if(me[1]<20||mo[1]<20)return null;
        int a=fromQuant(me[0]),b=fromQuant(mo[0]),diff=colorDist(a,b);
        if(diff<2300)return null;
        return new Board(x,y,s,a,b,me[1]+mo[1]+Math.min(5,diff/7000.));
    }
    private static double detailed(Frame f,int x,int y,int s,int a,int b){
        if(x<0||y<0||x+s>f.width||y+s>f.height)return -1;
        double score=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            int base=((r+c)&1)==0?a:b;
            for(double[] xy:new double[][]{{.085,.085},{.915,.085},{.085,.915},{.915,.915}}){
                int d=colorDist(sample(f,x,y,s,r,c,xy[0],xy[1]),base);
                if(d<950)score+=1;else if(d<2300)score+=.25;else score-=.1;
            }
        }
        // Exact grid-line alignment matters: a 2-3 px offset can shift
        // pieces into the wrong recognition masks while corners still match.
        if(score<185)return score;
        int edges=0;
        for(int r=0;r<8;r++)for(int edge=1;edge<8;edge++){
            int py=y+(int)((r+.13)*s/8.),px=x+(int)(edge*s/8.);
            if(colorDist(f.rgb(px-2,py),f.rgb(px+2,py))>2600)edges++;
        }
        for(int c=0;c<8;c++)for(int edge=1;edge<8;edge++){
            int px=x+(int)((c+.13)*s/8.),py=y+(int)(edge*s/8.);
            if(colorDist(f.rgb(px,py-2),f.rgb(px,py+2))>2600)edges++;
        }
        return score+edges*.28;
    }
    static Board find(Frame f, Board prev){
        if(prev!=null && prev.x+prev.size<=f.width && prev.y+prev.size<=f.height) {
            double score=detailed(f,prev.x,prev.y,prev.size,prev.even,prev.odd);
            if(score>175){prev.score=score;return prev;}
        }
        int limit=Math.min(f.width,f.height);
        ArrayList<Board> best=new ArrayList<>();
        // Prioritize full-width square mobile layouts, but search the entire screen.
        for(int s=Math.max(160,limit/2);s<=limit;s+=Math.max(7,limit/45)) {
            int step=Math.max(8,s/23);
            for(int y=0;y+s<=f.height;y+=step)for(int x=0;x+s<=f.width;x+=step){
                Board b=rough(f,x,y,s);
                if(b!=null&&b.score>=47){best.add(b);if(best.size()>16){best.sort((p,q)->Double.compare(q.score,p.score));best.remove(best.size()-1);}}
            }
        }
        if(best.isEmpty())return null;
        Board winner=null;
        for(Board candidate:best){
            Board local=null;
            for(int s=Math.max(150,candidate.size-18);s<=Math.min(limit,candidate.size+18);s+=3)
            for(int y=Math.max(0,candidate.y-20);y<=Math.min(f.height-s,candidate.y+20);y+=3)
            for(int x=Math.max(0,candidate.x-20);x<=Math.min(f.width-s,candidate.x+20);x+=3){
                double score=detailed(f,x,y,s,candidate.even,candidate.odd);
                if(local==null||score>local.score)local=new Board(x,y,s,candidate.even,candidate.odd,score);
            }
            if(local==null)continue;
            Board refined=local;
            for(int s=Math.max(150,local.size-3);s<=Math.min(limit,local.size+3);s++)
            for(int y=Math.max(0,local.y-3);y<=Math.min(f.height-s,local.y+3);y++)
            for(int x=Math.max(0,local.x-3);x<=Math.min(f.width-s,local.x+3);x++){
                double score=detailed(f,x,y,s,candidate.even,candidate.odd);
                if(score>refined.score)refined=new Board(x,y,s,candidate.even,candidate.odd,score);
            }
            if(winner==null||refined.score>winner.score)winner=refined;
        }
        return winner!=null&&winner.score>=195?winner:null;
    }
}
