package com.rapidursa.companions;

import java.util.ArrayList;
import lombok.Getter;

@Getter
public final class CustomPetData implements CompanionData
{
	private final String identifier;
	private final String name;
	private final boolean working = true;
	private final int attkAnim = -1;
	private final int attkAnimFrames = -1;
	private final int npcId;
	private final int iconID = -1;
	private final ArrayList<Integer> modelIDs;
	private final int size;
	private final int idleAnim;
	private final int walkAnim;
	private final int runAnim;
	private final int scale;
	private final int ambient = -1;
	private final int contrast = -1;
	private final ArrayList<Short> recolorIDs;
	private final boolean metamorph = false;
	private final String examine;
	private final String dryestPerson = "NPC ID:Custom companion:0:today";
	private final int chatHeadAnimID;

	public CustomPetData(int npcId, String name, ArrayList<Integer> modelIDs,
			int size, int idleAnim, int walkAnim, int runAnim, int scale,
			ArrayList<Short> recolorIDs)
	{
		this.npcId = npcId;
		this.identifier = "custom:" + npcId;
		this.name = name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name)
				? "NPC " + npcId : name;
		this.modelIDs = modelIDs;
		this.size = Math.max(1, Math.min(2, size));
		this.idleAnim = idleAnim;
		this.walkAnim = walkAnim >= 0 ? walkAnim : idleAnim;
		this.runAnim = runAnim >= 0 ? runAnim : this.walkAnim;
		this.scale = scale == 128 ? -1 : scale;
		this.recolorIDs = recolorIDs;
		this.examine = "A custom companion using NPC ID " + npcId + ".";
		this.chatHeadAnimID = idleAnim;
	}
}
