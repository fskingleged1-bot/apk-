package android.graphics;
public class Bitmap {
    private final int width,height;private final int[] pixels;
    public Bitmap(int w,int h,int[] p){width=w;height=h;pixels=p;}
    public int getWidth(){return width;}
    public int getHeight(){return height;}
    public void getPixels(int[] dest,int offset,int stride,int x,int y,int w,int h){for(int row=0;row<h;row++)System.arraycopy(pixels,(y+row)*width+x,dest,offset+row*stride,w);}
    public void recycle(){}
}
