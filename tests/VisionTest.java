package de.chesspro.auto;
import android.graphics.Bitmap;import android.graphics.BitmapFactory;import android.content.Context;
import javax.imageio.ImageIO;import java.io.File;import java.io.FileInputStream;import java.awt.image.BufferedImage;
public class VisionTest {
    static void check(boolean x,String s){if(!x)throw new AssertionError(s);}
    static BoardDetector.Frame load(String path,int width) throws Exception {
        BufferedImage source=ImageIO.read(new File(path));int height=(int)Math.round((double)source.getHeight()*width/source.getWidth());
        java.awt.Image resized=source.getScaledInstance(width,height,java.awt.Image.SCALE_FAST);
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=out.createGraphics();g.drawImage(resized,0,0,null);g.dispose();
        return new BoardDetector.Frame(new Bitmap(width,height,out.getRGB(0,0,width,height,null,0,width)));
    }
    public static void main(String[] args) throws Exception {
        String path="app/src/main/assets/known_board.png";
        if(args.length>0)path=args[0];
        BoardDetector.Frame frame=load(path,420);
        long t=System.currentTimeMillis();
        BoardDetector.Board board=BoardDetector.find(frame,null);
        check(board!=null,"board detected");
        System.out.println("Board found "+board.x+","+board.y+" size="+board.size+" score="+board.score+" in "+(System.currentTimeMillis()-t)+" ms");
        PieceVision vision=new PieceVision(new Context());
        PieceVision.Matched matching=vision.recognizeRefined(frame,board);
        PieceVision.Recognition result=matching==null?null:matching.recognized;
        check(result!=null,"pieces recognized");
        check(!result.whiteBottom,"black below");
        ChessPosition pos=ChessPosition.start();
        ChessPosition next=pos.track(result.placement);
        check(next!=null,"track e4 by image");
        check(next.turn==-1,"black to move");
        System.out.println("Position recognized (score="+result.score+") "+next.fen());
    }
}
