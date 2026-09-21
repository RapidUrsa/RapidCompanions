package com.rapidursa.companions;

import com.rapidursa.companions.dialog.DialogNode;
import com.rapidursa.companions.dialog.FakeDialogManager;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.api.model.Jarvis;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

import static com.rapidursa.companions.PetObjectModel.radToJau;
import static net.runelite.api.Perspective.COSINE;
import static net.runelite.api.Perspective.SINE;

@Slf4j
@PluginDescriptor(
	name = "Rapid Companions",
	description = "Choose a client-side companion with reliable, adjustable following.",
	tags = {"pet", "companion", "follower", "cosmetic"}
)
public class RapidCompanionsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private RapidCompanionsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RapidCompanionsOverlay overlayPet;

	@Inject
	private FakeDialogManager fakeDialogManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Hooks hooks;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private CompanionToggleOverlay companionButton;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Override
	protected void startUp() throws Exception
	{
		buildSidePanel();
		overlayManager.add(overlayPet);
		overlayManager.add(companionButton);
		mouseManager.registerMouseListener(companionButton);
		eventBus.register(fakeDialogManager);
		hooks.registerRenderableDrawListener(drawListener);
		chatCommandManager.registerCommand("!mochi",this::setPetMochi);

	}


	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlayPet);
		mouseManager.unregisterMouseListener(companionButton);
		overlayManager.remove(companionButton);
		eventBus.unregister(fakeDialogManager);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		hooks.unregisterRenderableDrawListener(drawListener);
		if (panel != null)
		{
			panel.removeAll();
		}

		clientThread.invokeLater(this::despawnAnyActivePOMs);
		chatCommandManager.unregisterCommand("!mochi");


	}



	@Provides
	RapidCompanionsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RapidCompanionsConfig.class);
	}

	private int lastActorOrientation;

	private boolean dialogOpen;
	private boolean petFollowing = false;
	private boolean petEnterHouse;
	private int combatFleeTicks;
	private Actor lastCombatOpponent;
	private int blockedFollowTicks;

	public PetObjectModel pet = new PetObjectModel();

	public CompanionData petData;

	private WorldPoint lastPlayerWP;
	private WorldPoint lastActorWP;

	public WorldArea nextTravellingPoint;
	public WorldArea petWorldArea = null;

	private Model petModel;

	private final List<WorldPoint> prevPlayerWPs = new ArrayList<>();

	private final HashMap<NPC,PetObjectModel> activeThralls = new HashMap<>();

	public SimplePolygon petPoly;

	private RapidCompanionsPanel panel;

	private NavigationButton navButton;

	private final static String CONFIG_GROUP = "rapidCompanions";

	private final static List<Integer> MELEE_THRALLS = Arrays.asList(10886,10885,10884);
	private final static List<Integer> RANGE_THRALLS = Arrays.asList(10883,10882,10881);
	private final static List<Integer> MAGE_THRALLS = Arrays.asList(10880,10879,10878);
	private final static List<Integer> ALL_THRALL_IDS = Arrays.asList(10886,10885,10884,10883,10882,10881,10880,10879,10878);

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;


	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getKey().equals("pet"))
		{
			petData = PetData.pets.get(config.pet().getIdentifier());
			configManager.setConfiguration(CONFIG_GROUP, "selectedCustomNpcId", -1);
			clientThread.invokeLater(()-> updatePet());
	}

		if (event.getKey().equals("sizePercent") || event.getKey().equals("followSpeedPercent"))
		{
			pet.setSizePercent(config.sizePercent());
			pet.setMovementSpeedPercent(config.followSpeedPercent());
		}

		if (event.getKey().equals("companionThralls") && !config.companionThralls())
		{
			for (PetObjectModel thrallObjectModel : activeThralls.values())
			{
				clientThread.invokeLater(thrallObjectModel::despawn);
			}
		}
		else if (event.getKey().equals("companionThralls"))
		{
			for (Map.Entry<NPC,PetObjectModel> entry : activeThralls.entrySet())
			{
				NPC npc = entry.getKey();
				PetObjectModel thrallObjectModel = entry.getValue();

				clientThread.invokeLater(()->
				{
					thrallObjectModel.spawn(npc.getWorldLocation(),npc.getOrientation(),thrallObjectModel.getSize());
					thrallObjectModel.setAnimation(thrallObjectModel.animationPoses[0]);

				});
			}
		}

		if (event.getKey().equals("allowBrokenPets") && config.allowBrokenPets() && !config.debug())
		{
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage("--------------------------------------------------------------------------------").build());
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage("<col=ff0000>[Companion Pets] Warning<col=000000>:<col=ffff00>").build());
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage("<col=000000>You have enabled the broken pets. These pets will not scale correctly and are awaiting a fix from RL.<col=ffff00>").build());
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage("--------------------------------------------------------------------------------").build());
		}




	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{

		if (gameStateChanged.getGameState() == GameState.LOGGING_IN || gameStateChanged.getGameState() == GameState.HOPPING)
		{
			activeThralls.clear();
		}

		if (gameStateChanged.getGameState() == GameState.LOADING)
		{
			if (pet.getRlObject() != null && pet.isActive())
			{
				lastPlayerWP = client.getLocalPlayer().getWorldLocation();
				lastActorWP = pet.getWorldLocation();
				lastActorOrientation = pet.getOrientation();

				pet.despawn();
			}


			for (PetObjectModel thrallObjectModel : activeThralls.values())
			{
				thrallObjectModel.despawn();
			}


		}


		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			if (config.selectedCustomNpcId() >= 0
					&& (!(petData instanceof CustomPetData) || petData.getNpcId() != config.selectedCustomNpcId()))
			{
				CustomPetData configuredCustom = createCustomPet(config.selectedCustomNpcId());
				if (configuredCustom != null)
				{
					petData = configuredCustom;
					if (panel != null)
					{
						panel.updateCurrentPetIcon();
					}
				}
			}
			if (pet.getRlObject() != null && petFollowing)
			{
				WorldPoint wp = client.getLocalPlayer().getWorldLocation();
				WorldPoint aWP = pet.getWorldLocation();

				double intx = aWP.toWorldArea().getX() - wp.toWorldArea().getX();
				double inty = aWP.toWorldArea().getY() - wp.toWorldArea().getY();


				if (lastPlayerWP.distanceTo(client.getLocalPlayer().getWorldLocation()) < 5)
				{
					pet.spawn(lastActorWP,lastActorOrientation,petData.getSize());
					pet.setAnimation(pet.animationPoses[0]);
				}
				else
				{
					pet.spawn(client.getLocalPlayer().getWorldLocation(),radToJau(Math.atan2(intx,inty)),petData.getSize());
					pet.setAnimation(pet.animationPoses[0]);
				}

				nextTravellingPoint = pet.getWorldLocation().toWorldArea();
			}


			for (Map.Entry<NPC,PetObjectModel> entry : activeThralls.entrySet())
			{
				PetObjectModel thrallObjectModel = entry.getValue();
				NPC npc = entry.getKey();

				thrallObjectModel.spawn(npc.getWorldLocation(),npc.getOrientation(),petData.getSize());
				thrallObjectModel.setAnimation(thrallObjectModel.animationPoses[0]);

			}


		}

	}



	@Subscribe
	public void onChatMessage(ChatMessage event)
	{

		String message = event.getMessage();

		if (message.equals("You do not have a follower.") && event.getType() == ChatMessageType.GAMEMESSAGE && !config.disableWhistle())
		{
			callPet(event);
		}

	}


	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{

		if (varbitChanged.getVarbitId() == 6719 && varbitChanged.getValue() == 0)
		{
			petEnterHouse = petFollowing;
		}

	}





	@Subscribe
	public void onGameTick(GameTick event)
	{

		WorldPoint playerDelayedLoc = getAndUpdatePlayersDelayedLoc();

		spawnPetInHouse();

		if (pet.getRlObject() != null && pet.isActive())
		{
			Player player = client.getLocalPlayer();

			// Always recalculate from the rendered location. Using the previous future
			// destination here caused old waypoints to accumulate and made the pet
			// orbit or run past the player after a change of direction.
			petWorldArea = pet.getWorldArea();

			WorldPoint actualPetLocation = WorldPoint.fromLocal(client, pet.getLocalLocation());
			Actor combatOpponent = lastCombatOpponent;
			boolean fleeingCombat = config.fleeDuringCombat()
					&& combatFleeTicks > 0
					&& actualPetLocation != null;
			if (combatFleeTicks > 0)
			{
				combatFleeTicks--;
			}
			else
			{
				lastCombatOpponent = null;
			}

			if (fleeingCombat)
			{
				movePetAwayFromCombat(player, combatOpponent, actualPetLocation);
			}
			else
			{
				pet.setMovementSpeedPercent(config.followSpeedPercent());
			boolean outsideFollowLeash = actualPetLocation != null
					&& actualPetLocation.distanceTo(player.getWorldLocation()) > config.followDistance();

			if (!outsideFollowLeash)
			{
				// Follow distance is a maximum leash, not a spacing command. Once the
				// pet catches up, stop it here so the player can walk closer without
				// making the companion back away to recreate the configured gap.
				pet.stopMoving();
				nextTravellingPoint = pet.getWorldArea();
				blockedFollowTicks = 0;
			}
			else
			{
				// Follow the player's recorded tile trail rather than choosing any tile
				// beside the player. This keeps the companion on the same route while the
				// leash check still lets the player walk closer once it has caught up.
				WorldPoint trailTarget = playerDelayedLoc != null
						? playerDelayedLoc : player.getWorldLocation();
				WorldArea worldArea = new WorldArea(trailTarget,1,1);
				// Use the reliable NPC-style movement for ordinary following. Keep a
				// short destination buffer for smooth rendering, but never let the full
				// route search steer normal movement away from the player's trail.
				WorldArea pathStep = petWorldArea;
				boolean blocked = false;
				for (int step = 0; step < 3 && !pathStep.intersectsWith(worldArea); step++)
				{
					WorldArea calculated = PathingLogic.calculateNextTravellingPoint(
							client, pathStep, worldArea, false, this::extraBlockageCheck);
					if (calculated == null || calculated.equals(pathStep))
					{
						blocked = true;
						break;
					}
					pathStep = calculated;
				}

				if (blocked)
				{
					blockedFollowTicks++;
					if (blockedFollowTicks >= 2)
					{
						WorldArea recoveryStep = PathingLogic.calculatePathDestination(
								client, petWorldArea, worldArea, 0, 1, this::extraBlockageCheck);
						if (recoveryStep != null && !recoveryStep.equals(petWorldArea))
						{
							pathStep = recoveryStep;
						}
					}
				}
				else
				{
					blockedFollowTicks = 0;
				}
				nextTravellingPoint = pathStep;

				if (pet.isActive() && nextTravellingPoint != null && !nextTravellingPoint.equals(petWorldArea))
				{
					double turnX = nextTravellingPoint.getX() - petWorldArea.getX();
					double turnY = nextTravellingPoint.getY() - petWorldArea.getY();
					pet.moveToReplacingTarget(nextTravellingPoint.toWorldPoint(), radToJau(Math.atan2(turnX,turnY)),petData.getSize());
				}
				else if (nextTravellingPoint != null)
				{
					pet.stopMoving();
				}
			}
			}

			if (!fleeingCombat && pet.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) > config.catchUpDistance() && petFollowing)
			{
				callPet(null);
			}

		}


		for (Map.Entry<NPC,PetObjectModel> entry : activeThralls.entrySet())
		{
			PetObjectModel pet = entry.getValue();
			NPC npc = entry.getKey();

			if (pet.getRlObject() == null || !pet.isActive())
			{
				return;
			}

			WorldArea petWorldArea = new WorldArea(pet.getWorldLocation(),1,1);

			//try useing the thralls local point
			WorldArea nextTravellingPoint = PathingLogic.calculateNextTravellingPoint(client,petWorldArea,npc.getWorldArea(),false, this::extraBlockageCheck);

			if (pet.isActive() && nextTravellingPoint != null)
			{
				pet.moveTo(nextTravellingPoint.toWorldPoint(),npc.getCurrentOrientation(),1);
			}

		}


	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player player = client.getLocalPlayer();
		Actor actor = event.getActor();
		if (player == null || actor == null)
		{
			return;
		}

		Actor selectedOpponent = player.getInteracting();
		boolean playerWasHit = actor == player;
		boolean selectedOpponentWasHit = actor == selectedOpponent && isCombatOpponent(actor);
		boolean attackerWasHit = isCombatOpponent(actor) && actor.getInteracting() == player;
		if (playerWasHit || selectedOpponentWasHit || attackerWasHit)
		{
			combatFleeTicks = 8;
			lastCombatOpponent = playerWasHit ? getCombatOpponent(player) : actor;
		}
	}

	private boolean isCombatOpponent(Actor actor)
	{
		return actor instanceof Player || (actor instanceof NPC && ((NPC) actor).getCombatLevel() > 0);
	}

	private Actor getCombatOpponent(Player player)
	{
		if (isCombatOpponent(player.getInteracting()))
		{
			return player.getInteracting();
		}

		for (NPC npc : client.getNpcs())
		{
			if (npc.getInteracting() == player && isCombatOpponent(npc))
			{
				return npc;
			}
		}
		return null;
	}

	private void movePetAwayFromCombat(Player player, Actor opponent, WorldPoint petLocation)
	{
		WorldPoint playerLocation = player.getWorldLocation();
		if (petLocation.distanceTo(playerLocation) >= config.combatFleeDistance())
		{
				pet.stopMoving();
				nextTravellingPoint = pet.getWorldArea();
				blockedFollowTicks = 0;
			return;
		}

		int dx = Integer.compare(petLocation.getX(), playerLocation.getX());
		int dy = Integer.compare(petLocation.getY(), playerLocation.getY());
		if (dx == 0 && dy == 0 && opponent != null)
		{
			dx = Integer.compare(playerLocation.getX(), opponent.getWorldLocation().getX());
			dy = Integer.compare(playerLocation.getY(), opponent.getWorldLocation().getY());
		}
		if (dx == 0 && dy == 0)
		{
			dx = 1;
		}

		WorldPoint fleeTarget = new WorldPoint(
				playerLocation.getX() + dx * config.combatFleeDistance(),
				playerLocation.getY() + dy * config.combatFleeDistance(),
				playerLocation.getPlane());
		WorldArea targetArea = new WorldArea(fleeTarget, 1, 1);
		WorldArea pathStep = petWorldArea;

		int pathSteps = 3;
		for (int step = 0; step < pathSteps; step++)
		{
			WorldArea calculated = PathingLogic.calculateNextTravellingPoint(
					client, pathStep, targetArea, false, this::extraBlockageCheck);
			if (calculated == null || calculated.equals(pathStep))
			{
				break;
			}
			pathStep = calculated;
		}

		if (pathStep.equals(petWorldArea))
		{
			WorldPoint escape = getPathOutWorldPoint(petWorldArea);
			if (escape != null)
			{
				pathStep = new WorldArea(escape, petData.getSize(), petData.getSize());
			}
		}

		nextTravellingPoint = pathStep;
		pet.setMovementSpeedPercent(Math.min(250, config.followSpeedPercent() + 50));
		double turnX = pathStep.toWorldPoint().getX() - petLocation.getX();
		double turnY = pathStep.toWorldPoint().getY() - petLocation.getY();
		pet.moveToReplacingTarget(pathStep.toWorldPoint(), radToJau(Math.atan2(turnX, turnY)), petData.getSize());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		int npcID = event.getNpc().getId();

		if (ALL_THRALL_IDS.contains(npcID))
		{
			PetObjectModel thrallObjectModel = new PetObjectModel();

			if (thrallObjectModel.getRlObject() == null || !thrallObjectModel.isActive())
			{
				PetData petData = PetData.pets.get(getThrallTypeData(npcID).getIdentifier());
				Model petModel = provideModel(petData);

				thrallObjectModel.init(client,petData);
				thrallObjectModel.setPoseAnimations(petData.getIdleAnim(),petData.getWalkAnim(),petData.getRunAnim());
				thrallObjectModel.setModel(petModel);
				thrallObjectModel.getRlObject().setDrawFrontTilesFirst(true);
			}

			if (config.companionThralls())
			{
				thrallObjectModel.spawn(event.getNpc().getWorldLocation(),event.getNpc().getOrientation(),1);
				thrallObjectModel.setAnimation(thrallObjectModel.animationPoses[0]); //0 == walk
			}


			activeThralls.putIfAbsent(event.getNpc(),thrallObjectModel);
		}

	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{

		if (activeThralls.containsKey(event.getNpc()))
		{
			activeThralls.get(event.getNpc()).despawn();
			activeThralls.remove(event.getNpc());
		}

	}


	//magic thrall and other thrall anims dont register becuse their first attk can come out as a neg 1 anim, use projectile instead
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{   //3847 for thermy 7126 for sire
		if (event.getActor() instanceof NPC)
		{
			NPC npc = (NPC) event.getActor();

			if (activeThralls.containsKey(npc) && npc.getAnimation() != -1 && activeThralls.get(npc).isActive())
			{
				PetObjectModel thrallPet = activeThralls.get(npc);

				if (thrallPet.getPetAttkAnim() != -1)
				{
					thrallPet.setAnimation(client.loadAnimation(thrallPet.getPetAttkAnim()));
				}
			}


		}



	}



	@Subscribe
	public void onClientTick(ClientTick event)
	{

		//skelly attk = 21 frames == +55 for start
		//melee attk = 21 frames
		//magic attk = 18 frames
		//basic magic attack anim 711


		if (pet.getRlObject() != null && pet.animationPoses != null)
		{

			LocalPoint lp = pet.getLocalLocation();
			int zOff = Perspective.getTileHeight(client,lp,client.getPlane());

			petPoly = calculateAABB(client, pet.getRlObject().getModel(), pet.getOrientation(), pet.getLocalLocation().getX(), pet.getLocalLocation().getY(),client.getPlane(), zOff);

			double intx = pet.getRlObject().getLocation().getX() - client.getLocalPlayer().getLocalLocation().getX();
			double inty = pet.getRlObject().getLocation().getY() - client.getLocalPlayer().getLocalLocation().getY();

			pet.onClientTick(event,radToJau(Math.atan2(intx,inty)));

			//for 2x2 set drawFontTilesFirst only when they are moveing / test vs walls in house and rimmy
			if (petData.getSize() == 2 && pet.getRlObject().getAnimation() == pet.animationPoses[1])
			{
				pet.getRlObject().setDrawFrontTilesFirst(true);
			}
			else if (pet.getRlObject().getAnimation() == pet.animationPoses[0] && pet.getRlObject().isDrawFrontTilesFirst())
			{
				pet.getRlObject().setDrawFrontTilesFirst(false);
			}

		}




		for (Map.Entry<NPC,PetObjectModel> entry : activeThralls.entrySet())
		{
			PetObjectModel thrallOM = entry.getValue();
			NPC thrall = entry.getKey();

			//System.out.println(thrallOM.getRlObject().getAnimationFrame());


			//18 is the end frame of the animation for abyssal orpahn //10 for thermy //9 zulrah
			//1741 zulrah anim
			if (thrallOM.getRlObject().getAnimation() != null
				&& thrallOM.getPetAttkAnim() != -1
				&& thrallOM.getRlObject().getAnimation().getId() == thrallOM.getPetAttkAnim()
				&& thrallOM.getRlObject().getAnimationFrame() == thrallOM.getPetAttkAnimFrames())
			{
				if (thrallOM.targetQueueSize == 0 && thrallOM.getRlObject().getAnimation() != thrallOM.animationPoses[0])
				{
					thrallOM.setAnimation(thrallOM.animationPoses[0]);
				}
				else
				{
					thrallOM.setAnimation(thrallOM.animationPoses[1]);
				}
			}

			thrallOM.onClientTick(event,thrall.getOrientation());

		}


	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (event.getProjectile().getStartCycle() == client.getGameCycle())
		{
			//x1 and y1 represet the projectiles cords when it is first spawned
			LocalPoint point = new LocalPoint(event.getProjectile().getX1(),event.getProjectile().getY1());

			for (Map.Entry<NPC,PetObjectModel> entry : activeThralls.entrySet())
			{
				NPC npc = entry.getKey();
				PetObjectModel thrall = entry.getValue();

				if (npc.getLocalLocation().equals(point) && npc.getAnimation() == -1 && thrall.getPetAttkAnim() != -1)
				{
					thrall.setAnimation(client.loadAnimation(thrall.getPetAttkAnim()));
				}


			}


		}







	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{

		if (pet.getRlObject() == null || !pet.isActive())
		{
			return;
		}

		int firstMenuIndex = 0;

		for (int i = 0; i < client.getMenuEntries().length; i++)
		{
			if (client.getMenuEntries()[i].getOption().equals("Cancel"))
			{
				firstMenuIndex = i + 1;
				break;
			}
		}


		List<String> options = petData instanceof CustomPetData
				? Arrays.asList("Pick-up", "Examine")
				: Arrays.asList("Talk-to", "Pick-up", "Examine");

		if (petData.isMetamorph())
		{
			options = Arrays.asList("Talk-to","Pick-up","Metamorphosis","Examine");
		}


		for (String string : options)
		{
			if (petPoly.contains(client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()))
			{
				client.createMenuEntry(firstMenuIndex)
						.setOption(string)
						.setTarget("<col=ffff00>" + petData.getName() + "</col>")
						.setType(MenuAction.RUNELITE)
						.setParam0(0)
						.setParam1(0)
						.setDeprioritized(true);

			}
		}

	}



	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{


		if (petData == null)
		{
			return;
		}


		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Pick-up"))
		{
			if (pet.isActive())
			{
				petFollowing = false;
				pet.despawn();
			}
		}

		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Examine"))
		{
			if (pet.isActive())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"",petData.getExamine(),"",false);
			}
		}


		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Metamorphosis"))
		{
			if (pet.isActive())
			{
				if (!PetData.morphModel.get(petData).isWorking() && !config.allowBrokenPets())
				{
					chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage("<col=000000>Sadly the transmog for this pet is awaiting a RL fix to work correctly<col=ffff00>").build());
					return;
				}

				petData = PetData.morphModel.get(petData);
				updatePet();
			}
		}

		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Talk-to"))
		{

			if (pet.isActive() && pet.getWorldArea().isInMeleeDistance(client.getLocalPlayer().getWorldArea()))
			{
				dialogOpen = true;
				fakeDialogManager.open(provideDialog());
			}

		}

		if (!event.getMenuTarget().contains(petData.getName()) && !event.getMenuOption().equals("Continue") && dialogOpen)
		{
			dialogOpen = false;
			chatboxPanelManager.close();
		}



	}



	//Hides the runelite obj to give other renderables like players prio
	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof RuneLiteObject)
		{
			RuneLiteObject rlObject = (RuneLiteObject) renderable;
			if (pet.getRlObject() != null && pet.getRlObject().equals(rlObject))
			{
				List<LocalPoint> localPoints = new ArrayList<>();
				List<Player> nonLocalPlayers = client.getPlayers().stream().filter(player -> !Objects.equals(player.getName(), client.getLocalPlayer().getName())).collect(Collectors.toList());
				nonLocalPlayers.forEach(player -> localPoints.add(player.getLocalLocation()));
				client.getNpcs().forEach(npc -> localPoints.add(npc.getLocalLocation()));

				boolean overlappingModel = localPoints.stream().anyMatch(localPoint -> localPoint.equals(pet.getLocalLocation()));
				return !overlappingModel;
			}


			for (PetObjectModel thrallOM : activeThralls.values())
			{
				if (thrallOM.getRlObject().equals(rlObject))
				{
					List<LocalPoint> localPoints = new ArrayList<>();
					client.getPlayers().forEach(player -> localPoints.add(player.getLocalLocation()));

					client.getNpcs().stream().filter(npc -> !ALL_THRALL_IDS.contains(npc.getId())).forEach(npc -> localPoints.add(npc.getLocalLocation()));

					boolean overlappingModel = localPoints.stream().anyMatch(localPoint -> localPoint.equals(thrallOM.getLocalLocation()));

					return !overlappingModel;
				}


			}

		}


		if (renderable instanceof NPC && config.companionThralls())
		{
			NPC npc = (NPC) renderable;
			return !ALL_THRALL_IDS.contains(npc.getId());
		}

		return true;
	}

	private PetData getThrallTypeData(int npcID)
	{
		if (MELEE_THRALLS.contains(npcID))
		{
			return config.meleeThrall();
		}

		if (RANGE_THRALLS.contains(npcID))
		{
			return config.rangeThrall();
		}

		if (MAGE_THRALLS.contains(npcID))
		{
			return config.mageThrall();
		}

		return null;
	}

	private void despawnAnyActivePOMs()
	{
		if (pet.getRlObject() != null && pet.isActive())
		{
			pet.despawn();
		}

		for (PetObjectModel pom : activeThralls.values())
		{
			pom.despawn();
		}

		activeThralls.clear();
	}

	private WorldPoint getAndUpdatePlayersDelayedLoc()
	{
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getWorldLocation() == null)
		{
			return null;
		}

		WorldPoint current = client.getLocalPlayer().getWorldLocation();

		// Keep actual visited positions, not repeated idle ticks. This makes the
		// setting represent distance along the route rather than a time delay.
		if (prevPlayerWPs.isEmpty()
				|| prevPlayerWPs.get(0).getPlane() != current.getPlane()
				|| prevPlayerWPs.get(0).distanceTo(current) > config.catchUpDistance())
		{
			prevPlayerWPs.clear();
			prevPlayerWPs.add(current);
			return current;
		}

		if (!current.equals(prevPlayerWPs.get(0)))
		{
			WorldPoint previous = prevPlayerWPs.get(0);
			int dx = current.getX() - previous.getX();
			int dy = current.getY() - previous.getY();

			// A running player commonly advances two tiles between game ticks. Record
			// the crossed tile too, so follow distance represents actual footsteps
			// rather than samples in time.
			if (Math.max(Math.abs(dx), Math.abs(dy)) == 2)
			{
				WorldPoint intermediate = new WorldPoint(
						previous.getX() + Integer.signum(dx),
						previous.getY() + Integer.signum(dy),
						current.getPlane());
				if (!intermediate.equals(previous) && !intermediate.equals(current))
				{
					prevPlayerWPs.add(0, intermediate);
				}
			}
			prevPlayerWPs.add(0, current);
		}

		// Select by ordered trail position, not straight-line distance. Distance
		// could select the wrong side of a bend or loop when an older tile became
		// physically close to the player again.
		int trailIndex = Math.min(config.followDistance(), prevPlayerWPs.size() - 1);
		WorldPoint delayed = prevPlayerWPs.get(trailIndex);

		if (prevPlayerWPs.size() > 32)
		{
			prevPlayerWPs.subList(32, prevPlayerWPs.size()).clear();
		}

		return delayed;
	}

	private void spawnPetInHouse()
	{
		if (petEnterHouse && petFollowing)
		{
			petEnterHouse = false;
			WorldPoint wp = client.getLocalPlayer().getWorldLocation();
			WorldPoint aWP = pet.getWorldLocation();

			double intx = aWP.toWorldArea().getX() - wp.toWorldArea().getX();
			double inty = aWP.toWorldArea().getY() - wp.toWorldArea().getY();

			pet.spawn(client.getLocalPlayer().getWorldLocation(),radToJau(Math.atan2(intx,inty)),petData.getSize());
			pet.setAnimation(pet.animationPoses[0]);
			nextTravellingPoint = pet.getWorldLocation().toWorldArea();
		}

	}

	public WorldPoint getPathOutWorldPoint(WorldArea worldArea)
	{

		ArrayList<WorldPoint> points = new ArrayList<>();

		for (int i = -1; i < 2; i++)
		{
			if (i != 0)
			{
				if (worldArea.canTravelInDirection(client.getTopLevelWorldView(),i,0))
				{

					WorldPoint worldPoint = new WorldPoint(worldArea.getX() + i,worldArea.getY(),client.getPlane());

					boolean secondCheck = true;
					if (petData.getSize() == 2)
					{
						WorldArea area = new WorldArea(worldPoint,2,2);
						secondCheck = area.canTravelInDirection(client.getTopLevelWorldView(),i,0) ;
					}


					if (!worldPoint.equals(client.getLocalPlayer().getWorldLocation()) && secondCheck)
					{
						points.add(worldPoint);
					}

				}


				if (worldArea.canTravelInDirection(client.getTopLevelWorldView(),0,i))
				{
					WorldPoint worldPoint = new WorldPoint(worldArea.getX(),worldArea.getY() + i,client.getPlane());

					boolean secondCheck = true;
					if (petData.getSize() == 2)
					{
						WorldArea area = new WorldArea(worldPoint,2,2);
						secondCheck	= area.canTravelInDirection(client.getTopLevelWorldView(),0,i);
					}

					if (!worldPoint.equals(client.getLocalPlayer().getWorldLocation()) && secondCheck)
					{
						points.add(worldPoint);
					}
				}
			}
		}


		if (!points.isEmpty())
		{
			return points.get(getRandomInt(points.size() - 1,0));
		}

		return null;
	}

	private int getRandomInt(int max, int min)
	{
		return min + (int)(Math.random() * ((max - min) + 1));
	}


	private DialogNode provideDialog()
	{

		List<String> data = Arrays.stream(petData.getDryestPerson().split(":")).collect(Collectors.toList());

		String kcIdentifer = data.get(0);
		String name = data.get(1);
		String kc = data.get(2);
		String date = data.get(3);

		return DialogNode.builder()
				.player()
				.animationId(567)
				.body("Tell me something to make me feel better")
				.onContinue
						(() ->
								DialogNode.builder()
										.npc(petData.getNpcId())
										.title(petData.getName())
										.body("It took " + name +" "+ kc + " " + kcIdentifer +" but<br>" +
												"They finally got me on " + date)
										.animationId(petData.getChatHeadAnimID())
										.build()


						)
				.build();
	}



	//contrast * 5 + 850
	public Model provideModel(CompanionData petData)
	{
		ModelData[] modelDataArray = new ModelData[petData.getModelIDs().size()];
		for (int i = 0; i < petData.getModelIDs().size(); i++)
		{
			modelDataArray[i] = client.loadModelData(petData.getModelIDs().get(i));
		}

		ModelData modelData = createModel(client,modelDataArray);
		modelData.cloneVertices();

//		if (petData.getScale() != -1)
//		{
//			modelData.cloneVertices();
//			modelData.scale(petData.getScale(),petData.getScale(),petData.getScale());
//		}


		//cut list in half fist half color to find, second half color to replace
		if (petData.getRecolorIDs() !=  null)
		{
			modelData.cloneColors();
			int mid = (petData.getRecolorIDs().size() / 2);

			for (int i = 0; i < mid; i++)
			{
				modelData.recolor(petData.getRecolorIDs().get(i),petData.getRecolorIDs().get(mid + i));
			}

		}


		int ambient = (petData.getAmbient() != -1 ? petData.getAmbient() : 0);
		int contrast = (petData.getContrast() != -1 ? petData.getContrast() : 0);

		return modelData.light(ambient + 64, contrast + 850,-30,-50,-30);
	}


	private void buildSidePanel()
	{
		panel = injector.getInstance(RapidCompanionsPanel.class);
		if (petData == null)
		{
			petData = loadConfiguredPet();
		}
		panel.sidePanelInitializer();

		BufferedImage icon = createBearPawSidebarIcon();
		navButton = NavigationButton.builder()
				.tooltip("Rapid Companions")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	private static BufferedImage createBearPawSidebarIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
				java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		java.awt.Color brown = new java.awt.Color(112, 73, 42);
		java.awt.Color gold = new java.awt.Color(231, 178, 83);
		graphics.setStroke(new java.awt.BasicStroke(1.2f));
		int[][] pads = {{4, 7, 7, 7}, {1, 5, 4, 5}, {3, 1, 4, 5}, {8, 1, 4, 5}, {11, 5, 4, 5}};
		for (int[] pad : pads)
		{
			graphics.setColor(brown);
			graphics.fillOval(pad[0], pad[1], pad[2], pad[3]);
			graphics.setColor(gold);
			graphics.drawOval(pad[0], pad[1], pad[2], pad[3]);
		}
		graphics.dispose();
		return image;
	}

	void toggleCompanion()
	{
		if (pet.getRlObject() != null && pet.isActive())
		{
			petFollowing = false;
			pet.despawn();
			prevPlayerWPs.clear();
			combatFleeTicks = 0;
			lastCombatOpponent = null;
			blockedFollowTicks = 0;
			return;
		}

		if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
		{
			callPet(null);
		}
	}

	boolean isCompanionActive()
	{
		return pet.getRlObject() != null && pet.isActive();
	}

	CompanionData loadConfiguredPet()
	{
		int customNpcId = config.selectedCustomNpcId();
		// NPC definitions are unavailable while the client is still on the login
		// screen. The selected custom pet is restored by onGameStateChanged once
		// the game reaches LOGGED_IN.
		if (customNpcId >= 0 && client.getGameState() == GameState.LOGGED_IN)
		{
			CustomPetData custom = createCustomPet(customNpcId);
			if (custom != null)
			{
				return custom;
			}
		}
		return PetData.pets.get(config.pet().getIdentifier());
	}

	CustomPetData createCustomPet(int npcId)
	{
		if (npcId < 0)
		{
			return null;
		}
		NPCComposition composition = client.getNpcDefinition(npcId);
		if (composition == null)
		{
			return null;
		}
		if (composition.getConfigs() != null)
		{
			NPCComposition transformed = composition.transform();
			if (transformed != null)
			{
				composition = transformed;
			}
		}
		int[] models = composition.getModels();
		if (models == null || models.length == 0)
		{
			return null;
		}
		ArrayList<Integer> modelIds = new ArrayList<>();
		for (int modelId : models)
		{
			if (modelId >= 0)
			{
				modelIds.add(modelId);
			}
		}
		if (modelIds.isEmpty())
		{
			return null;
		}

		ArrayList<Short> recolors = null;
		short[] replace = composition.getColorToReplace();
		short[] replaceWith = composition.getColorToReplaceWith();
		if (replace != null && replaceWith != null && replace.length == replaceWith.length)
		{
			recolors = new ArrayList<>();
			for (short color : replace)
			{
				recolors.add(color);
			}
			for (short color : replaceWith)
			{
				recolors.add(color);
			}
		}

		int idleAnimation = getSavedCustomAnimation(npcId, "idle");
		int walkAnimation = getSavedCustomAnimation(npcId, "walk");
		int runAnimation = getSavedCustomAnimation(npcId, "run");
		for (NPC npc : client.getNpcs())
		{
			if (npc.getId() == npcId)
			{
				idleAnimation = npc.getIdlePoseAnimation();
				walkAnimation = npc.getWalkAnimation();
				runAnimation = npc.getRunAnimation();
				configManager.setConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".idle", idleAnimation);
				configManager.setConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".walk", walkAnimation);
				configManager.setConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".run", runAnimation);
				break;
			}
		}
		String customName = configManager.getConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".name");
		String displayName = customName == null || customName.trim().isEmpty()
				? composition.getName() : customName.trim();
		return new CustomPetData(npcId, displayName, modelIds,
				composition.getSize(), idleAnimation, walkAnimation, runAnimation,
				composition.getWidthScale(), recolors);
	}

	private int getSavedCustomAnimation(int npcId, String type)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, "customNpc." + npcId + "." + type);
		if (value == null)
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ignored)
		{
			return -1;
		}
	}




	public void updatePet()
	{
		if (petData instanceof PetData)
		{
			configManager.setConfiguration(CONFIG_GROUP,"pet",(PetData) petData);
			configManager.setConfiguration(CONFIG_GROUP,"selectedCustomNpcId",-1);
		}
		else if (petData instanceof CustomPetData)
		{
			configManager.setConfiguration(CONFIG_GROUP,"selectedCustomNpcId",petData.getNpcId());
		}

		if (pet.getRlObject() == null)
		{
			pet.init(client,petData);
		}

		petModel = provideModel(petData);
		pet.setPoseAnimations(petData.getIdleAnim(),petData.getWalkAnim(),petData.getRunAnim());
		pet.setPetData(petData);
		pet.setSizePercent(config.sizePercent());
		pet.setMovementSpeedPercent(config.followSpeedPercent());

		if (client.getGameState() == GameState.LOGGED_IN && pet.isActive())
		{
			//set to 0 for 1x1 and != 90 for 2x2
			if (pet.getLocalLocation().distanceTo(LocalPoint.fromWorld(client,nextTravellingPoint.toWorldPoint())) > 0 && pet.getLocalLocation().distanceTo(LocalPoint.fromWorld(client,nextTravellingPoint.toWorldPoint())) != 90)
			{
				pet.setAnimation(pet.animationPoses[1]);
			}
			else
			{
				pet.setAnimation(pet.animationPoses[0]);
			}

		}

		pet.setModel(petModel);
		panel.updateCurrentPetIcon();
	}

	public void updatePet(CompanionData buttonPetData)
	{
		petData = buttonPetData;
		updatePet();
	}

	public boolean extraBlockageCheck(WorldPoint worldPoint)
	{
		if (petData.getSize() != 2)
		{
			return true;
		}

		WorldArea area = new WorldArea(worldPoint, 1, 1);

		List<WorldArea> worldAreas = new ArrayList<>();
		client.getPlayers().forEach(p -> worldAreas.add(p.getWorldArea()));
		client.getNpcs().forEach(npc -> worldAreas.add(npc.getWorldArea()));

		boolean overlappingModel = worldAreas.stream().anyMatch(wa -> wa.intersectsWith(area));

		return !overlappingModel;
	}


	private void callPet(ChatMessage event)
	{

		if ((pet.getRlObject() == null || !pet.isActive()))
		{
			if (petData == null)
			{
				petData = loadConfiguredPet();
			}
			petModel = provideModel(petData);

			pet.init(client,petData);
			pet.setPoseAnimations(petData.getIdleAnim(),petData.getWalkAnim(),petData.getRunAnim());
			pet.setSizePercent(config.sizePercent());
			pet.setMovementSpeedPercent(config.followSpeedPercent());
			pet.setModel(petModel);
			pet.getRlObject().setDrawFrontTilesFirst(true);
		}

		WorldPoint wp = client.getLocalPlayer().getWorldLocation();
		WorldPoint aWP = pet.getWorldLocation();

		boolean petHasLOS = wp.toWorldArea().hasLineOfSightTo(client.getTopLevelWorldView(),aWP);

		if (event != null && wp.toWorldArea().distanceTo(aWP.toWorldArea()) < 6 && petHasLOS && pet.isActive())
		{
			event.getMessageNode().setValue("Your follower is already close enough.");
			return;
		}
		else if (pet.isActive())
		{
			pet.despawn();
		}


		double intx = aWP.toWorldArea().getX() - wp.toWorldArea().getX();
		double inty = aWP.toWorldArea().getY() - wp.toWorldArea().getY();

		if (event != null)
		{
			event.getMessageNode().setValue("");
		}

		petFollowing = true;
		combatFleeTicks = 0;
		lastCombatOpponent = null;
		blockedFollowTicks = 0;

		pet.spawn(getPathOutWorldPoint(new WorldArea(getAndUpdatePlayersDelayedLoc(),petData.getSize(),petData.getSize())),radToJau(Math.atan2(intx,inty)),petData.getSize());
		pet.setAnimation(pet.animationPoses[0]); //0 == walk
		nextTravellingPoint = pet.getWorldLocation().toWorldArea();
	}

	private static ModelData createModel(Client client, ModelData... data)
	{
		return client.mergeModels(data);
	}

	private static ModelData createModel(Client client, int... data)
	{
		ModelData[] modelData = new ModelData[data.length];
		for (int i = 0; i < data.length; i++)
		{
			modelData[i] = client.loadModelData(data[i]);
		}
		return client.mergeModels(modelData);
	}


	private static SimplePolygon calculateAABB(Client client, Model m, int jauOrient, int x, int y, int z, int zOff)
	{
		AABB aabb = m.getAABB(jauOrient);

		int x1 = aabb.getCenterX();
		int y1 = aabb.getCenterZ();
		int z1 = aabb.getCenterY() + zOff;

		int ex = aabb.getExtremeX();
		int ey = aabb.getExtremeZ();
		int ez = aabb.getExtremeY();

		int x2 = x1 + ex;
		int y2 = y1 + ey;
		int z2 = z1 + ez;

		x1 -= ex;
		y1 -= ey;
		z1 -= ez;

		int[] xa = new int[]{
				x1, x2, x1, x2,
				x1, x2, x1, x2
		};
		int[] ya = new int[]{
				y1, y1, y2, y2,
				y1, y1, y2, y2
		};
		int[] za = new int[]{
				z1, z1, z1, z1,
				z2, z2, z2, z2
		};

		int[] x2d = new int[8];
		int[] y2d = new int[8];

		modelToCanvasCpu(client, 8, x, y, z, 0, xa, ya, za, x2d, y2d);

		return Jarvis.convexHull(x2d, y2d);
	}

	private static void modelToCanvasCpu(Client client, int end, int x3dCenter, int y3dCenter, int z3dCenter, int rotate, int[] x3d, int[] y3d, int[] z3d, int[] x2d, int[] y2d)
	{
		final int
				cameraPitch = client.getCameraPitch() & 2047,
				cameraYaw = client.getCameraYaw() & 2047,
				normalizedRotate = rotate & 2047,

				pitchSin = SINE[cameraPitch],
				pitchCos = COSINE[cameraPitch],
				yawSin = SINE[cameraYaw],
				yawCos = COSINE[cameraYaw],
				rotateSin = SINE[normalizedRotate],
				rotateCos = COSINE[normalizedRotate],

				cx = x3dCenter - client.getCameraX(),
				cy = y3dCenter - client.getCameraY(),
				cz = z3dCenter - client.getCameraZ(),

				viewportXMiddle = client.getViewportWidth() / 2,
				viewportYMiddle = client.getViewportHeight() / 2,
				viewportXOffset = client.getViewportXOffset(),
				viewportYOffset = client.getViewportYOffset(),

				zoom3d = client.getScale();

		for (int i = 0; i < end; i++)
		{
			int x = x3d[i];
			int y = y3d[i];
			int z = z3d[i];

			if (normalizedRotate != 0)
			{
				int x0 = x;
				x = x0 * rotateCos + y * rotateSin >> 16;
				y = y * rotateCos - x0 * rotateSin >> 16;
			}

			x += cx;
			y += cy;
			z += cz;

			final int
					x1 = x * yawCos + y * yawSin >> 16,
					y1 = y * yawCos - x * yawSin >> 16,
					y2 = z * pitchCos - y1 * pitchSin >> 16,
					z1 = y1 * pitchCos + z * pitchSin >> 16;

			int viewX, viewY;

			if (z1 < 50)
			{
				viewX = Integer.MIN_VALUE;
				viewY = Integer.MIN_VALUE;
			}
			else
			{
				viewX = (viewportXMiddle + x1 * zoom3d / z1) + viewportXOffset;
				viewY = (viewportYMiddle + y2 * zoom3d / z1) + viewportYOffset;
			}

			x2d[i] = viewX;
			y2d[i] = viewY;
		}
	}

	private void setPetMochi(ChatMessage chatMessage, String s)
	{
		configManager.setConfiguration(CONFIG_GROUP, "pet", PetData.MOCHI);
		callPet(chatMessage);
	}


}
