package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import nortantis.util.JFontChooser;
import nortantis.util.Pair;
import nortantis.util.Tuple2;

public class SwingHelper
{
	public static final int spaceBetweenRowsOfComponents = 8;
	public static final int borderWidthBetweenComponents = 4;
	public static final int sidePanelPreferredWidth = 300;
	public static final int sidePanelMinimumWidth = 300;
	private static final int sliderWidth = 170;
	private static final int rowVerticalInset = 10;
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 11; 
	
	private static int curY = 0;
	
	public static RowHider addLabelAndComponentToPanel(JPanel panelToAddTo, String labelText, String tooltip, JComponent component)
	{
		
		GridBagConstraints lc = new GridBagConstraints();
		lc.fill = GridBagConstraints.HORIZONTAL;
		lc.gridx = 0;
		lc.gridy = curY;
		lc.weightx = 0.4;
		lc.anchor = GridBagConstraints.NORTHEAST;
		lc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		panelToAddTo.add(createWrappingLabel(labelText, tooltip), lc);
		
		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 1;
		cc.gridy = curY;
		cc.weightx = 0.6;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		panelToAddTo.add(component, cc);
		
		curY++;
		
		return new RowHider(lc, cc);
	}
	
	private static Component createWrappingLabel(String text, String tooltip)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setToolTipText(tooltip);
		return label;
	}
	
	public static <T extends Component> RowHider addLabelAndComponentsToPanelVertical(JPanel panelToAddTo, String labelText, String tooltip, 
			List<T> components)
	{
		return addLabelAndComponentsToPanel(panelToAddTo, labelText, tooltip, BoxLayout.Y_AXIS, 0, components);
	}
	
	public static <T extends Component> RowHider addLabelAndComponentsToPanelHorizontal(JPanel panelToAddTo, String labelText, String tooltip, 
			int componentLeftPadding, List<T> components)
	{
		return addLabelAndComponentsToPanel(panelToAddTo, labelText, tooltip, BoxLayout.X_AXIS, componentLeftPadding, components);
	}
	
	private static <T extends Component> RowHider addLabelAndComponentsToPanel(JPanel panelToAddTo, String labelText, String tooltip, int boxLayoutDirection,
			int componentLeftPadding, List<T> components)
	{		
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, boxLayoutDirection));
		compPanel.add(Box.createHorizontalStrut(componentLeftPadding));
		for (Component comp : components)
		{
			compPanel.add(comp);
			if (boxLayoutDirection == BoxLayout.X_AXIS && comp != components.get(components.size() - 1))
			{
				compPanel.add(Box.createHorizontalStrut(10));
			}
		}
		compPanel.add(Box.createHorizontalGlue());
		
		return addLabelAndComponentToPanel(panelToAddTo, labelText, tooltip, compPanel);
	}
	
	public static Tuple2<JPanel, JScrollPane> createPanelForLabeledComponents()
	{
		JPanel panel = new VerticallyScrollablePanel();
		panel.setLayout(new GridBagLayout());
		JScrollPane scrollPane = new JScrollPane(panel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(sidePanelScrollSpeed);
		return new Tuple2<>(panel, scrollPane);
	}
	
	public static void resetGridY()
	{
		curY = 0;
	}
	
	public static void addVerticalFillerRow(JPanel panel)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.VERTICAL;
		c.gridx = 0;
		c.gridy = curY;
		c.weightx = 1.0;
		c.weighty = 1.0;
		
		JPanel filler = new JPanel();
		panel.add(filler, c);
		
		curY++;
	}

	public static void initializeComboBoxItems(JComboBox<String> comboBox, Collection<String> items, String selectedItem)
	{
		comboBox.removeAllItems();
		for (String item : items)
		{
			comboBox.addItem(item);
		}
		if (selectedItem != null && !selectedItem.isEmpty())
		{
			if (!items.contains(selectedItem))
			{
				comboBox.addItem(selectedItem);
			}
			comboBox.setSelectedItem(selectedItem);
		}
	}
	
	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		Color c = JColorChooser.showDialog(parent, "", colorDisplay.getBackground());
		if (c != null)
			colorDisplay.setBackground(c);
	}
	
	public static void addLeftAlignedComponent(JPanel parent, JComponent component)
	{
		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 0;
		cc.gridy = curY;
		cc.weightx = 1;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		parent.add(component, cc);
		
		curY++;
	}
	
	public static void addSeperator(JPanel panelToAddTo)
	{
		final int minHeight = 2;
		
		{
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 0;
			c.gridy = curY;
			c.weightx = 0.5;
			c.anchor = GridBagConstraints.LINE_START;
			c.insets = new Insets(0, 5, 0, 0);
			JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
			sep.setMinimumSize(new Dimension(0, minHeight));
			panelToAddTo.add(sep, c);
		}

		{
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 1;
			c.gridy = curY;
			c.weightx = 0.5;
			c.anchor = GridBagConstraints.LINE_START;
			c.insets = new Insets(0, 0, 0, 5);
			JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
			sep.setMinimumSize(new Dimension(0, minHeight));
			panelToAddTo.add(sep, c);
		}

		curY++;
	}
	
	public static void setSliderWidthForSidePanel(JSlider slider)
	{
		slider.setPreferredSize(new Dimension(sliderWidth, slider.getPreferredSize().height));
	}
	
	
	public static JPanel createColorPickerPreviewPanel()
	{
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(50, 25));
		panel.setBackground(Color.BLACK);
		return panel;
	}

	public static void showColorPicker(JComponent parent, final JPanel colorDisplay, String title)
	{
		final JColorChooser colorChooser = new JColorChooser(colorDisplay.getBackground());
		colorChooser.setPreviewPanel(new JPanel());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
			}

		};
		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);

	}
	
	public static Tuple2<JLabel, JButton> createFontChooser(JPanel panelToAddTo, String labelText, int height)
	{
		// TODO Make the choose button stay to the left when the display label gets big.
		final int spaceUnderFontDisplays = 4;
		JLabel fontDisplay = new JLabel("");
		JPanel displayHolder = new JPanel();
		displayHolder.setLayout(new BorderLayout());
		displayHolder.add(fontDisplay);
		displayHolder.setPreferredSize(new Dimension(displayHolder.getPreferredSize().width, height));

		final JButton chooseButton = new JButton("Choose");
		chooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				runFontChooser(panelToAddTo, fontDisplay);
			}
		});
		JPanel chooseButtonHolder = new JPanel();
		chooseButtonHolder.setLayout(new BoxLayout(chooseButtonHolder, BoxLayout.X_AXIS));
		chooseButtonHolder.add(chooseButton);
		chooseButtonHolder.add(Box.createHorizontalGlue());
		SwingHelper.addLabelAndComponentsToPanelVertical(panelToAddTo, labelText, "",
				Arrays.asList(displayHolder, Box.createVerticalStrut(spaceUnderFontDisplays), chooseButtonHolder));
		
		
		return new Tuple2<>(fontDisplay, chooseButton);
	}
	

	private static void runFontChooser(JComponent parent, JLabel fontDisplay)
	{
		JFontChooser fontChooser = new JFontChooser();
		fontChooser.setSelectedFont(fontDisplay.getFont());
		int status = fontChooser.showDialog(parent);
		if (status == JFontChooser.OK_OPTION)
		{
			Font font = fontChooser.getSelectedFont();
			fontDisplay.setText(font.getFontName());
			fontDisplay.setFont(font);
		}
	}
}
