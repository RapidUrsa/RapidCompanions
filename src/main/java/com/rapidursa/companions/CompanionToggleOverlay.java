package com.rapidursa.companions;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
final class CompanionToggleOverlay extends Overlay implements MouseListener
{
    private final RapidCompanionsPlugin plugin;
    private final RapidCompanionsConfig config;
    private final ClientThread clientThread;
    private final ItemManager itemManager;
    private CompanionData cachedPet;
    private BufferedImage cachedIcon;

    @Inject
    CompanionToggleOverlay(RapidCompanionsPlugin plugin, RapidCompanionsConfig config,
            ClientThread clientThread, ItemManager itemManager)
    {
        this.plugin = plugin;
        this.config = config;
        this.clientThread = clientThread;
        this.itemManager = itemManager;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        int size = Math.max(24, Math.min(96, config.buttonSize()));
        int padding = Math.max(2, size / 12);
        boolean active = plugin.isCompanionActive();
        CompanionData selected = plugin.petData != null ? plugin.petData : config.pet();
        if (selected != cachedPet)
        {
            cachedPet = selected;
            cachedIcon = selected.getIconID() >= 0 ? itemManager.getImage(selected.getIconID()) : null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(18, 20, 24, 210));
        graphics.fillRoundRect(0, 0, size, size, Math.max(7, size / 5), Math.max(7, size / 5));
        graphics.setColor(active ? new Color(231, 178, 83) : new Color(115, 120, 128));
        graphics.setStroke(new BasicStroke(Math.max(1f, size / 22f)));
        graphics.drawRoundRect(1, 1, size - 3, size - 3, Math.max(7, size / 5), Math.max(7, size / 5));

        if (cachedIcon != null)
        {
            Composite previous = graphics.getComposite();
            if (!active)
            {
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.48f));
            }
            graphics.drawImage(cachedIcon, padding, padding, size - 2 * padding, size - 2 * padding, null);
            graphics.setComposite(previous);
        }
		else
		{
			graphics.setColor(active ? new Color(231, 178, 83) : new Color(150, 150, 150));
			drawCustomPetName(graphics, selected.getName(), size);
		}
        return new Dimension(size, size);
    }

	private static void drawCustomPetName(Graphics2D graphics, String name, int size)
	{
		String label = name == null || name.trim().isEmpty() ? "NPC" : name.trim();
		String[] lines = splitName(label);
		float fontSize = Math.max(7f, size / 5f);
		int availableWidth = Math.max(12, size - 8);
		do
		{
			graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, fontSize));
			int widest = 0;
			for (String line : lines)
			{
				widest = Math.max(widest, graphics.getFontMetrics().stringWidth(line));
			}
			if (widest <= availableWidth || fontSize <= 5f)
			{
				break;
			}
			fontSize -= 0.5f;
		}
		while (true);

		int lineHeight = graphics.getFontMetrics().getHeight();
		int baseline = (size - lineHeight * lines.length) / 2 + graphics.getFontMetrics().getAscent();
		for (String line : lines)
		{
			int textWidth = graphics.getFontMetrics().stringWidth(line);
			graphics.drawString(line, (size - textWidth) / 2, baseline);
			baseline += lineHeight;
		}
	}

	private static String[] splitName(String name)
	{
		if (name.length() <= 9)
		{
			return new String[]{name};
		}
		int middle = name.length() / 2;
		int split = name.lastIndexOf(' ', middle);
		if (split < 1)
		{
			split = name.indexOf(' ', middle);
		}
		if (split < 1)
		{
			split = middle;
		}
		return new String[]{name.substring(0, split).trim(), name.substring(split).trim()};
	}

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        if (SwingUtilities.isLeftMouseButton(event) && getBounds().contains(event.getPoint()))
        {
            event.consume();
            clientThread.invokeLater(plugin::toggleCompanion);
        }
        return event;
    }

    @Override public MouseEvent mouseClicked(MouseEvent event) { return event; }
    @Override public MouseEvent mouseReleased(MouseEvent event) { return event; }
    @Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
    @Override public MouseEvent mouseExited(MouseEvent event) { return event; }
    @Override public MouseEvent mouseDragged(MouseEvent event) { return event; }
    @Override public MouseEvent mouseMoved(MouseEvent event) { return event; }
}
