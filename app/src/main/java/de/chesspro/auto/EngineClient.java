package de.chesspro.auto;

import org.json.JSONObject;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

/** Cloud Stockfish adapter. Only FEN positions are sent; never screenshot pixels. */
final class EngineClient {
    static final class Answer {final String move;final String score;Answer(String m,String s){move=m;score=s;}}
    static Answer analyze(String fen) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL("https://chess-api.com/v1").openConnection();
        conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setConnectTimeout(4000);conn.setReadTimeout(7500);
        conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
        JSONObject req=new JSONObject();req.put("fen",fen);req.put("depth",12);req.put("maxThinkingTime",50);
        try{
            byte[] data=req.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream output=conn.getOutputStream()){output.write(data);}
            if(conn.getResponseCode()!=200)throw new Exception("HTTP "+conn.getResponseCode());
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream input=conn.getInputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>100_000)throw new Exception("Antwort zu groß");}}
            JSONObject result=new JSONObject(out.toString("UTF-8"));
            String move=result.optString("move","");
            if(!move.matches("[a-h][1-8][a-h][1-8][qrbn]?"))throw new Exception("Kein gültiger Enginezug");
            String score=result.has("eval")?String.valueOf(result.optDouble("eval",0)):"";
            return new Answer(move,score);
        }finally{conn.disconnect();}
    }
}
