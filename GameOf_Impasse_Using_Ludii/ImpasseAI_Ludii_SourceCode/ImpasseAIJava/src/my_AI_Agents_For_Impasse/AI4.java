package my_AI_Agents_For_Impasse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import game.Game;
import main.collections.FastArrayList;
import other.AI;
import other.context.Context;
import other.location.Location;
import other.move.Move;
import other.state.State;
import other.trial.Trial;
import utils.data_structures.transposition_table.TranspositionTable;
import utils.data_structures.transposition_table.TranspositionTable.ABTTData;
import game.types.board.SiteType;

/**
 * Minimax AI for Ludii Game
 * 
 * @edited by Amit Jadhav - Referenced code by Dennis Soemers (AlphaBetaSearch.java)
 * and other resources from Ludii & LudiiAI repositories on Github
 */
public class AI4 extends AI
{
	protected int windowValue = 0; // This will be updated to the best score of the last depth inside a search
	protected int windowDelta = 18;
	/**Each Piece value is 10 in my code, a slide, transpose, bear-off, crown can yield between 1 to 7 score points
	So keeping a window of 18 assuming two rational players cannot damage each other's position
	or improve own position worth beyond a pieces and additional move points**/ 
	protected final boolean AspirationWindow = true;
	protected int player = -1;
	public int movesTillNow = 0; // to keep a count of moves for Adaptive Time Control on Iterative Deepening
	protected int maximisingPlayer = 0; // this will be overwritten in select action function
	protected int minimisingPlayer = 0; // this will be overwritten in select action function
	/** Current list of moves available in root */
	protected FastArrayList<Move> availableMoves = null;
	protected int ALPHA = -10000; // Lowest score bound at root
	protected int BETA = 10000; // Highest score bound at root
	protected int SCORE = ALPHA; // Current score of latest best move found
	private int maximumAllowedDepth = 200; // Value of maximum ply in case of infinite time and computational resources
	// The last move returned.
	protected Move chosenMove = null;
	// Transposition Table
	protected TranspositionTable transpositionTable = null;
	// String to print to Analysis tab of the Ludii app
	protected String analysisReport = null;
	// Estimated score of the root node based on last-run search
	protected int estimatedRootScore = 0;
	// If true at end of a search, it means we searched full tree
	protected boolean searchedFullTree = false;
	// Fast arraylist to store sorted list of moves
	protected FastArrayList<Move> sortedRootMoves = null;
	//Assuming a 10 Min per side AI game, this is set to 600 seconds before the start of the game
	public double remainingGameTime=600.0;
	protected int boardsEvaluated=0; // to keep track of positions reached before deciding on a move
	protected int totalPrunings=0; // to keep track of count of prunings made before deciding on a move

	/*Constructor*/
	public AI4()
	{
		this.friendlyName = "AI4";
	}	
	
