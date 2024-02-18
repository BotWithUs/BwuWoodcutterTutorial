package net.botwithus;

import net.botwithus.api.game.hud.inventories.Backpack;
import net.botwithus.api.game.hud.inventories.Bank;
import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.events.impl.ChatMessageEvent;
import net.botwithus.rs3.events.impl.ServerTickedEvent;
import net.botwithus.rs3.game.Area;
import net.botwithus.rs3.game.Client;
import net.botwithus.rs3.game.Coordinate;
import net.botwithus.rs3.game.Item;
import net.botwithus.rs3.game.hud.interfaces.Component;
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery;
import net.botwithus.rs3.game.queries.builders.items.InventoryItemQuery;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.queries.results.ResultSet;
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer;
import net.botwithus.rs3.game.scene.entities.object.SceneObject;
import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;
import net.botwithus.rs3.util.Regex;

import java.util.Random;
import java.util.regex.Pattern;

public class SkeletonScript extends LoopingScript {

    private BotState botState = BotState.IDLE;
    private boolean someBool = true;
    private Random random = new Random();
    private Pattern woodboxPattern = Regex.getPatternForContainingOneOf("Wood box", "wood box");
    private Pattern logPattern = Regex.getPatternForContainingOneOf("Logs", "logs");
    private Area draynorBanks = new Area.Rectangular(new Coordinate(3090, 3239, 0), new Coordinate(3092, 3248, 0));

    enum BotState {
        //define your own states here
        IDLE,
        SKILLING,
        BANKING,
        //...

    }

    public SkeletonScript(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.sgc = new SkeletonScriptGraphicsContext(getConsole(), this);
    }

    @Override
    public boolean initialize() {
        super.initialize();
        subscribe(ChatMessageEvent.class, chatMessageEvent -> {
            //more events available at https://botwithus.net/javadoc/net.botwithus.rs3/net/botwithus/rs3/events/impl/package-summary.html
            println("Chatbox message received: %s", chatMessageEvent.getMessage());
        });
        return true;
    }

    @Override
    public void onLoop() {
        //Loops every 100ms by default, to change:
        //this.loopDelay = 500;
        LocalPlayer player = Client.getLocalPlayer();
        if (player == null || Client.getGameState() != Client.GameState.LOGGED_IN || botState == BotState.IDLE) {
            //wait some time so we dont immediately start on login.
            Execution.delay(random.nextLong(3000,7000));
            return;
        }

        switch (botState) {
            case IDLE -> {
                //do nothing
                println("We're idle!");
                Execution.delay(random.nextLong(1000,3000));
            }
            case SKILLING -> {
                //do some code that handles your skilling
                Execution.delay(handleSkilling(player));
            }
            case BANKING -> {
                //handle your banking logic, etc
                Execution.delay(handleBanking(player));
            }
        }
    }

    private long handleBanking(LocalPlayer player) {

        //println("Anim id: " + player.getAnimationId());
        println("Player moving: " + player.isMoving());
        if (player.isMoving()) {
            return random.nextLong(3000,5000);
        }

        if (Bank.isOpen()) {
            println("Bank is open!");

            ResultSet<Item> logsInBox = InventoryItemQuery.newQuery(937).results();
            if (logsInBox.stream().anyMatch(item -> item.getId() != -1)) {
                //deposit logs from box
                Component box = ComponentQuery.newQuery(517).componentIndex(15).option("Empty - logs and bird's nests").results().first();
                if (box != null) {
                    boolean success = box.interact("Empty - logs and bird's nests");
                    println("Deposited box contents: " + success);
                    if (success)
                        return random.nextLong(1500,3000);
                }
            } else {
                //either box was empty or we deposited
                println("Already deposited, skipping.");
            }

            Component logsForDeposit = ComponentQuery.newQuery(517).componentIndex(15).option("Deposit-All").results().first();
            if (logsForDeposit != null) {
                boolean success = logsForDeposit.interact("Deposit-All");
                println("Tried to deposit log: " + success);
                if (success) {
                    return random.nextLong(1500,3000);
                }
            }


            //we can go back to our skill state
            botState = BotState.SKILLING;
        } else {
            ResultSet<SceneObject> banks = SceneObjectQuery.newQuery().name("Counter").option("Bank").inside(draynorBanks).results();
            if (banks.isEmpty()) {
                println("Bank query was empty.");
            } else {
                SceneObject bank = banks.random();
                if (bank != null) {
                    println("Yay, we found our bank.");
                    println("Interacted bank: " + bank.interact("Bank"));
                }
            }
        }

        return random.nextLong(1500,3000);
    }

