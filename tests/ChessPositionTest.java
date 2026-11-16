package de.chesspro.auto;
public class ChessPositionTest {
    static void assertIt(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static ChessPosition play(ChessPosition p,String move){
        for(ChessPosition.Move m:p.legal())if(m.uci().equals(move))return p.apply(m);
        throw new AssertionError("Illegal test move "+move+" in "+p.fen());
    }
    public static void main(String[] args){
        ChessPosition p=ChessPosition.start();
        assertIt(p.fen().equals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),"Start FEN");
        assertIt(p.legal().size()==20,"Start legal count");
        p=play(p,"e2e4");
        assertIt(p.turn==-1 && p.fen().contains(" b KQkq e3 0 1"),"Turn after e4");
        assertIt(ChessPosition.start().track(p.placement()).fen().equals(p.fen()),"track e4");
        ChessPosition q=play(p,"e7e5");
        assertIt(q.turn==1 && q.fen().contains(" w KQkq e6 0 2"),"Turn after e5");
        assertIt(p.track(q.placement()).fen().equals(q.fen()),"track e5");
        String[] sequence={"g1f3","b8c6","f1c4","g8f6","e1g1"};
        for(String s:sequence)q=play(q,s);
        assertIt(q.board[62]=='K'&&q.board[61]=='R',"castling rook shift");
        ChessPosition ep=ChessPosition.start();
        for(String s:new String[]{"e2e4","a7a6","e4e5","d7d5","e5d6"})ep=play(ep,s);
        assertIt(ep.board[27]=='.' && ep.board[19]=='P',"en passant capture");
        ChessPosition promo=ChessPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1");
        assertIt(promo!=null,"promotion FEN parse");
        ChessPosition end=play(promo,"a7a8q");assertIt(end.board[0]=='Q',"promotion queen");
        assertIt(ChessPosition.bootstrap(q.placement(),q.turn)!=null,"bootstrap midgame");
        System.out.println("ChessPosition: all 9 cases passed.");
    }
}
