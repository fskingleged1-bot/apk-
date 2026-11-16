package android.content.res;
import java.io.*;
public class AssetManager {public InputStream open(String file) throws IOException {return new FileInputStream("app/src/main/assets/"+file);}}
