package nortantis.editor;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import hoten.voronoi.Center;
import hoten.voronoi.nodename.as3delaunay.Circle;
import nortantis.BGColorCancelHandler;
import nortantis.BGColorPreviewPanel;
import nortantis.MapSettings;
import nortantis.RunSwing;

public class LandOceanTool extends EditorTool
{

	private JPanel colorDisplay;
	private JPanel colorChooserPanel;
	private JRadioButton oceanButton;
	private BufferedImage map;
	private JRadioButton replaceColorButton;
	private JRadioButton paintColorButton;
	private JRadioButton landButton;

	public LandOceanTool(MapSettings settings, EditorDialog dialog)
	{
		super(settings, dialog);
	}

	@Override
	public String getToolbarName()
	{
		return "Land and Ocean";
	}

	@Override
	public void onBeforeSaving()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected JPanel createToolsOptionsPanel()
	{
		JPanel toolOptionsPanel = new JPanel();
		toolOptionsPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		toolOptionsPanel.setLayout(new BoxLayout(toolOptionsPanel, BoxLayout.Y_AXIS));
		
		// Tools
		JLabel toolsLabel = new JLabel("Brush:");
		List<JComponent> radioButtons = new ArrayList<>();
		ButtonGroup group = new ButtonGroup();
		oceanButton = new JRadioButton("Ocean");
	    group.add(oceanButton);
	    radioButtons.add(oceanButton);
	    toolOptionsPanel.add(oceanButton);
		ActionListener listener = new ActionListener() 
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (colorChooserPanel != null && settings.drawRegionColors)
				{
					colorChooserPanel.setVisible(!oceanButton.isSelected());
				}
			}
	    };
	    oceanButton.addActionListener(listener);
	    
	    if (settings.drawRegionColors)
	    {
			replaceColorButton = new JRadioButton("Replace Color");
		    group.add(replaceColorButton);
		    radioButtons.add(replaceColorButton);
		    replaceColorButton.addActionListener(listener);
			paintColorButton = new JRadioButton("Paint Color");
		    group.add(paintColorButton);
		    radioButtons.add(paintColorButton);
		    paintColorButton.addActionListener(listener);
	    }
	    else
	    {
			landButton = new JRadioButton("Land");
		    group.add(landButton);
		    radioButtons.add(landButton);
		    landButton.addActionListener(listener);
	    }
	    oceanButton.setSelected(true); // Selected by default
	    EditorTool.addLabelAndComponentsToPanel(toolOptionsPanel, toolsLabel, radioButtons);
	    
	    // Color chooser
	    if (settings.drawRegionColors)
	    {
		    JLabel colorLabel = new JLabel("Color:");
		    
		    colorDisplay = new JPanel();
		    colorDisplay.setPreferredSize(new Dimension(50, 25));
		    colorDisplay.setBackground(settings.landColor);
	    	
			JButton chooseButton = new JButton("Choose");
			chooseButton.setBounds(814, 314, 87, 25);
			chooseButton = new JButton("Choose");
			chooseButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					RunSwing.showColorPickerWithPreviewPanel(toolOptionsPanel, colorDisplay, "Text color");
				}
			});
			
			JPanel chooserPanel = new JPanel();
			chooserPanel.setLayout(new BoxLayout(chooserPanel, BoxLayout.X_AXIS));
			chooserPanel.add(colorDisplay);
			chooserPanel.add(Box.createHorizontalGlue());
			chooserPanel.add(chooseButton);
			
			colorChooserPanel = EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, colorLabel, chooserPanel);
	    }
	    listener.actionPerformed(null);

	    // Brush sizes
	    JLabel brushSizeLabel = new JLabel("Brush size:");
	    JComboBox<ImageIcon> brushSizeComboBox = new JComboBox<>();
	    List<Integer> brushSizes = Arrays.asList(1, 25, 70);
	    int largest = 70;
	    for (int brushSize : brushSizes)
	    {
	    	if (brushSize == 1)
	    	{
	    		brushSize = 4; // Needed to make it visible
	    	}
	    	BufferedImage image = new BufferedImage(largest, largest, BufferedImage.TYPE_INT_ARGB);
	    	Graphics2D g = image.createGraphics();
	    	g.setColor(Color.white);
	    	g.setColor(Color.black);
	    	g.fillOval(largest/2 - brushSize/2, largest/2 - brushSize/2, brushSize, brushSize);
	    	brushSizeComboBox.addItem(new ImageIcon(image));
	    }
	    EditorTool.addLabelAndComponentToPanel(toolOptionsPanel, brushSizeLabel, brushSizeComboBox);
	    

		return toolOptionsPanel;
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
		if (map != null) // If the map is visible ...
		{
			Center center = mapParts.graph.findClosestCenter(new hoten.geom.Point(e.getX(), e.getY()));
			if (center != null)
			{
				if (settings.drawRegionColors)
				{
					if (replaceColorButton.isSelected())
					{
						// TODO
					}
					else
					{
						CenterEdit edit = settings.edits.centerEdits.get(center.index);
						edit.isWater = oceanButton.isSelected();
						if (paintColorButton.isSelected())
						{
							edit.regionColor = colorDisplay.getBackground();
						}
					}
				}
				else
				{
					CenterEdit edit = settings.edits.centerEdits.get(center.index);
					edit.isWater = oceanButton.isSelected();
					edit.regionColor = settings.landColor; // This needs to be set in case the user switching draw region colors on later.
				}
				handleMapChange(center);
			}
		}
	}
	
	private void handleMapChange(Center center)
	{
		mapParts.graph.updateCoast(center);
		mapParts.graph.rebuildNoisyEdgesForCenter(center);
		createAndShowMap();
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onBeforeCreateMap()
	{
		// Change a few settings to make map creation faster.
		settings.resolution = zoom;
		settings.landBlur = 0;
		settings.oceanEffects = 0;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
	}

	@Override
	protected BufferedImage onBeforeShowMap(BufferedImage map)
	{
		this.map = map;
		return map;
	}

	@Override
	public void onSwitchingAway()
	{
		// TODO Auto-generated method stub
		
	}

}
