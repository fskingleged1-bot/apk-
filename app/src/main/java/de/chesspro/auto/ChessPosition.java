package de.chesspro.auto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Self-contained chess move legality, position tracking, and FEN generation. */
public final class ChessPosition {
    public final char[] board;
    public final int turn,castle,enPassant,half,full;
    // castle bits: 1=K 2=Q 4=k 8=q
    public ChessPosition(char[] cells,int side,int rights,int ep,int halfMove,int fullMove){
        board=cells.clone();turn=side;castle=rights;enPassant=ep;half=halfMove;full=fullMove;
    }
    public static final String START="rnbqkbnrpppppppp................................PPPPPPPPRNBQKBNR";
    public static ChessPosition start(){return new ChessPosition(START.toCharArray(),1,15,-1,0,1);}
    public static boolean valid(String placement){
        if(placement==null||placement.length()!=64)return false;
        int[] count=new int[128];int white=0,black=0;
        for(char p:placement.toCharArray()){
            if(p=='.')continue;
            if(p>=128||"pnbrqkPNBRQK".indexOf(p)<0)return false;
            count[p]++;if(Character.isUpperCase(p))white++;else black++;
        }
        return count['K']==1&&count['k']==1&&count['P']<=8&&count['p']<=8&&white<=16&&black<=16;
    }
    private static int side(char p){return p=='.'?0:Character.isUpperCase(p)?1:-1;}
    private static boolean inside(int r,int c){return r>=0&&r<8&&c>=0&&c<8;}
    private static int idx(int r,int c){return r*8+c;}
    public String placement(){return new String(board);}
    private static boolean attacks(char[] b,int index,int target){
        char type=Character.toLowerCase(b[index]);int turn=side(b[index]);
        int r=index/8,c=index%8,tr=target/8,tc=target%8,dr=tr-r,dc=tc-c;
        if(type=='p')return dr==-turn&&Math.abs(dc)==1;
        if(type=='n')return (Math.abs(dr)==1&&Math.abs(dc)==2)||(Math.abs(dr)==2&&Math.abs(dc)==1);
        if(type=='k')return Math.max(Math.abs(dr),Math.abs(dc))==1;
        boolean diagonal=Math.abs(dr)==Math.abs(dc)&&dr!=0;
        boolean straight=(dr==0)!=(dc==0);
        if(!(type=='b'&&diagonal || type=='r'&&straight || type=='q'&&(diagonal||straight)))return false;
        int sr=Integer.compare(tr,r),sc=Integer.compare(tc,c),y=r+sr,x=c+sc;
        while(y!=tr||x!=tc){if(b[idx(y,x)]!='.')return false;y+=sr;x+=sc;}
        return true;
    }
    private static boolean threatened(char[] b,int target,int attacker){
        for(int from=0;from<64;from++)if(side(b[from])==attacker && attacks(b,from,target))return true;
        return false;
    }
    public boolean isCheck(int color){
        char king=color>0?'K':'k';int target=-1;
        for(int i=0;i<64;i++)if(board[i]==king){target=i;break;}
        return target<0||threatened(board,target,-color);
    }
    public static ChessPosition bootstrap(String placement,int turn){
        if(!valid(placement)||(turn!=1&&turn!=-1))return null;
        ChessPosition p=new ChessPosition(placement.toCharArray(),turn,0,-1,0,1);
        if(p.isCheck(-turn))return null;
        return p;
    }
    public static class Move {
        public final int from,to;public final char promo;public final boolean enPassant,castle;
        Move(int a,int b,char q,boolean ep,boolean cs){from=a;to=b;promo=q;enPassant=ep;castle=cs;}
        public String uci(){return square(from)+square(to)+(promo==0?"":String.valueOf(promo));}
    }
    public static String square(int id){return "abcdefgh".charAt(id%8)+String.valueOf(8-id/8);}
    private void add(ArrayList<Move> list,int from,int to,boolean ep,boolean cs){
        if(to<0||to>=64||Character.toLowerCase(board[to])=='k')return;
        char p=Character.toLowerCase(board[from]);
        if(p=='p'&&(to/8==0||to/8==7)){
            for(char promotion:new char[]{'q','r','b','n'})list.add(new Move(from,to,promotion,ep,cs));
        }else list.add(new Move(from,to,(char)0,ep,cs));
    }
    public ArrayList<Move> pseudo(){
        ArrayList<Move> out=new ArrayList<>();
        for(int from=0;from<64;from++){
            char piece=board[from];if(side(piece)!=turn)continue;
            char type=Character.toLowerCase(piece);int r=from/8,c=from%8;
            if(type=='p'){
                int destR=r-turn;if(inside(destR,c)&&board[idx(destR,c)]=='.'){
                    add(out,from,idx(destR,c),false,false);
                    int forward2=r-2*turn;
                    if((turn==1&&r==6||turn==-1&&r==1)&&board[idx(forward2,c)]=='.')add(out,from,idx(forward2,c),false,false);
                }
                for(int dx:new int[]{-1,1})if(inside(destR,c+dx)){
                    int target=idx(destR,c+dx);
                    if(side(board[target])==-turn)add(out,from,target,false,false);
                    else if(target==enPassant&&board[target]=='.'&&board[idx(r,c+dx)]==(turn==1?'p':'P'))add(out,from,target,true,false);
                }
            }else{
                int[][] dirs= type=='n'?new int[][]{{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}}:
                   type=='b'?new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}}:
                   type=='r'?new int[][]{{-1,0},{1,0},{0,-1},{0,1}}:
                   type=='q'?new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}}:
                   new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
                for(int[] d:dirs){int rr=r+d[0],cc=c+d[1];
                    while(inside(rr,cc)){
                        int to=idx(rr,cc);if(side(board[to])==turn)break;
                        add(out,from,to,false,false);
                        if(board[to]!='.'||type=='k'||type=='n')break;
                        rr+=d[0];cc+=d[1];
                    }
                }
                if(type=='k'&&from==(turn==1?60:4)&&!isCheck(turn)){
                    int kingside=turn==1?1:4,queenside=turn==1?2:8;
                    if((castle&kingside)!=0&&board[from+3]==(turn==1?'R':'r')&&board[from+1]=='.'&&board[from+2]=='.'&&
                        !threatened(board,from+1,-turn)&&!threatened(board,from+2,-turn))add(out,from,from+2,false,true);
                    if((castle&queenside)!=0&&board[from-4]==(turn==1?'R':'r')&&board[from-1]=='.'&&board[from-2]=='.'&&board[from-3]=='.'&&
                        !threatened(board,from-1,-turn)&&!threatened(board,from-2,-turn))add(out,from,from-2,false,true);
                }
            }
        }
        return out;
    }
    public ArrayList<Move> legal(){
        ArrayList<Move> out=new ArrayList<>();for(Move m:pseudo())if(!apply(m).isCheck(turn))out.add(m);return out;
    }
    private static int rookRights(int sq){switch(sq){case 63:return 1;case 56:return 2;case 7:return 4;case 0:return 8;default:return 0;}}
    public ChessPosition apply(Move m){
        char[] b=board.clone();char piece=b[m.from],captured=b[m.to];
        b[m.from]='.';b[m.to]=m.promo!=0?(turn==1?Character.toUpperCase(m.promo):m.promo):piece;
        if(m.enPassant)b[m.to+8*turn]='.';
        if(m.castle){int rookFrom=m.to>m.from?m.from+3:m.from-4,rookTo=m.to>m.from?m.from+1:m.from-1;b[rookTo]=b[rookFrom];b[rookFrom]='.';}
        int rights=castle;if(piece=='K')rights&=~3;if(piece=='k')rights&=~12;
        if(Character.toLowerCase(piece)=='r')rights&=~rookRights(m.from);
        if(Character.toLowerCase(captured)=='r')rights&=~rookRights(m.to);
        int ep=Character.toLowerCase(piece)=='p'&&Math.abs(m.to-m.from)==16?(m.to+m.from)/2:-1;
        int halfNext=Character.toLowerCase(piece)=='p'||captured!='.'||m.enPassant?0:half+1;
        return new ChessPosition(b,-turn,rights,ep,halfNext,full+(turn==-1?1:0));
    }
    /** Accept only a unique legal move (or two successive moves if one frame was skipped). */
    public ChessPosition track(String observed){
        if(!valid(observed))return null;
        if(placement().equals(observed))return this;
        ChessPosition match=null;
        for(Move m:legal()){
            ChessPosition next=apply(m);
            if(next.placement().equals(observed)){if(match!=null)return null;match=next;}
        }
        if(match!=null)return match;
        // A missed opponent+player pair can happen while the phone is busy.
        for(Move first:legal()){
            ChessPosition intermediate=apply(first);
            for(Move second:intermediate.legal()){
                ChessPosition next=intermediate.apply(second);
                if(next.placement().equals(observed)){
                    if(match!=null&&!match.fen().equals(next.fen()))return null;
                    match=next;
                }
            }
        }
        return match;
    }
    public String fen(){
        StringBuilder out=new StringBuilder();
        for(int row=0;row<8;row++){
            int gaps=0;for(int col=0;col<8;col++){
                char p=board[row*8+col];if(p=='.')gaps++;else{if(gaps>0){out.append(gaps);gaps=0;}out.append(p);}
            }
            if(gaps>0)out.append(gaps);if(row<7)out.append('/');
        }
        out.append(turn==1?" w ":" b ");
        if(castle==0)out.append('-');else{if((castle&1)!=0)out.append('K');if((castle&2)!=0)out.append('Q');if((castle&4)!=0)out.append('k');if((castle&8)!=0)out.append('q');}
        out.append(' ').append(enPassant<0?"-":square(enPassant)).append(' ').append(half).append(' ').append(full);
        return out.toString();
    }
    public static ChessPosition fromFen(String fen){
        try{
            String[] fields=fen.trim().split("\\s+");if(fields.length!=6)return null;
            char[] board=new char[64];Arrays.fill(board,'.');String[] rows=fields[0].split("/");if(rows.length!=8)return null;
            for(int r=0;r<8;r++){int c=0;for(char ch:rows[r].toCharArray()){
                if(ch>='1'&&ch<='8')c+=ch-'0';else{if(c>=8||"pnbrqkPNBRQK".indexOf(ch)<0)return null;board[r*8+c++]=ch;}
            }if(c!=8)return null;}
            if(!valid(new String(board)))return null;
            int turn=fields[1].equals("w")?1:fields[1].equals("b")?-1:0;if(turn==0)return null;
            int rights=0;if(fields[2].contains("K"))rights|=1;if(fields[2].contains("Q"))rights|=2;if(fields[2].contains("k"))rights|=4;if(fields[2].contains("q"))rights|=8;
            int ep=-1;if(!fields[3].equals("-")){String s=fields[3];if(s.length()!=2)return null;ep=(8-(s.charAt(1)-'0'))*8+(s.charAt(0)-'a');if(ep<0||ep>=64)return null;}
            ChessPosition pos=new ChessPosition(board,turn,rights,ep,Integer.parseInt(fields[4]),Integer.parseInt(fields[5]));
            return pos.isCheck(-turn)?null:pos;
        }catch(Exception e){return null;}
    }
}
