package my_AI_Agents_For_Impasse;

import game.Game;
import main.collections.FastArrayList;
import other.AI;
import other.context.Context;
import other.move.Move;
import other.state.State;

public class AI1 extends AI
{
	protected int player = -1;
	protected int maximisingPlayer = 0; // this will be overwritten in select action function
	protected int minimisingPlayer = 0; // this will be overwritten in select action function
	protected final FastArrayList<Move> availableMoves = null; // all the moves available at root
	protected int ALPHA = -10000; // Lowest score bound at root
	protected int BETA = 10000; // Highest score bound at root
	protected int SCORE = ALPHA; // Current score of latest best move found
	/*Constructor*/
	public AI1()
	{
		this.friendlyName = "AI1";
	}	
	
	@Override
	public Move selectAction(
			final Game game,
			final Context context,
			final double maxSeconds, 
			final int maxIterations, 
			final int maxDepth)
	{

//		final int startDepth = 1;
		int chosenDepth =4; // This AI is Pessimistic and checks upto 4 ply deep
		//with opponent's move being the last move after which boards are evaluated
		
		/** Since the selectAction method will be called only for our own AI player in its turn,
		 * its safe to initialise this player as the Maximising player,
		 * as this AI player will look to maximise the score for SELF. With similar logic and since it is a 2 player game,
		 * we choose the player with Next turn in current context as the minimising player
		 */
		maximisingPlayer = context.state().mover(); // Own AI player
		minimisingPlayer = 2 - (maximisingPlayer/2); // Opponent
		Move chosenMove;
		chosenMove = moveChooserFunc(game, context,chosenDepth,maximisingPlayer);
		
//		System.out.println("Self => P"+ maximisingPlayer +" & Opp => P"+minimisingPlayer);
//		System.out.println("Own Sites: "+ context.state().owned().sites(context.state().mover()));
//		System.out.println("Opponent Sites after own turn:"+context.state().owned().sites(context.state().next()));
		return chosenMove;
	}
	public Move moveChooserFunc
	(	final Game game, 
		final Context context, 
		final int maxDepth,
		int maximisingPlayer) 
	{	
		FastArrayList<Move> availableMoves = new FastArrayList<Move>(game.moves(context).moves());
		
		if (availableMoves.size()==1)
		{
			return availableMoves.get(0); // the only move available is returned without any further search if applicable
		}
		ALPHA = -10000;
		SCORE = ALPHA;
		BETA = 10000;
		Move chosenMove=null;
		// To deal with the scenario where player has no moves - NOTE: Impasse is also a move at this layer
		if (availableMoves.isEmpty()) 
		{ System.out.println("ALERT!!! NO MOVES AT ROOT!!!");}
		
		// Going through all the moves at root to identify best candidate move
		int alphaBetaValue=0,backupScore=BETA;
		for (int i = 0; i<availableMoves.size();i++)
		{
			final Context tempContext = copyContext(context);
			final Move candidateMove = availableMoves.get(i);
//			System.out.println("Trying Move "+(i+1)+" at root");
			game.apply(tempContext, candidateMove);
			if (tempContext.winners().size()==0) // if there are no winners, look deeper
			{
				alphaBetaValue = ab_Minimax(tempContext, maxDepth-1, ALPHA, BETA, maximisingPlayer);
			}
			else
			{
				if (tempContext.winners().contains(maximisingPlayer)) // AI Wins after playing a move
				{
					System.out.println("AI won, congrats!");
					return candidateMove;
				}
				else if (tempContext.winners().contains(minimisingPlayer)) // Opponent AI / Human wins after playing a move
				{
					System.out.println("AI lost, Better luck next Time!!");
					return candidateMove;					
				}
			}
			if (alphaBetaValue > SCORE)
			{
				SCORE = alphaBetaValue; // best score found for AI
				backupScore=alphaBetaValue; // A trace of the best score is stored for displaying
				chosenMove = candidateMove; // best move found for AI
			}
			if (SCORE > ALPHA)		// new lower bound
				ALPHA = SCORE;		
			if (ALPHA >= BETA)		// beta cut-off - Deep Pruning
				break;
		}
		
		System.out.println("Playing with score : "+backupScore+
				" from: "+(chosenMove==null?"NULL":chosenMove.from())+
				" to: "+(chosenMove==null?"NULL":chosenMove.to()));
		return chosenMove;
	}
	// Alpha - Beta pruning applied to Minimax function
	public int ab_Minimax
		(
			Context currContext,
			int depth,
			int alpha,
			int beta,
			int player
		)
		{
			final Game game = currContext.game();
			final State state = currContext.state();
			FastArrayList<Move> furtherAvailableMoves = game.moves(currContext).moves();
//			System.out.println(furtherAvailableMoves);
//			final Trial trial = currContext.trial();
//			if (furtherAvailableMoves.get(0).isPass());
			// return highest possible score based on whether AI has no moves or Opponent has no moves at this depth
			if (furtherAvailableMoves.isEmpty()) 
			{
				System.out.println("ALERT!!! NO MOVES AT DEPTH "+(4-depth)+"!!!");
				return (state.mover()==maximisingPlayer?beta:alpha);}
			else 
			{
			final int mover = state.mover();
//			System.out.println("Mover:"+mover+" MaxPlayer:"+maximisingPlayer+" at depth "+(4-depth));
			int score=0;
			// If maximum depth i.e. 4 ply deep reached, then evaluate the leaf nodes
			if (depth ==0) //|| currContext.winners().size()!=0
			{
				score = evaluate(currContext);
				// Invert score for minimizing player
				if (mover != maximisingPlayer)
					{score = -score;}
				return score;
			}
			if (mover == maximisingPlayer) // if AI is the current player to move and max depth not yet reached,
				//expand further for all moves of opponent
			{
//				System.out.println("Eval AI");
				score = alpha;
				for (int i = 0; i < furtherAvailableMoves.size(); i++)
				{
					final Context minimaxContext = copyContext(currContext); // using the predefined copy context method in ludii
					//to preserve the original context before applying the move to support backtracking afterwards
					final Move ABMove = furtherAvailableMoves.get(i);
					game.apply(minimaxContext, ABMove);
					final int value = ab_Minimax(minimaxContext, depth - 1, alpha, beta, maximisingPlayer);
					// calling alpha beta for the opponent here to score its moves as Min player 
					if (value > score){score = value;}
					if (score > alpha) {alpha = score;}
					if (alpha >= beta) {break;}	// beta cut-off
				}
			}
			else 
			{	// if Opponent is the current player to move and max depth not yet reached,
				//expanding further for all moves of AI
//				System.out.println("Eval Opp");
				score = beta;
				for (int i = 0; i < furtherAvailableMoves.size(); i++)
				{
					final Context minimaxContext = copyContext(currContext);
					final Move ABMove = furtherAvailableMoves.get(i);
					game.apply(minimaxContext, ABMove);
					// calling alpha beta for the AI here to score its moves as Max player
					final int value = ab_Minimax(minimaxContext, depth - 1, alpha, beta, maximisingPlayer);
					if (value < score){score = value;}
					if (score < beta) {beta = score;}
					if (alpha >= beta) {break;}// beta cut-off				
				}}
			return score;}
		}
	public int evaluate(Context context) 
	{
//		Random random = new Random();
//		int random1 = random.nextInt(100);
//		return random1;
//		Game game = context.game();
		final State state = context.state();
//		FastArrayList<Move> movesToEvaluate = game.moves(context).moves();
//		System.out.println(state.mover()==1? "Evaluating for White & Printing all movable piece-site details..":
//			"Evaluating for Black & Printing all movable piece-site details..");
//		System.out.println(" Occupied squares: "+state.owned().sites(state.mover()));
//		for (int i=0;i<movesToEvaluate.size();i++)
//		{
//			System.out.println("Move "+i+" - From:"+movesToEvaluate.get(i).from()+
//					" To:"+movesToEvaluate.get(i).to()+" & Stack size:"+movesToEvaluate.get(i).levelFrom());
//		}
		int positionscore=0;
		int blockadescore=0;
		if (state.mover()==1)
		{
			positionscore = -(state.owned().sites(1).sum()); // simple position score of mover
			blockadescore =  -(state.owned().sites(2).sum()); // simple position score of the opponent --> blockade score
		}
		else
		{
			positionscore = state.owned().sites(2).sum(); // simple position score of mover
			blockadescore = state.owned().sites(1).sum(); // simple position score of the opponent --> blockade score
		}
		int mobilityScore = context.game().moves(context).count(); // mobility of the current mover
		mobilityScore=(int)mobilityScore/10; // down-scaling the mobility score
		return (int)3*positionscore+blockadescore+mobilityScore;
	}
	
	@Override
	public void initAI(final Game game, final int playerID) {
		this.player = playerID;
	}
	
}