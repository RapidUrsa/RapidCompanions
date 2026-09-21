package com.rapidursa.companions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class RapidCompanionsPanel extends PluginPanel
{
    private static final String CONFIG_GROUP = "rapidCompanions";
    private static final Color ACTIVE_BORDER = new Color(231, 178, 83);
    private static final Color INACTIVE_BORDER = new Color(70, 74, 80);
    private static final Color FAVORITE_GOLD = new Color(244, 194, 76);

    private final RapidCompanionsPlugin plugin;
    private final RapidCompanionsConfig config;
    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final JPanel content = new JPanel();
    private boolean initialized;

    @Inject
    public RapidCompanionsPanel(RapidCompanionsPlugin plugin, RapidCompanionsConfig config,
            ItemManager itemManager, ClientThread clientThread, ConfigManager configManager)
    {
        // Keep this class as the navigation panel for compatibility with the
        // RuneLite API used by this project. The catalogue is placed in its
        // own constrained scroll pane in sidePanelInitializer().
        super(false);
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.configManager = configManager;
    }

    public void sidePanelInitializer()
    {
        if (!initialized)
        {
            setLayout(new BorderLayout());
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(ColorScheme.DARK_GRAY_COLOR);

            JPanel northPanel = new JPanel(new BorderLayout());
            northPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            northPanel.add(content, BorderLayout.NORTH);

            JScrollPane scrollPane = new JScrollPane(northPanel);
            scrollPane.setBorder(null);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            add(scrollPane, BorderLayout.CENTER);
            initialized = true;
        }
        refresh();
    }

    public void updateCurrentPetIcon()
    {
        refresh();
    }

    private void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        if (!initialized)
        {
            return;
        }

        content.removeAll();
        JLabel title = new JLabel("Companion Menagerie");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.PLAIN, 22f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);

        JLabel subtitle = new JLabel("Choose your cosmetic companion");
        subtitle.setForeground(Color.LIGHT_GRAY);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(subtitle);
        content.add(gap(12));

        addSectionTitle("Favourites");
        List<PetData> favourites = getFavourites();
        if (favourites.isEmpty())
        {
            JLabel empty = new JLabel("Star a companion to keep it here");
            empty.setForeground(Color.GRAY);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            content.add(empty);
        }
        else
        {
            JPanel favouriteCards = createIconGrid();
            for (PetData pet : favourites)
            {
                favouriteCards.add(createPetCard(pet, true));
            }
            content.add(favouriteCards);
        }
        content.add(gap(14));

		addCustomPetSection();
		content.add(gap(14));
		addSizeControl();
		content.add(gap(14));

        JPanel companionHeader = new JPanel(new BorderLayout(8, 0));
        companionHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
        companionHeader.setAlignmentX(LEFT_ALIGNMENT);
        companionHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        companionHeader.add(sectionLabel("Companions"), BorderLayout.WEST);

        JButton variantsButton = new JButton(config.filter() ? "All forms" : "Main pets");
        variantsButton.setToolTipText(config.filter() ? "Showing every pet and transmog" : "Showing the main pet catalogue");
        variantsButton.setFont(FontManager.getRunescapeSmallFont());
        variantsButton.setFocusPainted(false);
        variantsButton.addActionListener(event ->
        {
            configManager.setConfiguration(CONFIG_GROUP, "filter", !config.filter());
            refresh();
        });
        companionHeader.add(variantsButton, BorderLayout.EAST);
        content.add(companionHeader);
        content.add(gap(6));

        JPanel allCards = createIconGrid();
        Set<PetData> favouriteSet = new LinkedHashSet<>(favourites);
        PetData[] pets = config.filter() ? PetData.values() : PetData.petsToShow.toArray(new PetData[0]);
        for (PetData pet : pets)
        {
            if (favouriteSet.contains(pet))
            {
                continue;
            }
            allCards.add(createPetCard(pet, false));
        }
        content.add(allCards);
        content.add(gap(14));
        addThrallSection();
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

	private void addCustomPetSection()
	{
		addSectionTitle("Custom pets");

		List<Integer> customIds = getCustomNpcIds();
		if (!customIds.isEmpty())
		{
			JPanel cards = createIconGrid();
			for (int npcId : customIds)
			{
				cards.add(createCustomPetCard(npcId));
			}
			content.add(cards);
			content.add(gap(6));
		}

		JPanel addFields = new JPanel();
		addFields.setLayout(new BoxLayout(addFields, BoxLayout.Y_AXIS));
		addFields.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addFields.setAlignmentX(LEFT_ALIGNMENT);
		addFields.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
		JLabel nameLabel = new JLabel("Pet name (optional)");
		nameLabel.setForeground(Color.LIGHT_GRAY);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setAlignmentX(LEFT_ALIGNMENT);
		addFields.add(nameLabel);
		addFields.add(gap(2));
		JTextField nameField = new JTextField();
		nameField.setToolTipText("Choose a name for the custom pet");
		nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		nameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameField.setForeground(Color.WHITE);
		nameField.setCaretColor(Color.WHITE);
		nameField.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		addFields.add(nameField);
		addFields.add(gap(4));
		JLabel idLabel = new JLabel("NPC ID");
		idLabel.setForeground(Color.LIGHT_GRAY);
		idLabel.setFont(FontManager.getRunescapeSmallFont());
		idLabel.setAlignmentX(LEFT_ALIGNMENT);
		addFields.add(idLabel);
		addFields.add(gap(2));

		JPanel addRow = new JPanel(new BorderLayout(5, 0));
		addRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JTextField npcIdField = new JTextField();
		npcIdField.setToolTipText("Enter an NPC ID");
		npcIdField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		npcIdField.setForeground(Color.WHITE);
		npcIdField.setCaretColor(Color.WHITE);
		npcIdField.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JButton add = new JButton("Add");
		add.setToolTipText("Add this NPC as a custom pet");
		Runnable addNpc = () ->
		{
			final int npcId;
			try
			{
				npcId = Integer.parseInt(npcIdField.getText().trim());
			}
			catch (NumberFormatException ex)
			{
				JOptionPane.showMessageDialog(this, "Enter a numeric NPC ID.", "Invalid NPC ID", JOptionPane.WARNING_MESSAGE);
				return;
			}
			final String customName = sanitizeCustomName(nameField.getText());
			clientThread.invokeLater(() ->
			{
				CustomPetData custom = plugin.createCustomPet(npcId);
				SwingUtilities.invokeLater(() ->
				{
					if (custom == null)
					{
						JOptionPane.showMessageDialog(this, "NPC " + npcId + " has no usable model.",
								"Unable to add NPC", JOptionPane.WARNING_MESSAGE);
						return;
					}
					String displayName = customName.isEmpty() ? custom.getName() : customName;
					configManager.setConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".name", displayName);
					List<Integer> ids = getCustomNpcIds();
					if (!ids.contains(npcId))
					{
						ids.add(npcId);
						configManager.setConfiguration(CONFIG_GROUP, "customNpcIds", joinNpcIds(ids));
					}
					if (plugin.petData instanceof CustomPetData && plugin.petData.getNpcId() == npcId)
					{
						clientThread.invokeLater(() ->
						{
							CustomPetData renamed = plugin.createCustomPet(npcId);
							if (renamed != null)
							{
								plugin.updatePet(renamed);
							}
						});
					}
					nameField.setText("");
					npcIdField.setText("");
					refresh();
				});
			});
		};
		add.addActionListener(event -> addNpc.run());
		npcIdField.addActionListener(event -> addNpc.run());
		addRow.add(npcIdField, BorderLayout.CENTER);
		addRow.add(add, BorderLayout.EAST);
		addFields.add(addRow);
		content.add(addFields);
	}

	private void addSizeControl()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setAlignmentX(LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		header.add(sectionLabel("Pet size"), BorderLayout.WEST);
		JLabel value = new JLabel(config.sizePercent() + "%");
		value.setForeground(ACTIVE_BORDER);
		header.add(value, BorderLayout.EAST);
		content.add(header);

		JSlider slider = new JSlider(50, 200, config.sizePercent());
		slider.setBackground(ColorScheme.DARK_GRAY_COLOR);
		slider.setAlignmentX(LEFT_ALIGNMENT);
		slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		slider.setMajorTickSpacing(25);
		slider.setPaintTicks(true);
		slider.setToolTipText("Resize the selected pet from 50% to 200%");
		slider.addChangeListener(event ->
		{
			int size = slider.getValue();
			value.setText(size + "%");
			configManager.setConfiguration(CONFIG_GROUP, "sizePercent", size);
		});
		content.add(slider);
	}

	private JPanel createCustomPetCard(int npcId)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setPreferredSize(new Dimension(0, 58));
		boolean selected = plugin.petData instanceof CustomPetData && plugin.petData.getNpcId() == npcId;
		card.setBorder(BorderFactory.createLineBorder(selected ? ACTIVE_BORDER : INACTIVE_BORDER, selected ? 2 : 1));

		String savedName = configManager.getConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".name");
		String name = savedName == null || savedName.trim().isEmpty()
				? (selected ? plugin.petData.getName() : "NPC " + npcId) : savedName.trim();
		String shortName = name.length() > 10 ? name.substring(0, 9) + "…" : name;
		JButton choose = new JButton("<html><center>" + escapeHtml(shortName) + "<br><font color='#999999'>" + npcId + "</font></center></html>");
		choose.setFont(FontManager.getRunescapeSmallFont());
		choose.setForeground(Color.WHITE);
		choose.setFocusPainted(false);
		choose.setBorderPainted(false);
		choose.setContentAreaFilled(false);
		choose.setToolTipText("Select " + name);
		choose.addActionListener(event -> clientThread.invokeLater(() ->
		{
			CustomPetData custom = plugin.createCustomPet(npcId);
			if (custom != null)
			{
				plugin.updatePet(custom);
				SwingUtilities.invokeLater(this::refresh);
			}
		}));
		card.add(choose, BorderLayout.CENTER);

		JButton remove = new JButton("×");
		remove.setForeground(Color.GRAY);
		remove.setToolTipText("Remove custom pet");
		remove.setFocusPainted(false);
		remove.setBorderPainted(false);
		remove.setContentAreaFilled(false);
		remove.addActionListener(event -> removeCustomPet(npcId));
		card.add(remove, BorderLayout.EAST);
		return card;
	}

	private void removeCustomPet(int npcId)
	{
		List<Integer> ids = getCustomNpcIds();
		ids.remove(Integer.valueOf(npcId));
		configManager.setConfiguration(CONFIG_GROUP, "customNpcIds", joinNpcIds(ids));
		configManager.setConfiguration(CONFIG_GROUP, "customNpc." + npcId + ".name", "");
		if (config.selectedCustomNpcId() == npcId ||
				(plugin.petData instanceof CustomPetData && plugin.petData.getNpcId() == npcId))
		{
			clientThread.invokeLater(() -> plugin.updatePet(config.pet()));
		}
		refresh();
	}

	private List<Integer> getCustomNpcIds()
	{
		List<Integer> ids = new ArrayList<>();
		String configured = config.customNpcIds();
		if (configured == null || configured.trim().isEmpty())
		{
			return ids;
		}
		for (String value : configured.split(","))
		{
			try
			{
				int id = Integer.parseInt(value.trim());
				if (id >= 0 && !ids.contains(id))
				{
					ids.add(id);
				}
			}
			catch (NumberFormatException ignored)
			{
				// Ignore invalid values left in configuration.
			}
		}
		return ids;
	}

	private static String joinNpcIds(List<Integer> ids)
	{
		StringBuilder value = new StringBuilder();
		for (int id : ids)
		{
			if (value.length() > 0)
			{
				value.append(',');
			}
			value.append(id);
		}
		return value.toString();
	}

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String sanitizeCustomName(String value)
	{
		String cleaned = value == null ? "" : value.replace("<", "").replace(">", "").trim();
		return cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned;
	}

    private void addThrallSection()
    {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JButton collapse = new JButton(config.showThralls() ? "▾" : "▸");
        collapse.setToolTipText(config.showThralls() ? "Collapse thralls" : "Expand thralls");
        collapse.setFocusPainted(false);
        collapse.setBorderPainted(false);
        collapse.setContentAreaFilled(false);
        collapse.addActionListener(event ->
        {
            configManager.setConfiguration(CONFIG_GROUP, "showThralls", !config.showThralls());
            refresh();
        });
        header.add(collapse, BorderLayout.WEST);
        header.add(sectionLabel("Companion thralls"), BorderLayout.CENTER);

        JToggleButton enabled = new JToggleButton(config.companionThralls() ? "On" : "Off");
        enabled.setSelected(config.companionThralls());
        enabled.setFocusPainted(false);
        enabled.addActionListener(event ->
        {
            configManager.setConfiguration(CONFIG_GROUP, "companionThralls", !config.companionThralls());
            refresh();
        });
        header.add(enabled, BorderLayout.EAST);
        content.add(header);
        if (!config.showThralls())
        {
            return;
        }
        content.add(gap(6));

        JPanel thralls = new JPanel(new GridLayout(1, 3, 5, 0));
        thralls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        thralls.setAlignmentX(LEFT_ALIGNMENT);
        thralls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        thralls.add(createThrallCard("Melee", config.meleeThrall(), "meleeThrall"));
        thralls.add(createThrallCard("Range", config.rangeThrall(), "rangeThrall"));
        thralls.add(createThrallCard("Mage", config.mageThrall(), "mageThrall"));
        content.add(thralls);
    }

    private JPanel createThrallCard(String type, PetData pet, String configKey)
    {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createLineBorder(INACTIVE_BORDER));
        card.setToolTipText(type + ": " + pet.getName());
        JLabel icon = new JLabel(type, SwingConstants.CENTER);
        icon.setForeground(Color.WHITE);
        icon.setFont(FontManager.getRunescapeSmallFont());
        loadIcon(icon, pet, 36, 36);
        card.add(icon, BorderLayout.CENTER);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem useCurrent = new JMenuItem("Use current pet as " + type.toLowerCase() + " thrall");
        useCurrent.addActionListener(event ->
        {
            if (plugin.petData instanceof PetData)
            {
                configManager.setConfiguration(CONFIG_GROUP, configKey, plugin.petData);
                refresh();
            }
        });
        menu.add(useCurrent);
        card.setComponentPopupMenu(menu);
        icon.setComponentPopupMenu(menu);
        return card;
    }

    private JPanel createPetCard(PetData pet, boolean favouriteSection)
    {
        JPanel card = new JPanel(new BorderLayout(4, 0));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        card.setPreferredSize(new Dimension(0, 58));
        CompanionData currentPet = plugin.petData != null ? plugin.petData : config.pet();
        boolean selected = currentPet == pet;
        Border outside = BorderFactory.createLineBorder(selected ? ACTIVE_BORDER : INACTIVE_BORDER, selected ? 2 : 1);
        card.setBorder(BorderFactory.createCompoundBorder(outside,
                BorderFactory.createEmptyBorder(selected ? 4 : 5, 7, selected ? 4 : 5, 5)));

        JButton choose = new JButton();
        choose.setHorizontalAlignment(SwingConstants.CENTER);
        choose.setFocusPainted(false);
        choose.setBorderPainted(false);
        choose.setContentAreaFilled(false);
        choose.setForeground(Color.WHITE);
        choose.setToolTipText("Select " + pet.getName());
        loadIcon(choose, pet, 38, 38);
        choose.addActionListener(event -> clientThread.invokeLater(() ->
        {
            plugin.updatePet(pet);
            SwingUtilities.invokeLater(this::refresh);
        }));
        card.add(choose, BorderLayout.CENTER);

        boolean favourite = getFavourites().contains(pet);
        JButton star = new JButton(favourite ? "★" : "☆");
        star.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        star.setForeground(favourite ? FAVORITE_GOLD : Color.GRAY);
        star.setToolTipText(favourite ? "Remove from favourites" : "Add to favourites");
        star.setFocusPainted(false);
        star.setBorderPainted(false);
        star.setContentAreaFilled(false);
        star.addActionListener(event -> toggleFavourite(pet));
        card.add(star, BorderLayout.EAST);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem melee = new JMenuItem("Set as melee thrall");
        melee.addActionListener(event -> setThrall("meleeThrall", pet));
        JMenuItem range = new JMenuItem("Set as range thrall");
        range.addActionListener(event -> setThrall("rangeThrall", pet));
        JMenuItem mage = new JMenuItem("Set as mage thrall");
        mage.addActionListener(event -> setThrall("mageThrall", pet));
        menu.add(melee);
        menu.add(range);
        menu.add(mage);
        choose.setComponentPopupMenu(menu);
        if (favouriteSection)
        {
            card.setToolTipText("Favourite companion");
        }
        return card;
    }

    private void setThrall(String key, PetData pet)
    {
        configManager.setConfiguration(CONFIG_GROUP, key, pet);
        refresh();
    }

    private void toggleFavourite(PetData pet)
    {
        Set<PetData> favourites = new LinkedHashSet<>(getFavourites());
        if (!favourites.remove(pet))
        {
            favourites.add(pet);
        }
        StringBuilder value = new StringBuilder();
        for (PetData favourite : favourites)
        {
            if (value.length() > 0)
            {
                value.append(',');
            }
            value.append(favourite.name());
        }
        configManager.setConfiguration(CONFIG_GROUP, "favorites", value.toString());
        refresh();
    }

    private List<PetData> getFavourites()
    {
        List<PetData> favourites = new ArrayList<>();
        String configured = config.favorites();
        if (configured == null || configured.trim().isEmpty())
        {
            return favourites;
        }
        Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty()).forEach(value ->
        {
            try
            {
                PetData pet = PetData.valueOf(value);
                if (!favourites.contains(pet))
                {
                    favourites.add(pet);
                }
            }
            catch (IllegalArgumentException ignored)
            {
                // Ignore favourites left behind by removed or renamed pets.
            }
        });
        return favourites;
    }

    private JPanel createIconGrid()
    {
        JPanel cards = new JPanel(new GridLayout(0, 3, 5, 5));
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cards.setAlignmentX(LEFT_ALIGNMENT);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return cards;
    }

    private void addSectionTitle(String text)
    {
        content.add(sectionLabel(text));
        content.add(gap(6));
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static Component gap(int height)
    {
        return Box.createRigidArea(new Dimension(0, height));
    }

    private void loadIcon(AbstractButton button, PetData pet, int width, int height)
    {
        AsyncBufferedImage source = itemManager.getImage(pet.getIconID());
        Runnable update = () -> button.setIcon(new ImageIcon(resize(source, width, height)));
        source.onLoaded(() -> SwingUtilities.invokeLater(update));
        update.run();
    }

    private void loadIcon(JLabel label, PetData pet, int width, int height)
    {
        AsyncBufferedImage source = itemManager.getImage(pet.getIconID());
        Runnable update = () -> label.setIcon(new ImageIcon(resize(source, width, height)));
        source.onLoaded(() -> SwingUtilities.invokeLater(update));
        update.run();
    }

    private static BufferedImage resize(BufferedImage source, int width, int height)
    {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (source != null)
        {
            Graphics2D graphics = result.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
            graphics.dispose();
        }
        return result;
    }
}
