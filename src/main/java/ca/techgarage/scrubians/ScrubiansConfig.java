package ca.techgarage.scrubians;

import ca.techgarage.bscm.Comment;

public class ScrubiansConfig {

    @Comment("Distence NPCs will look at you from {int}")
    public static int NPCLookDistence = 8;

    @Comment("Speed NPC will turn its head {int}")
    public static int NPCHeadSpeed = 10;

    @Comment("default time NPC will wait for {int [min: 0]}")
    public static int defaultNPCWaitTimeInTicks = 20;

    @Comment("Display something at all times on right-click")
    public static boolean npcHasNothingtoSayMessage = true;

}
