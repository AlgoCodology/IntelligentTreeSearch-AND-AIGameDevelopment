	package main;

import app.StartDesktopApp;
import manager.ai.AIRegistry;
import mcts.ExampleDUCT;
import mcts.ExampleUCT;
import my_AI_Agents_For_Impasse.AI1;
import my_AI_Agents_For_Impasse.AI2;
import my_AI_Agents_For_Impasse.AI3;
import my_AI_Agents_For_Impasse.AI4;
import random.RandomAI;

/**
 * The main method of this launches the Ludii application with its GUI, and registers
 * the example AIs from this project such that they are available inside the GUI.
 *
 * @author Dennis Soemers
 */
public class LaunchLudii
{
	
	/**
	 * The main method
	 * @param args
	 */
	public static void main(final String[] args)
	{
		// Register our example AIs
		AIRegistry.registerAI("Example Random AI", () -> {return new RandomAI();}, (game) -> {return true;});
		AIRegistry.registerAI("Example UCT", () -> {return new ExampleUCT();}, (game) -> {return new ExampleUCT().supportsGame(game);});
		AIRegistry.registerAI("Example DUCT", () -> {return new ExampleDUCT();}, (game) -> {return new ExampleDUCT().supportsGame(game);});
		AIRegistry.registerAI("AI1", () -> {return new AI1();}, (game) -> {return true;}); // AI1 with minimax and alpha-beta pruning
		AIRegistry.registerAI("AI2", () -> {return new AI2();}, (game) -> {return true;}); // AI2 with ID, Move Ordering, TT and Enhanced Evaluation over AI1
		AIRegistry.registerAI("AI3", () -> {return new AI3();}, (game) -> {return true;}); // AI3 with Adaptive time control over AI2
		AIRegistry.registerAI("AI4", () -> {return new AI4();}, (game) -> {return true;}); // AI4 with Aspiration Window over AI3
		// Run Ludii
		StartDesktopApp.main(new String[0]);
	}

}
