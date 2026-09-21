package com.rapidursa.companions;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;


public class RapidCompanionsPluginTest
{
	public static void main(String[] args) throws Exception
	{

		ExternalPluginManager.loadBuiltin(RapidCompanionsPlugin.class);

		RuneLite.main(args);

	}
}
