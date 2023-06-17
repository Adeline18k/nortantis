package nortantis.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultFocusManager;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.imgscalr.Scalr.Method;

import hoten.geom.Rectangle;
import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import nortantis.DimensionDouble;
import nortantis.ImageCache;
import nortantis.MapCreator;
import nortantis.MapParts;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.RunSwing;
import nortantis.TextDrawer;
import nortantis.UserPreferences;
import nortantis.util.ImageHelper;
import nortantis.util.JComboBoxFixed;
import nortantis.util.Tuple2;

@SuppressWarnings("serial")
public class EditorFrame extends JFrame
{
	JScrollPane scrollPane;
	EditorTool currentTool;
	List<EditorTool> tools;
	public static final int borderWidthBetweenComponents = 4;
	public static final int toolsPanelWidth = 280;
	// Controls how large 100% zoom is, in pixels.
	final double oneHundredPercentMapWidth = 4096;
	private JPanel toolsOptionsPanelContainer;
	private JPanel currentToolOptionsPanel;
	private JComboBox<String> zoomComboBox;
	public MapEditingPanel mapEditingPanel;
	boolean areToolToggleButtonsEnabled = true;
	JMenuItem undoButton;
	JMenuItem redoButton;
	private TitledBorder toolOptionsPanelBorder;
	private JMenuItem clearEntireMapButton;
	private boolean hadEditsAtStartup;
	public boolean isMapReadyForInteractions;
	public Undoer undoer;
	MapParts mapParts;
	private List<String> zoomLevels;
	double zoom;
	double imageQualityScale;
	private boolean mapNeedsFullRedraw;
	private ArrayDeque<IncrementalUpdate> incrementalUpdatesToDraw;
	private boolean isMapBeingDrawn;
	private ReentrantLock drawLock;
	MapSettings settings;
	private JProgressBar progressBar;
	private Timer progressBarTimer;
	private JPanel toolsPanel;
	private JPanel bottomPanel;
	private RunSwing parent;
	private final String fitToWindowZoomLevel = "Fit to Window";
	private JMenu imageQualityMenu;
	private JRadioButtonMenuItem radioButton75Percent;
	private JRadioButtonMenuItem radioButton100Percent;
	private JRadioButtonMenuItem radioButton125Percent;

