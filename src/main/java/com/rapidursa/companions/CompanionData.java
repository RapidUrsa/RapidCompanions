package com.rapidursa.companions;

import java.util.ArrayList;

/** Common model data shared by built-in pets and user-added NPC companions. */
public interface CompanionData
{
	String getIdentifier();
	String getName();
	boolean isWorking();
	int getAttkAnim();
	int getAttkAnimFrames();
	int getNpcId();
	int getIconID();
	ArrayList<Integer> getModelIDs();
	int getSize();
	int getIdleAnim();
	int getWalkAnim();
	int getRunAnim();
	int getScale();
	int getAmbient();
	int getContrast();
	ArrayList<Short> getRecolorIDs();
	boolean isMetamorph();
	String getExamine();
	String getDryestPerson();
	int getChatHeadAnimID();
}