	@Override
	public Move selectAction(final Game game,final Context context,final double secondsPerMove,final int maxIterations,final int maxDepth)
	{
		maximumAllowedDepth=15; // Go 15 ply if time permits
		final double moveStartTimer=System.currentTimeMillis();
		double moveTimeInSeconds = 0; // time allowed for this move
		// initial phase assuming an average game
		if (movesTillNow<=70 || remainingGameTime>=150) {moveTimeInSeconds=remainingGameTime/(85-movesTillNow);}
		// playing faster after realizing the game is lasting longer than normal
		else {moveTimeInSeconds=remainingGameTime/(100-movesTillNow);}
		System.out.println("AI Elapsed Time:"+(600-remainingGameTime));
		System.out.println("Time alloted for this move="+moveTimeInSeconds);
		System.out.println("Moves over: "+movesTillNow);
		final int startingDepth = 1;
		/** Since the selectAction method will be called only for our own AI player in its turn,
		 * its safe to initialise this player as the Maximising player,
		 * as this AI player will look to maximise the score for SELF. With similar logic and since it is a 2 player game,
		 * we choose the player with Next turn in current context as the minimising player
		 */
		maximisingPlayer = context.state().mover(); // Own AI player
		minimisingPlayer = 2 - (maximisingPlayer/2); // Opponent
		if (transpositionTable != null) // if transposition Table object is initiated, then allocate memory for it
			transpositionTable.allocate();
		chosenMove = moveChooserFunc(game, context,moveTimeInSeconds,maximumAllowedDepth,startingDepth,maximisingPlayer);
		if (transpositionTable != null)
		transpositionTable.deallocate(); // if transposition Table object is initiated, memory is likely allocated already,
		//as the call to moveChooser is over,so deallocate to free up memory
		remainingGameTime-=(System.currentTimeMillis()-moveStartTimer)/1000;
		movesTillNow++;
		return chosenMove;
	}
	public Move moveChooserFunc
	(	final Game game, 
		final Context context,
		final double secondsPerMove,
		final int maxDepth,
		final int startDepth,
		int maximisingPlayer) 
		{
			boardsEvaluated=0;totalPrunings=0; // initiate both to zero
			final long startTime = System.currentTimeMillis();
			long stopTime = (secondsPerMove > 0.0) ? startTime + (long) (secondsPerMove * 1000) : Long.MAX_VALUE;
			availableMoves = game.moves(context).moves();
			// Shuffling the moves to induce variation in game play in case of multiple equally valued moves
			final FastArrayList<Move> tempMovesList = new FastArrayList<Move>(availableMoves);
			sortedRootMoves = new FastArrayList<Move>(availableMoves.size());
			while (!tempMovesList.isEmpty()) // if there exists a move
			{
				sortedRootMoves.add(tempMovesList.removeSwap(ThreadLocalRandom.current().nextInt(tempMovesList.size())));
			}
			final int totalInitialMoves = sortedRootMoves.size();
			final List<MoveOrderList> moveOrderingMovesList = new ArrayList<MoveOrderList>(sortedRootMoves.size());
			if (totalInitialMoves==1)
			{	/* in case of a single move available at ROOT,
				then AI has to play that move without any further search as no other option! */
				stopTime = startTime + (long) (0.001 * 1000);
			}
			// Storing scores found for purpose of move ordering
			final moveScoreList moveScores = new moveScoreList(totalInitialMoves);
			final int depthReductionStep = 1;
//			int searchDepth = startDepth - depthReductionStep;
			int searchDepth = 0;
			// Best move found so far during a fully-completed search (ignoring incomplete early-terminated search)
			Move bestMoveCompleteSearch = sortedRootMoves.get(0);
			while (searchDepth < maxDepth)
			{	searchDepth += depthReductionStep;
				searchedFullTree = true;
//				System.out.println("SEARCHING TO DEPTH: " + searchDepth);
				ALPHA = -10000;BETA = 10000;
				SCORE = ALPHA;
				int windowAlpha, windowBeta; // to maintain the alpha & beta bounds after considering the window
				int ABscore=0;
				// best move during search at this depth
				Move bestMove = sortedRootMoves.get(0);
				for (int i = 0; i<totalInitialMoves;i++)
				{
					final Context tempContext = copyContext(context);
					final Move candidateMove = sortedRootMoves.get(i);
					game.apply(tempContext, candidateMove);
					if (AspirationWindow) // if aspiration window function is enabled
					{
						windowAlpha=windowValue-windowDelta;
						windowBeta=windowValue+windowDelta;
						ABscore=minimaxAlphaBeta(tempContext, searchDepth-1, windowAlpha, windowBeta, maximisingPlayer,stopTime);
						if (ABscore>=(windowBeta))
						{
							System.out.println("Fail High at depth: "+(searchDepth-1)+" so shifting up the window");
							ABscore=minimaxAlphaBeta(tempContext, searchDepth-1, ABscore,10000, maximisingPlayer,stopTime);
						}
						else if (ABscore<=(windowAlpha))
						{
							System.out.println("Fail High at depth: "+(searchDepth-1)+" so shifting down the window");
							ABscore=minimaxAlphaBeta(tempContext, searchDepth-1, -10000, ABscore, maximisingPlayer,stopTime);
						}
					}
					else
					{
						ABscore = minimaxAlphaBeta(tempContext, searchDepth-1, ALPHA, BETA, maximisingPlayer,stopTime);
					}
					if (System.currentTimeMillis() >= stopTime || wantsInterrupt)
					//time per move crossed -> canceling search
					{	bestMove = null;
							break;}
					moveScores.set(i, ABscore);
					if (ABscore > SCORE)
					{	SCORE = ABscore;
						bestMove = candidateMove;}
					ALPHA = Math.max(ALPHA, SCORE);
					if (ALPHA >= BETA)		// beta cut-off
					{
						totalPrunings++;
						break;}
				}
				if (bestMove != null)		// search completed at current depth
				{
					estimatedRootScore = SCORE; // to save the score of previous depth and return it in case of time run out
					windowValue=SCORE; // updating the score estimate for Aspiration Window based on the best score for previous depth
					if (SCORE == 10000)
					{	return bestMove;}
					else if (SCORE == -10000)
					{	return bestMoveCompleteSearch;}
					else if (searchedFullTree)
					{	// We've searched full tree but did not see a winning or losing position
						return bestMove;}
					bestMoveCompleteSearch = bestMove;
				}
				else
				{	// reducing the depth as search till current depth cannot complete in alloted time for the move
					searchDepth -= depthReductionStep;}
				if (System.currentTimeMillis() >= stopTime || wantsInterrupt)
				{
					// return best move up to last completed depth as time over
					analysisReport = friendlyName + " completed search of depth " + searchDepth + 
							" with score:"+estimatedRootScore+". Searched:"+boardsEvaluated+" positions, and pruned another "+totalPrunings+" of them.";
					return bestMoveCompleteSearch;
				}
				// order moves based on scores found, for next search at next ply of earlier search depth
				moveOrderingMovesList.clear();
				for (int i = 0; i < totalInitialMoves; ++i)
				{
					moveOrderingMovesList.add(new MoveOrderList(sortedRootMoves.get(i), moveScores.get(i)));
				}
				Collections.sort(moveOrderingMovesList);
				
				sortedRootMoves.clear();
				for (int i = 0; i < totalInitialMoves; ++i)
				{
					sortedRootMoves.add(moveOrderingMovesList.get(i).move);
				}
				
				// clear the vector of scores
				moveScores.fill(0, totalInitialMoves, 0);
			}
			System.out.println("Playing from:"+bestMoveCompleteSearch.from()+ " to:"+bestMoveCompleteSearch.to()+" with score :"+SCORE );
		return bestMoveCompleteSearch;
		}
	public int minimaxAlphaBeta
		(	final Context currContext,
			final int depth,
			final int inAlpha,
			final int inBeta,
			final int player,
			final long stopTime)
		{
			final Game game = currContext.game();
			final Trial trial = currContext.trial();
			final State state = currContext.state();
			final float originalAlpha = inAlpha;
			int alpha = inAlpha;
			int beta = inBeta;
			final long zobrist = state.fullHash(currContext);
			final ABTTData tableData;
			final int mover = state.mover();
			if (transpositionTable != null)
			{
				tableData = transpositionTable.retrieve(zobrist);
				
				if (tableData != null)
				{
					if (tableData.depth >= depth) //if TT recommendation is deeper searched than current alpha beta call
					{
						// Already searched deep enough for data in TT, using results from TT
						switch (tableData.valueType)
						{
						case TranspositionTable.EXACT_VALUE:
						{
							boardsEvaluated++;
							return (int)tableData.value;
						}
						case TranspositionTable.LOWER_BOUND:
							alpha = Math.max(alpha, (int)tableData.value);
							break;
						case TranspositionTable.UPPER_BOUND:
							beta = Math.min(beta, (int)tableData.value);
							break;
						default:
							System.err.println("INVALID TRANSPOSITION TABLE DATA!");
							break;
						}
						
						if (alpha >= beta)
						{
							boardsEvaluated++;
							totalPrunings++;
							return (int)tableData.value;
						}
					}
				}
			}
			else
			{
				tableData = null;
			}
			if (trial.over() || !currContext.active(maximisingPlayer))
			{
				// terminal node (at least for maximizing player)
				return (int) 10000;
			}
			else if (depth == 0)
			{
				searchedFullTree = false;
				int PTMscore = 3; // Player-to-move in the leaf state gets +3 score and so effectively -3 for non-mover
				/**Any combination of slide/transpose + bear off / crown can yield between 1 and 7 score-points
				as per my evaluation logic, so giving a conservative advantage of 3 score-points to the mover **/ 
				int score=evaluate(currContext)+PTMscore;
				boardsEvaluated++;
				if (currContext.winners().contains(minimisingPlayer))
				{
					score= (int) -10000;
				}
				if (mover != maximisingPlayer)
				{score = -score;}
				return score;
			}
			FastArrayList<Move> furtherAvailableMoves = game.moves(currContext).moves();
			final int legalMovesCount = furtherAvailableMoves.size();
			if (tableData != null) // if the position exists in TT, (NOTE:this would be at a lower depth than current search),
				//even then the score and score type is valuable information to save search effort so reusing the result..
			{
				//TT's best move is prioritized and tried first over the other ordered moves. 
				final Move transpositionBestMove = tableData.bestMove;
				furtherAvailableMoves = new FastArrayList<Move>(furtherAvailableMoves);	// Copying before reordering basis TT move
				/* effectively swapping second best move to TT move's index while preserving the first ordered move
				 * (probably also a very strong candidate move) from previous depth immediately after TT best move */
				for (int i = 0; i < legalMovesCount; ++i)
				{
					if (transpositionBestMove.equals(furtherAvailableMoves.get(i)))
					{
						if(legalMovesCount<=2)
						{final Move temp = furtherAvailableMoves.get(0);
						furtherAvailableMoves.set(0, furtherAvailableMoves.get(i));
						furtherAvailableMoves.set(i, temp);
						break;}
						else if (legalMovesCount>2)
						{
						final Move temp = furtherAvailableMoves.get(0);
						furtherAvailableMoves.set(0, furtherAvailableMoves.get(i));
						furtherAvailableMoves.set(i, temp);
						final Move temp1 = furtherAvailableMoves.get(1);
						furtherAvailableMoves.set(1, furtherAvailableMoves.get(i));
						furtherAvailableMoves.set(i, temp1);}
						break;
					}
				}
			}
			Move bestMove = furtherAvailableMoves.get(0);
//			System.out.println("Mover:"+mover+" MaxPlayer:"+maximisingPlayer+" at depth "+(6-depth));
//			System.out.println("WINNERS: "+currContext.winners());
			if (mover == maximisingPlayer)
			{
//				System.out.println("Eval AI");
				int score = -10000;
				for (int i = 0; i < furtherAvailableMoves.size(); i++)
				{
					final Context minimaxContext = copyContext(currContext);
					final Move ABMove = furtherAvailableMoves.get(i);
					game.apply(minimaxContext, ABMove);
					final int value = minimaxAlphaBeta(minimaxContext, depth - 1, alpha, beta, maximisingPlayer,stopTime);
					if (System.currentTimeMillis() >= stopTime || wantsInterrupt)	// time over, cancel search and return 0 score
					{
						return 0;
					}
					if (value > score)
					{
						bestMove = ABMove;
						score = value;
					}
					alpha = Math.max(alpha,score);
					if (alpha >= beta)	// beta cut-off
						{	totalPrunings++;
							break;}
				}
				if (transpositionTable != null)
				{
					// Store data in transposition table
					if (score <= originalAlpha)		// Found upper bound
						transpositionTable.store(bestMove, zobrist, score, depth, TranspositionTable.UPPER_BOUND);
					else if (score >= beta)			// Found lower bound
						transpositionTable.store(bestMove, zobrist, score, depth, TranspositionTable.LOWER_BOUND);
					else							// Found exact value
						transpositionTable.store(bestMove, zobrist, score, depth, TranspositionTable.EXACT_VALUE);
				}
				return score;
			}
			else 
			{
//				System.out.println("Eval Opp");
				int score = 10000;
				for (int i = 0; i < legalMovesCount; i++)
				{					
					final Context minimaxContext = copyContext(currContext);
					final Move ABMove = furtherAvailableMoves.get(i);
					game.apply(minimaxContext, ABMove);
					final int value = minimaxAlphaBeta(minimaxContext, depth - 1, alpha, beta, maximisingPlayer,stopTime);
					if (System.currentTimeMillis() >= stopTime || wantsInterrupt)	// time to abort search
					{
						return 0;
					}
					if (value < score)
					{
						bestMove = ABMove;
						score = value;
					}
					if (score < beta)
						beta = score;
					if (alpha >= beta)	// beta cut-off
						{	totalPrunings++;
							break;}
				}
				if (transpositionTable != null)
				{
					// Store data in transposition table
					if (score <= originalAlpha)		// Found upper bound
						transpositionTable.store(bestMove, zobrist, score, depth, TranspositionTable.UPPER_BOUND);
					else if (score >= beta)			// Found lower bound
						transpositionTable.store(bestMove, zobrist, score, depth, TranspositionTable.LOWER_BOUND);
					else							// Found exact value
						transpositionTable.store(bestMove, zobrist, score, depth, TranspositionTable.EXACT_VALUE);
				}
				return score;
			}
		}
	public static int evaluate(Context context) 
	{	// Collecting data about all own and opponent occupied sites on Board
		final State state = context.state();
		final List<? extends Location>[] OwnPieces = state.owned().positions(state.mover());
		final List<? extends Location>[] EnemyPieces = state.owned().positions((int)(2-(state.mover()/2)));
		ArrayList<Integer> allOwnSites = new ArrayList<Integer>(1);
		ArrayList<Integer> allEnemySites = new ArrayList<Integer>(1);
		
		for (int i1=0;i1< OwnPieces.length;i1++)
		{if (OwnPieces[i1].isEmpty())continue;
			for (final Location pos1 : OwnPieces[i1]){
				if (pos1.siteType() != SiteType.Cell || context.containerId()[pos1.site()] == 0)
				{	allOwnSites.add(pos1.site());}}}
		for (int i2=0;i2< EnemyPieces.length;i2++)
		{if (EnemyPieces[i2].isEmpty())continue;
			for (final Location pos2 : EnemyPieces[i2]){
				if (pos2.siteType() != SiteType.Cell || context.containerId()[pos2.site()] == 0)
				{	allEnemySites.add(pos2.site());}}}
		
	    //Computing count of own and opponent pieces to further calculate material advantage
		int ownPiecesCount=allOwnSites.size(),enemyPiecesCount=allEnemySites.size();
	    
		// re-arranging piece x square location list to piece x count (single / double stack) x square location list
		Object[] obj_OwnArray= allOwnSites.toArray();
	    int[] OwnArray=new int[allOwnSites.size()];
	    for(int i=0;i<obj_OwnArray.length;i++)
	    {OwnArray[i]=(int)obj_OwnArray[i];}
	    List<Integer> aList1 = IntStream.of(OwnArray).boxed().collect(Collectors.toCollection(ArrayList::new));
	    Set<Integer> mySet1 = new HashSet<Integer>(aList1);
	    int[][] valuesFrequencies1 = new int[mySet1.size()][2];
	    int index1 = 0;   
	    for(int s: mySet1){
	    	valuesFrequencies1[index1][0] = s;
	    	valuesFrequencies1[index1][1] = Collections.frequency(aList1,s);
	    	index1++;}
	    
	    Object[] obj_EnemyArray= allEnemySites.toArray();
	    int[] EnemyArray=new int[allEnemySites.size()];
	    for(int i=0;i<obj_EnemyArray.length;i++)
	    {EnemyArray[i]=(int)obj_EnemyArray[i];}
	    List<Integer> aList2 = IntStream.of(EnemyArray).boxed().collect(Collectors.toCollection(ArrayList::new));
	    Set<Integer> mySet2 = new HashSet<Integer>(aList2);
	    int[][] valuesFrequencies2 = new int[mySet2.size()][2];
	    int index2 = 0;
	    for(int s: mySet2){
	    	valuesFrequencies2[index2][0] = s;
	    	valuesFrequencies2[index2][1] = Collections.frequency(aList2,s);
	    	index2++;}
	    
	    int ownPos=0; // Self - positional score (penalty)
	    int oppPos=0; // Opponent - positional score (penalty)
	    int materialAdv=0; // material advantage --> self pieces - opponent pieces
	  /**
	   * loosing own piece from the board earns 10 weight points (a)
	   * improving position by one square earns 1 weight points, (b)
	   * blocking an opponent by one square earns 1 weight point, (c)
	   * NOTE: posScore and blockScore are penalties and thus computed
	   * as negative by default and here 'a','b','c' are only unsigned scaling factors
	   */
	    int a = 10, b = 1, c = 1; 

	    // assigning scores basis row numbers and piece types
	    //(White - Single, White - Double, Black - Single, Black - Double) on the board 
	    for (int i=0;i<valuesFrequencies1.length;i++) 
	    {
	    if (state.mover()==1) // white player is evaluating for self
	    		{if(valuesFrequencies1[i][1]==1) // own single
	    		{ownPos+=(14-valuesFrequencies1[i][0]/8);}
	       		else {ownPos+=(14+valuesFrequencies1[i][0]/8);}} // own double
	    // black player is evaluating for self	
	    else {
	    	if(valuesFrequencies1[i][1]==1) // own single
	    	{ownPos+=(7+valuesFrequencies1[i][0]/8);}
     		else{ownPos+=(21-valuesFrequencies1[i][0]/8);}}} //own double
	    for (int i=0;i<valuesFrequencies2.length;i++) 
	    {  	if ((2-state.mover()/2)==1) // black player is evaluating for its opponent i.e. white player
	    		{if(valuesFrequencies2[i][1]==1) // opponent's (white's single)
	    		{oppPos+=(14-valuesFrequencies2[i][0]/8);}
	       		else {oppPos+=(14+valuesFrequencies2[i][0]/8);}} //opponent's (white's double)
     		else // white player is evaluating for its opponent i.e. black player
     		{if(valuesFrequencies2[i][1]==1)  // opponent's (black's single)
     		{oppPos+=(7+valuesFrequencies2[i][0]/8);}
     		else{oppPos+=(21-valuesFrequencies2[i][0]/8);}}} //opponent's (black's double)

	    materialAdv = (enemyPiecesCount-ownPiecesCount); // material advantage
	    int finalScore;
	    final int rscore = ThreadLocalRandom.current().nextInt(-1,2); // Random score - points between -1 / 0 / 1
	    // random score is to play in favor or promisingly strong branches over very few super strong moves 
	    if (materialAdv>=4) // play greedy if currently 4 pieces UP!!
	    {finalScore=a*materialAdv-(b+1)*ownPos;}
	    else // play with a balance of greed and caution if not in a too strong position!!
	    {finalScore= a*materialAdv-b*ownPos+c*oppPos+rscore;}
	    ownPiecesCount=0; enemyPiecesCount=0;		
     return finalScore;
	}	
	@Override
	public void initAI(final Game game, final int playerID) {
		this.player = playerID;
		estimatedRootScore = 0;
		analysisReport = null;
		availableMoves = null;
		chosenMove = null;
		transpositionTable = new TranspositionTable(20);
	}
//	@Override
	public String generateAnalysisReport()
	{
		return analysisReport;
	}
	
	protected class moveScoreList implements Serializable
	{
		/** */
		private static final long serialVersionUID = 1L;
		protected final int[] scoreArray;
		public moveScoreList(final int len)
		{
			scoreArray=new int[len];
		}
		public void set(final int index, final int score)
		{
			scoreArray[index] = score;
		}
		public int get(final int index)
		{
			return scoreArray[index];
		}
		public void fill(final int startIndex,final int indexafterEndIndex,final int score)
		{
			Arrays.fill(scoreArray, startIndex, indexafterEndIndex, score);
		}
	}
	protected class MoveOrderList implements Comparable<MoveOrderList>
	{
		/** The move */
		public final Move move;
		/** The move's score */
		public final int score;
		
		// Constructor
		public MoveOrderList(final Move move, final int score)
		{	this.move = move;
			this.score = score;}

		@Override
		public int compareTo(final MoveOrderList other)
		{	final int difference = other.score - score;
			if (difference < 0) return -1;
			else if (difference > 0)	return 1;
			else return 0;}
	}
}