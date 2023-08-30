package random;

import java.util.concurrent.ThreadLocalRandom;
import game.Game;
import main.collections.FastArrayList;
import other.AI;
import other.context.Context;
import other.move.Move;
import utils.AIUtils;
/**
 * Example third-party implementation of a random AI for Ludii
 * 
 * @author Dennis Soemers
 */

public class RandomAI extends AI
{
	//-------------------------------------------------------------------------
	/** Our player index */
	protected int player = -1;
	//-------------------------------------------------------------------------
	/**
	 * Constructor
	 */
	public RandomAI()
	{
		this.friendlyName = "Example Random AI";
	}
	//-------------------------------------------------------------------------

	@Override
	public Move selectAction
	(final Game game, final Context context, final double maxSeconds, final int maxIterations, final int maxDepth)
	{	FastArrayList<Move> legalMoves = game.moves(context).moves();	
		// If we're playing a simultaneous-move game, some of the legal moves may be 
		// for different players. Extract only the ones that we can choose.
		if (!game.isAlternatingMoveGame())
			legalMoves = AIUtils.extractMovesForMover(legalMoves, player);
		final int r = ThreadLocalRandom.current().nextInt(legalMoves.size());
		return legalMoves.get(r);
	}
	/**
	public static void nodeExpander(Node current)
	{	for(int i = 0; i < current.unexpandedMoves.size();)
		{Move move = current.unexpandedMoves.remove(i);
		Context context = new Context(current.context);
		context.game().apply(context, move);
		new Node(current, move, context);}}
	**/
	@Override
	public void initAI(final Game game, final int playerID)
	{
		this.player = playerID;
	}
	
	//-------------------------------------------------------------------------
	/**
	 * 
		private static class Node {
		// Our parent node
		private final Node parent;
		
		// The move that led from parent to this node
		private final Move moveFromParent;
		
		// This objects contains the game state for this node (this is why we don't support stochastic games)
		private final Context context;
		
		// Visit count for this node
		private int visitCount = 0;
		
		// For every player, sum of utilities / scores backpropagated through this node
		private final double[] scoreSums;
		
		// Child nodes
		private final List<Node> children = new ArrayList<Node>();
		
		// List of moves for which we did not yet create a child node
		private final FastArrayList<Move> unexpandedMoves;
		
		public Node(final Node parent, final Move moveFromParent, final Context context) {
			this.parent = parent;
			this.moveFromParent = moveFromParent;
			this.context = context;
			final Game game = context.game();
			scoreSums = new double[game.players().count() + 1];
			
			// For simplicity, we just take ALL legal moves. 
			// This means we do not support simultaneous-move games.
			unexpandedMoves = new FastArrayList<Move>(game.moves(context).moves());
			
			if (parent != null)
				parent.children.add(this);
		}
		
	}
**/
}