	/**
	 * Creates a dialog for editing text.
	 * 
	 * @param settings
	 *            Settings for the map. The user's edits will be stored in
	 *            settings.edits. Other fields in settings may be modified in
	 *            the editing process and will not be stored after the editor
	 *            closes.
	 * @throws IOException
	 */
	public EditorFrame(final MapSettings settings, final RunSwing runSwing)
	{
		hadEditsAtStartup = !settings.edits.isEmpty();
		if (settings.edits.isEmpty())
		{
			// This is in case the user closes the editor before it finishes
			// generating the first time. This
			// assignment causes the edits being created by the generator to be
			// discarded. This is necessary
			// to prevent a case where there are edits but the UI doesn't
			// realize it.
			settings.edits = new MapEdits();
		}

		if (!hadEditsAtStartup)
		{
			settings.edits.bakeGeneratedTextAsEdits = true;
		}
		this.settings = settings;
		this.parent = runSwing;
		updateEditsPointerInRunSwing();

		final EditorFrame thisFrame = this;
		setBounds(100, 100, 1122, 701);

		getContentPane().setLayout(new BorderLayout());

		mapEditingPanel = new MapEditingPanel(null, this);
		mapEditingPanel.setLayout(new BorderLayout());

		mapEditingPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					currentTool.handleMouseClickOnMap(e);
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					currentTool.handleMousePressedOnMap(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					currentTool.handleMouseReleasedOnMap(e);
				}
			}

		});

		mapEditingPanel.addMouseMotionListener(new MouseMotionListener()
		{

			@Override
			public void mouseMoved(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					currentTool.handleMouseMovedOnMap(e);
				}
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					currentTool.handleMouseDraggedOnMap(e);
				}
			}
		});

		mapEditingPanel.addMouseListener(new MouseListener()
		{

			@Override
			public void mouseReleased(MouseEvent e)
			{
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (isMapReadyForInteractions)
				{
					currentTool.handleMouseExitedMap(e);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
			}
		});

		setupMenuBar(runSwing, thisFrame);
		undoer = new Undoer(settings, this);
		undoer.updateUndoRedoEnabled();

		// Setup tools
		tools = Arrays.asList(new LandWaterTool(this), new IconTool(this), new TextTool(this));
		if (UserPreferences.getInstance().lastEditorTool != "")
		{
			for (EditorTool tool : tools)
			{
				if (tool.getToolbarName().equals(UserPreferences.getInstance().lastEditorTool))
				{
					currentTool = tool;
				}
			}
		}
		if (currentTool == null)
		{
			currentTool = tools.get(2);
		}

		scrollPane = new JScrollPane(mapEditingPanel);
		// Speed up the scroll speed.
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		getContentPane().add(scrollPane);

		toolsPanel = new JPanel();
		toolsPanel.setPreferredSize(new Dimension(toolsPanelWidth, getContentPane().getHeight()));
		toolsPanel.setLayout(new BoxLayout(toolsPanel, BoxLayout.Y_AXIS));
		getContentPane().add(toolsPanel, BorderLayout.EAST);

		JPanel toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(toolsPanelWidth, 20));
		toolSelectPanel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Tools"));
		toolsPanel.add(toolSelectPanel);
		for (EditorTool tool : tools)
		{
			JToggleButton toolButton = new JToggleButton();
			try
			{
				toolButton.setIcon(new ImageIcon(tool.getImageIconFilePath()));
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			toolButton.setMaximumSize(new Dimension(50, 50));
			tool.setToggleButton(toolButton);
			toolButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleToolSelected(tool);
				}
			});
			toolSelectPanel.add(toolButton);
		}

		toolsOptionsPanelContainer = new JPanel();
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.add(currentToolOptionsPanel);
		toolsPanel.add(toolsOptionsPanelContainer);
		toolOptionsPanelBorder = BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
				currentTool.getToolbarName() + " Options");
		toolsOptionsPanelContainer.setBorder(toolOptionsPanelBorder);

		JPanel progressAndBottomPanel = new JPanel();
		progressAndBottomPanel.setLayout(new BoxLayout(progressAndBottomPanel, BoxLayout.Y_AXIS));
		// Progress bar
		JPanel progressBarPanel = new JPanel();
		progressBarPanel.setLayout(new BoxLayout(progressBarPanel, BoxLayout.X_AXIS));
		progressBarPanel.setBorder(BorderFactory.createEmptyBorder(0, borderWidthBetweenComponents - 2, 0, borderWidthBetweenComponents));
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Drawing...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		progressBarPanel.add(progressBar);
		progressAndBottomPanel.add(progressBarPanel);

		// Setup bottom panel
		bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(borderWidthBetweenComponents, borderWidthBetweenComponents,
				borderWidthBetweenComponents, borderWidthBetweenComponents));

		JLabel lblZoom = new JLabel("Zoom:");
		bottomPanel.add(lblZoom);
		lblZoom.setToolTipText("Zoom the map in or out (CTRL + mouse wheel). To view more details at higher zoom levels, adjust View > Image Quality.");

		zoomLevels = Arrays.asList(new String[] { fitToWindowZoomLevel, "50%", "75%", "100%", "125%" });
		zoomComboBox = new JComboBoxFixed<>();
		for (String level : zoomLevels)
		{
			zoomComboBox.addItem(level);
		}
		if (UserPreferences.getInstance().zoomLevel != "" && zoomLevels.contains(UserPreferences.getInstance().zoomLevel))
		{
			zoomComboBox.setSelectedItem(UserPreferences.getInstance().zoomLevel);
		}
		else
		{
			final String defaultZoomLevel = "50%";
			zoomComboBox.setSelectedItem(defaultZoomLevel);
			UserPreferences.getInstance().zoomLevel = defaultZoomLevel;
		}

		bottomPanel.add(zoomComboBox);
		zoomComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				UserPreferences.getInstance().zoomLevel = (String) zoomComboBox.getSelectedItem();
				updateDisplayedMapFromGeneratedMap(true);
				mapEditingPanel.repaint();
				mapEditingPanel.revalidate();
			}
		});

		bottomPanel.add(Box.createHorizontalGlue());

		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				thisFrame.dispatchEvent(new WindowEvent(thisFrame, WindowEvent.WINDOW_CLOSING));
			}
		});

		bottomPanel.add(doneButton);

		progressAndBottomPanel.add(bottomPanel);
		toolsPanel.add(progressAndBottomPanel);

		ActionListener listener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				progressBar.setVisible(isMapBeingDrawn);
			}
		};
		progressBarTimer = new Timer(50, listener);
		progressBarTimer.setInitialDelay(500);

		// Using KeyEventDispatcher instead of KeyListener makes the keys work
		// when any component is focused.
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			private boolean isSaving;

			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_S) && e.isControlDown())
				{
					// Prevent multiple "save as" popups.
					if (isSaving)
					{
						return false;
					}
					isSaving = true;

					try
					{
						// Save
						runSwing.saveSettings(scrollPane);
					}
					finally
					{
						isSaving = false;
					}
				}
				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
				UserPreferences.getInstance().lastEditorTool = currentTool.getToolbarName();
				currentTool.onSwitchingAway();

				if (settings.edits.isEmpty())
				{
					// This happens if the user closes the editor before it
					// finishes generating the first time. This
					// assignment causes the edits being created by the
					// generator to be discarded. This is necessary
					// to prevent a case where there are edits but the UI
					// doesn't realize it.
					settings.edits = new MapEdits();
				}
				else
				{
					// Undo and redo assign edits, so re-assign the pointer in
					// RunSwing.
					updateEditsPointerInRunSwing();
				}

				runSwing.frame.setEnabled(true);
				runSwing.updateFieldsWhenEditsChange();
				if (!hadEditsAtStartup && !settings.edits.isEmpty())
				{
					showMapChangesMessage(runSwing);
				}
			}
		});

		addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				if (fitToWindowZoomLevel.equals((String) zoomComboBox.getSelectedItem()))
				{
					createAndShowMapIncrementalUsingCenters(null);
				}
			}
		});

		drawLock = new ReentrantLock();
		incrementalUpdatesToDraw = new ArrayDeque<>();

		handleToolSelected(currentTool);
		updateImageQualityScale(UserPreferences.getInstance().editorImageQuality);
		updateDisplayedMapFromGeneratedMap(false);
		mapEditingPanel.repaint();
		handleImageQualityChange(UserPreferences.getInstance().editorImageQuality);
	}
	
	public void handleMouseWheelChangingZoom(MouseWheelEvent e)
	{
		if (isMapReadyForInteractions)
		{
			int scrollDirection = e.getUnitsToScroll() > 0 ? -1 : 1;
			int newIndex = zoomComboBox.getSelectedIndex() + scrollDirection;
			if (newIndex < 0)
			{
				newIndex = 0;
			}
			else if (newIndex > zoomComboBox.getItemCount() - 1)
			{
				newIndex = zoomComboBox.getItemCount() - 1;
			}
			if (newIndex != zoomComboBox.getSelectedIndex())
			{
				zoomComboBox.setSelectedIndex(newIndex);
				updateDisplayedMapFromGeneratedMap(true);
			}
		}
	}

	public void updateDisplayedMapFromGeneratedMap(boolean updateScrollLocationIfZoomChanged)
	{
		double oldZoom = zoom;
		zoom = translateZoomLevel((String) zoomComboBox.getSelectedItem());
				
		if (mapEditingPanel.mapFromMapCreator != null)
		{
			java.awt.Rectangle scrollTo = null;

			if (updateScrollLocationIfZoomChanged && zoom != oldZoom)
			{
				java.awt.Rectangle visible = mapEditingPanel.getVisibleRect();
				double scale = zoom / oldZoom;
				java.awt.Point mousePosition = mapEditingPanel.getMousePosition();
				if (mousePosition != null && (zoom > oldZoom))
				{
					// Zoom toward the mouse's position, keeping the point currently under the mouse the same if possible.
					scrollTo = new java.awt.Rectangle(
							(int)(mousePosition.x * scale) - mousePosition.x + visible.x, 
							(int)(mousePosition.y * scale) - mousePosition.y + visible.y, 
							visible.width, 
							visible.height);
					// If I should change my mind and want the scroll to put the mouse position into the center of the screen, then here's that code:
//					java.awt.Point target = new java.awt.Point((int)(mousePosition.x * scale),(int)(mousePosition.y * scale));
//					scrollTo = new java.awt.Rectangle(
//							target.x - visible.width/2, 
//							target.y - visible.height/2, 
//							visible.width, 
//							visible.height);
				}
				else
				{
					// Zoom toward or away from the current center of the screen.
					java.awt.Point 	currentCentroid = new java.awt.Point(visible.x + (visible.width / 2), visible.y + (visible.height / 2));
					java.awt.Point targetCentroid = new java.awt.Point((int)(currentCentroid.x * scale),(int)(currentCentroid.y * scale));
					scrollTo = new java.awt.Rectangle(
							targetCentroid.x - visible.width/2, 
							targetCentroid.y - visible.height/2, 
							visible.width, 
							visible.height);
				}
			}

			BufferedImage mapWithExtraStuff = currentTool.onBeforeShowMap(mapEditingPanel.mapFromMapCreator);
			mapEditingPanel.setZoom(zoom);
			mapEditingPanel.image = ImageHelper.scaleByWidth(mapWithExtraStuff, (int) (mapWithExtraStuff.getWidth() * zoom),
					Method.BALANCED);
			
			if (scrollTo != null)
			{
				// For some reason I have to do a whole bunch of revalidation or else scrollRectToVisible doesn't realize the map has changed size.
				mapEditingPanel.revalidate();
				scrollPane.revalidate();
				this.revalidate();
				
				mapEditingPanel.scrollRectToVisible(scrollTo);
			}
		}
	}

	private double translateZoomLevel(String zoomLevel)
	{
		if (zoomLevel == null)
		{
			return 1.0;
		}
		else if (zoomLevel.equals(fitToWindowZoomLevel))
		{
			if (mapEditingPanel.mapFromMapCreator != null)
			{
				final int additionalWidthToRemoveIDontKnowWhereItsCommingFrom = 2;
				Dimension size = new Dimension(scrollPane.getSize().width - additionalWidthToRemoveIDontKnowWhereItsCommingFrom,
						scrollPane.getSize().height - additionalWidthToRemoveIDontKnowWhereItsCommingFrom);

				DimensionDouble fitted = ImageHelper.fitDimensionsWithinBoundingBox(size, mapEditingPanel.mapFromMapCreator.getWidth(),
						mapEditingPanel.mapFromMapCreator.getHeight());
				return fitted.getWidth() / mapEditingPanel.mapFromMapCreator.getWidth();
			}
			else
			{
				return 1.0;
			}
		}
		else
		{
			double percentage = parsePercentage(zoomLevel);
			if (mapEditingPanel.mapFromMapCreator != null)
			{
				return (oneHundredPercentMapWidth * percentage) / mapEditingPanel.mapFromMapCreator.getWidth();
			}
			else
			{
				return 1.0;
			}
		}
	}

	/**
	 * Updates the pointer to the edits in the parent GUI to the edits being
	 * used in the editor. This is necessary so that when you either save in the
	 * editor or close the editor and then save, the edits are stored/saved.
	 * Note that undo/redo create deep copies of the edits, which means they
	 * have to update this pointer.
	 */
	public void updateEditsPointerInRunSwing()
	{
		parent.edits = settings.edits;
	}

	private void showMapChangesMessage(RunSwing runSwing)
	{
		if (!UserPreferences.getInstance().hideMapChangesWarning)
		{
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.add(new JLabel("<html>Some fields in the generator are now disabled to ensure your map remains"
					+ "<br>compatible with your edits. If a field is disabled for this reason, a message is added"
					+ "<br>to the field's tool tip. If you wish to enable those fields, you can either clear your "
					+ "<br>edits (Editor > Clear Edits), or create a new random map by going to File > New.</html>"));
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(runSwing.frame, panel, "", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideMapChangesWarning = checkBox.isSelected();
		}
	}

	private void setupMenuBar(RunSwing runSwing, final EditorFrame thisFrame)
	{
		JMenuBar menuBar = new JMenuBar();
		this.getContentPane().add(menuBar, BorderLayout.NORTH);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmSave = new JMenuItem("Save");
		mnFile.add(mntmSave);
		mntmSave.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
		mntmSave.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				runSwing.saveSettings(mntmSave);
			}
		});

		JMenuItem mntmSaveAs = new JMenuItem("Save As...");
		mnFile.add(mntmSaveAs);
		mntmSaveAs.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent arg0)
			{
				runSwing.saveSettingsAs(mntmSaveAs);
			}
		});

		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);

		undoButton = new JMenuItem("Undo");
		mnEdit.add(undoButton);
		undoButton.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
		undoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (currentTool != null)
				{
					undoer.undo();
				}
				undoer.updateUndoRedoEnabled();
			}
		});

		redoButton = new JMenuItem("Redo");
		mnEdit.add(redoButton);
		redoButton.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
		redoButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (currentTool != null)
				{
					undoer.redo();
				}
				undoer.updateUndoRedoEnabled();
			}
		});

		clearEntireMapButton = new JMenuItem("Clear Entire Map");
		mnEdit.add(clearEntireMapButton);
		clearEntireMapButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				clearEntireMap();
			}
		});
		clearEntireMapButton.setEnabled(false);

		JMenu mnView = new JMenu("View");
		menuBar.add(mnView);

		imageQualityMenu = new JMenu("Image Quality");
		mnView.add(imageQualityMenu);
		imageQualityMenu.setToolTipText("Change the quality of the map displayed in the editor. Does not apply when exporting the map to an image using the Generate button in the main window. Higher values make the editor slower.");

		ActionListener resolutionListener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				String text = ((JRadioButtonMenuItem) e.getSource()).getText();
				UserPreferences.getInstance().editorImageQuality = text;
				thisFrame.handleImageQualityChange(text);
				
				showImageQualityMessage();
			}
		};

		ButtonGroup resolutionButtonGroup = new ButtonGroup();

		radioButton75Percent = new JRadioButtonMenuItem("Low");
		radioButton75Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton75Percent);
		resolutionButtonGroup.add(radioButton75Percent);

		radioButton100Percent = new JRadioButtonMenuItem("Medium");
		radioButton100Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton100Percent);
		resolutionButtonGroup.add(radioButton100Percent);

		radioButton125Percent = new JRadioButtonMenuItem("High");
		radioButton125Percent.addActionListener(resolutionListener);
		imageQualityMenu.add(radioButton125Percent);
		resolutionButtonGroup.add(radioButton125Percent);

		if (UserPreferences.getInstance().editorImageQuality != null && !UserPreferences.getInstance().editorImageQuality.equals(""))
		{
			boolean found = false;
			for (JRadioButtonMenuItem resolutionOption : new JRadioButtonMenuItem[] { radioButton75Percent, radioButton100Percent, radioButton125Percent })
			{
				if (UserPreferences.getInstance().editorImageQuality.equals(resolutionOption.getText()))
				{
					resolutionOption.setSelected(true);
					found = true;
					break;
				}
			}
			if (!found)
			{
				// Shouldn't happen. This means the user preferences have a bad
				// value.
				radioButton100Percent.setSelected(true);
				UserPreferences.getInstance().editorImageQuality = radioButton100Percent.getText();
			}
		}
		else
		{
			radioButton100Percent.setSelected(true);
			UserPreferences.getInstance().editorImageQuality = radioButton100Percent.getText();
		}
	}
	
	private void showImageQualityMessage()
	{
		if (!UserPreferences.getInstance().hideImageQualityMessage)
		{
			Dimension size = new Dimension(400, 115);
			JPanel panel = new JPanel();
			panel.setPreferredSize(size);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			JLabel label = new JLabel("<html>Changing the image quality in the editor is only for the purpose of either speeding"
					+ " up the editor by using a lower quality, or viewing more details at higher zoom levels using a higher quality."
					+ " It does not affect the quality of the map exported to an image when you press the Generate button in the"
					+ " main window.</html>");
			panel.add(label);
			label.setMaximumSize(size);
			panel.add(Box.createVerticalStrut(18));
			JCheckBox checkBox = new JCheckBox("Don't show this message again.");
			panel.add(checkBox);
			JOptionPane.showMessageDialog(this, panel, "Tip", JOptionPane.INFORMATION_MESSAGE);
			UserPreferences.getInstance().hideImageQualityMessage = checkBox.isSelected();
		}
	}

	public void handleToolSelected(EditorTool selectedTool)
	{
		enableOrDisableToolToggleButtonsAndZoom(false);

		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.clearSelectedCenters();
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.hideBrush();
		currentTool.onSwitchingAway();
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
		toolOptionsPanelBorder.setTitle(currentTool.getToolbarName() + " Options");
		toolsOptionsPanelContainer.remove(currentToolOptionsPanel);
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.add(currentToolOptionsPanel);
		toolsOptionsPanelContainer.revalidate();
		toolsOptionsPanelContainer.repaint();
		if (mapEditingPanel.mapFromMapCreator != null)
		{
			updateDisplayedMapFromGeneratedMap(false);
		}
		currentTool.onActivate();
		mapEditingPanel.repaint();
		enableOrDisableToolToggleButtonsAndZoom(true);
	}

	public void enableOrDisableToolToggleButtonsAndZoom(boolean enable)
	{
		areToolToggleButtonsEnabled = enable;
		for (EditorTool tool : tools)
		{
			tool.setToggleButtonEnabled(enable);
		}
		zoomComboBox.setEnabled(enable);
		clearEntireMapButton.setEnabled(enable);
		if (imageQualityMenu != null)
		{
			radioButton75Percent.setEnabled(enable);
			radioButton100Percent.setEnabled(enable);
			radioButton125Percent.setEnabled(enable);
		}

		if (enable)
		{
			progressBarTimer.stop();
			progressBar.setVisible(false);
		}
		else
		{
			progressBarTimer.start();
		}
	}

	private double parsePercentage(String zoomStr)
	{
		double zoomPercent = Double.parseDouble(zoomStr.substring(0, zoomStr.length() - 1));
		return zoomPercent / 100.0;
	}

	private class IncrementalUpdate
	{
		public IncrementalUpdate(Set<Center> centersChanged, Set<Edge> edgesChanged)
		{
			if (centersChanged != null)
			{
				this.centersChanged = new HashSet<Center>(centersChanged);
			}
			if (edgesChanged != null)
			{
				this.edgesChanged = new HashSet<Edge>(edgesChanged);
			}
		}

		Set<Center> centersChanged;
		Set<Edge> edgesChanged;

		public void add(IncrementalUpdate other)
		{
			if (other == null)
			{
				return;
			}

			if (centersChanged != null && other.centersChanged != null)
			{
				centersChanged.addAll(other.centersChanged);
			}
			else if (centersChanged == null && other.centersChanged != null)
			{
				centersChanged = new HashSet<>(other.centersChanged);
			}

			if (edgesChanged != null && other.edgesChanged != null)
			{
				edgesChanged.addAll(other.edgesChanged);
			}
			else if (edgesChanged == null && other.edgesChanged != null)
			{
				edgesChanged = new HashSet<>(other.edgesChanged);
			}
		}
	}

	public void updateChangedCentersOnMap(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}

	public void updateChangedEdgesOnMap(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}

	/**
	 * Redraws the map, then displays it. Use only with UpdateType.Full and
	 * UpdateType.Quick.
	 */
	public void createAndShowMapFull()
	{
		createAndShowMap(UpdateType.Full, null, null);
	}

	public void createAndShowMapIncrementalUsingCenters(Set<Center> centersChanged)
	{
		createAndShowMap(UpdateType.Incremental, centersChanged, null);
	}

	public void createAndShowMapIncrementalUsingEdges(Set<Edge> edgesChanged)
	{
		createAndShowMap(UpdateType.Incremental, null, edgesChanged);
	}

	public void createAndShowMapFromChange(MapChange change)
	{
		if (change.updateType == UpdateType.Full)
		{
			createAndShowMapFull();
		}
		else
		{
			Set<Center> centersChanged = getCentersWithChangesInEdits(change.edits);
			Set<Edge> edgesChanged = null;
			// Currently createAndShowMap doesn't support drawing both center
			// edits and edge edits at the same time, so there is no
			// need to find edges changed if centers were changed.
			if (centersChanged.size() == 0)
			{
				edgesChanged = getEdgesWithChangesInEdits(change.edits);
			}
			createAndShowMap(UpdateType.Incremental, centersChanged, edgesChanged);
		}
	}

	private Set<Center> getCentersWithChangesInEdits(MapEdits changeEdits)
	{
		Set<Center> changedCenters = settings.edits.centerEdits.stream()
				.filter(cEdit -> !cEdit.equals(changeEdits.centerEdits.get(cEdit.index)))
				.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());

		Set<RegionEdit> regionChanges = settings.edits.regionEdits.values().stream()
				.filter(rEdit -> !rEdit.equals(changeEdits.regionEdits.get(rEdit.regionId))).collect(Collectors.toSet());
		for (RegionEdit rEdit : regionChanges)
		{
			Set<Center> regionCenterEdits = changeEdits.centerEdits.stream()
					.filter(cEdit -> cEdit.regionId != null && cEdit.regionId == rEdit.regionId)
					.map(cEdit -> mapParts.graph.centers.get(cEdit.index)).collect(Collectors.toSet());
			changedCenters.addAll(regionCenterEdits);
		}

		return changedCenters;
	}

	private Set<Edge> getEdgesWithChangesInEdits(MapEdits changeEdits)
	{
		return settings.edits.edgeEdits.stream().filter(eEdit -> !eEdit.equals(changeEdits.edgeEdits.get(eEdit.index)))
				.map(eEdit -> mapParts.graph.edges.get(eEdit.index)).collect(Collectors.toSet());
	}

	/**
	 * Redraws the map, then displays it
	 */
	private void createAndShowMap(UpdateType updateType, Set<Center> centersChanged, Set<Edge> edgesChanged)
	{
		if (isMapBeingDrawn)
		{
			if (updateType == UpdateType.Full)
			{
				mapNeedsFullRedraw = true;
				incrementalUpdatesToDraw.clear();
			}
			else if (updateType == UpdateType.Incremental)
			{
				incrementalUpdatesToDraw.add(new IncrementalUpdate(centersChanged, edgesChanged));
			}
			return;
		}

		isMapBeingDrawn = true;
		enableOrDisableToolToggleButtonsAndZoom(false);

		if (updateType == UpdateType.Full)
		{
			adjustSettingsForEditor();
		}

		SwingWorker<Tuple2<BufferedImage, Rectangle>, Void> worker = new SwingWorker<Tuple2<BufferedImage, Rectangle>, Void>()
		{
			@Override
			public Tuple2<BufferedImage, Rectangle> doInBackground() throws IOException
			{
				drawLock.lock();
				try
				{
					if (updateType == UpdateType.Full)
					{
						if (mapParts == null)
						{
							mapParts = new MapParts();
						}
						BufferedImage map = new MapCreator().createMap(settings, null, mapParts);
						System.gc();
						return new Tuple2<>(map, null);
					}
					else
					{
						BufferedImage map = mapEditingPanel.mapFromMapCreator;
						// Incremental update
						if (centersChanged != null && centersChanged.size() > 0)
						{
							Rectangle replaceBounds = new MapCreator().incrementalUpdateCenters(settings, mapParts, map, centersChanged);
							return new Tuple2<>(map, replaceBounds);
						}
						else if (edgesChanged != null && edgesChanged.size() > 0)
						{
							Rectangle replaceBounds = new MapCreator().incrementalUpdateEdges(settings, mapParts, map, edgesChanged);
							return new Tuple2<>(map, replaceBounds);
						}
						else
						{
							// Nothing to do.
							return new Tuple2<>(map, null);
						}
					}
				}
				finally
				{
					drawLock.unlock();
				}
			}

			@Override
			public void done()
			{
				Rectangle replaceBounds = null;
				try
				{
					Tuple2<BufferedImage, Rectangle> tuple = get();
					mapEditingPanel.mapFromMapCreator = tuple.getFirst();
					replaceBounds = tuple.getSecond();
				}
				catch (InterruptedException ex)
				{
					throw new RuntimeException(ex);
				}
				catch (Exception ex)
				{
					if (isCausedByOutOfMemoryError(ex))
					{
						String outOfMemoryMessage = "Out of memory. Try lowering the zoom or allocating more memory to the Java heap space.";
						JOptionPane.showMessageDialog(null, outOfMemoryMessage, "Error", JOptionPane.ERROR_MESSAGE);
						ex.printStackTrace();
					}
					else
					{
						JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
						ex.printStackTrace();
					}
				}

				if (mapEditingPanel.mapFromMapCreator != null)
				{
					mapEditingPanel.setGraph(mapParts.graph);

					initializeCenterEditsIfEmpty();
					initializeRegionEditsIfEmpty();
					initializeEdgeEditsIfEmpty();

					if (undoer.copyOfEditsWhenEditorWasOpened == null)
					{
						// This has to be done after the map is drawn rather
						// than when the editor frame is first created because
						// the first time the map is drawn is when the edits are
						// created.
						undoer.copyOfEditsWhenEditorWasOpened = settings.edits.deepCopy();
					}

					updateDisplayedMapFromGeneratedMap(false);

					enableOrDisableToolToggleButtonsAndZoom(true);

					isMapBeingDrawn = false;
					if (mapNeedsFullRedraw)
					{
						createAndShowMapFull();
					}
					else if (updateType == UpdateType.Incremental && incrementalUpdatesToDraw.size() > 0)
					{
						IncrementalUpdate incrementalUpdate = combineAndGetNextIncrementalUpdateToDraw();
						createAndShowMap(UpdateType.Incremental, incrementalUpdate.centersChanged, incrementalUpdate.edgesChanged);
					}

					if (updateType == UpdateType.Full)
					{
						mapNeedsFullRedraw = false;
					}

					mapEditingPanel.repaint();
					// Tell the scroll pane to update itself.
					mapEditingPanel.revalidate();
					isMapReadyForInteractions = true;
				}
				else
				{
					enableOrDisableToolToggleButtonsAndZoom(true);
					mapEditingPanel.clearSelectedCenters();
					isMapBeingDrawn = false;
				}
			}

		};
		worker.execute();
	}

	/**
	 * Combines the incremental updates in incrementalUpdatesToDraw so they can
	 * be drawn together. Clears out incrementalUpdatesToDraw.
	 * 
	 * @return The combined update to draw
	 */
	private IncrementalUpdate combineAndGetNextIncrementalUpdateToDraw()
	{
		if (incrementalUpdatesToDraw.size() == 0)
		{
			return null;
		}

		IncrementalUpdate result = incrementalUpdatesToDraw.pop();
		if (incrementalUpdatesToDraw.size() == 1)
		{
			return result;
		}

		while (incrementalUpdatesToDraw.size() > 0)
		{
			IncrementalUpdate next = incrementalUpdatesToDraw.pop();
			result.add(next);
		}
		return result;
	}

	private boolean isCausedByOutOfMemoryError(Throwable ex)
	{
		if (ex == null)
		{
			return false;
		}

		if (ex instanceof OutOfMemoryError)
		{
			return true;
		}

		return isCausedByOutOfMemoryError(ex.getCause());
	}

	private void initializeCenterEditsIfEmpty()
	{
		if (settings.edits.centerEdits.isEmpty())
		{
			settings.edits.initializeCenterEdits(mapParts.graph.centers, mapParts.iconDrawer);
		}
	}

	private void initializeEdgeEditsIfEmpty()
	{
		if (settings.edits.edgeEdits.isEmpty())
		{
			settings.edits.initializeEdgeEdits(mapParts.graph.edges);
		}
	}

	private void initializeRegionEditsIfEmpty()
	{
		if (settings.edits.regionEdits.isEmpty())
		{
			settings.edits.initializeRegionEdits(mapParts.graph.regions.values());
		}
	}

	/**
	 * Handles when zoom level changes in the display.
	 */
	public void handleImageQualityChange(String resolutionText)
	{
		updateImageQualityScale(resolutionText);
		
		isMapReadyForInteractions = false;
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.repaint();

		ImageCache.getInstance().clear();
		mapParts = null;
		createAndShowMapFull();
	}
	
	private void updateImageQualityScale(String imageQualityText)
	{
		if (imageQualityText.equals(radioButton75Percent.getText()))
		{
			imageQualityScale = 0.75;
		}
		else if (imageQualityText.equals(radioButton100Percent.getText()))
		{
			imageQualityScale = 1.0;
		}
		else if (imageQualityText.equals(radioButton125Percent.getText()))
		{
			imageQualityScale = 1.25;
		}
	}

	public void clearEntireMap()
	{
		if (mapParts == null || mapParts.graph == null)
		{
			return;
		}

		// Erase text
		if (mapParts.textDrawer == null)
		{
			// The text tool has not been opened. Draw the text once so we can
			// erase it.
			mapParts.textDrawer = new TextDrawer(settings, MapCreator.calcSizeMultiplier(mapParts.graph.getWidth()));
			mapParts.textDrawer.drawText(mapParts.graph, ImageHelper.deepCopy(mapParts.landBackground), mapParts.landBackground,
					mapParts.mountainGroups, mapParts.cityDrawTasks);
		}
		for (MapText text : settings.edits.text)
		{
			text.value = "";
		}

		for (Center center : mapParts.graph.centers)
		{
			// Change land to ocean
			settings.edits.centerEdits.get(center.index).isWater = true;
			settings.edits.centerEdits.get(center.index).isLake = false;

			// Erase icons
			settings.edits.centerEdits.get(center.index).trees = null;
			settings.edits.centerEdits.get(center.index).icon = null;

			// Erase rivers
			for (Edge edge : center.borders)
			{
				EdgeEdit eEdit = settings.edits.edgeEdits.get(edge.index);
				eEdit.riverLevel = 0;
			}
		}

		undoer.setUndoPoint(UpdateType.Full, null);
		createAndShowMapFull();
	}

	private void adjustSettingsForEditor()
	{
		settings.resolution = imageQualityScale;
		settings.frayedBorder = false;
		settings.drawText = false;
		settings.grungeWidth = 0;
		settings.drawBorder = false;
		settings.alwaysUpdateLandBackgroundWithOcean = true;
	}
}
