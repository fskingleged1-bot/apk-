package android.graphics;
import java.io.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;
public class BitmapFactory {public static Bitmap decodeStream(InputStream in) throws IOException {BufferedImage a=ImageIO.read(in);int w=a.getWidth(),h=a.getHeight();return new Bitmap(w,h,a.getRGB(0,0,w,h,null,0,w));}}