    private void fillBox(Item woodbox) {
        Component woodboxComp = ComponentQuery.newQuery(1473).componentIndex(5).itemName(woodbox.getName()).option("Fill").results().first();
        if (woodboxComp != null) {
            println("Filled woodbox: " + woodboxComp.interact("Fill"));
        }
    }

    private long handleSkilling(LocalPlayer player) {

        if (Backpack.isFull()) {

            Item woodbox = InventoryItemQuery.newQuery(93).name(woodboxPattern).results().first();
            if (woodbox == null || woodbox.getId() == -1) {
                println("We did not find our woodox, so we should bank.");
                botState = BotState.BANKING;
            } else {
                //we found our woodbox
                println("Yay, found found our woodbox: " + woodbox.getName());

                //TODO refactor this into a function so that its not repeated in handleBanking.
                //do calcs
                if (woodbox.getName() != null) {
                    int capacity = getBaseWoodboxCapacity(woodbox.getName());
                    capacity = capacity + getAdditionalWoodboxCapacity();
                    println("Our expected capacity is: " + capacity);
                    Item logs = InventoryItemQuery.newQuery(93).name(logPattern).results().first();
                    if (logs == null && logs.getId() == -1 && logs.getName() == null) {
                        println("No log found in inventory.");
                    } else {
                        //we found the log, and can proceed
                        Item logsStored = InventoryItemQuery.newQuery(937).name(logs.getName()).results().first();
                        if (logsStored == null || logsStored.getId() == -1) {
                            println("We didnt find logs in the woodbox, but we have one, so fill it.");
                        } else {
                            //good to finally fill if math maths
                            if (logsStored.getStackSize() >= capacity) {
                                //we cant fill, our woodbox is full, and we should actually bank
                                println("Moving to banking state");
                                botState = BotState.BANKING;
                                return random.nextLong(1500,3000);
                            }
                        }
                        //we can fill our box
                        Component woodboxComp = ComponentQuery.newQuery(1473).componentIndex(5).itemName(woodbox.getName()).option("Fill").results().first();
                        if (woodboxComp != null) {
                            println("Filled woodbox: " + woodboxComp.interact("Fill"));
                        }
                    }
                }
            }
            return random.nextLong(1500,3000);
        }

        println("Anim id: " + player.getAnimationId());
        println("Player moving: " + player.isMoving());
        if (player.getAnimationId() != -1 || player.isMoving()) {
            return random.nextLong(3000,5000);
        }

        SceneObject tree = SceneObjectQuery.newQuery().name("Willow").option("Chop down").hidden(false).results().nearest();
        if (tree != null) {
            println("Interacted tree: " + tree.interact("Chop down"));
        }

        return random.nextLong(1500,3000);
    }

    public int getAdditionalWoodboxCapacity() {
        int level = Skills.WOODCUTTING.getActualLevel();
        for (int threshold = 95; threshold > 0; threshold -= 10) {
            if (level >= threshold)
                return threshold + 5;
        }
        return 0;
    }

    public int getBaseWoodboxCapacity(String woodboxName) {
        switch (woodboxName) {
            case "Wood box":
                return 70;
            case "Oak wood box":
                return 80;
            case "Willow wood box":
                return 90;
            case "Teak wood box":
                return 100;
            case "Maple wood box":
                return 110;
            case "Acadia wood box":
                return 120;
            case "Mahogany wood box":
                return 130;
            case "Yew wood box":
                return 140;
            case "Magic wood box":
                return 150;
            case "Elder wood box":
                return 160;
        }
        return 0;
    }

    public BotState getBotState() {
        return botState;
    }

    public void setBotState(BotState botState) {
        this.botState = botState;
    }

    public boolean isSomeBool() {
        return someBool;
    }

    public void setSomeBool(boolean someBool) {
        this.someBool = someBool;
    }
}
