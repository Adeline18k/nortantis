package nortantis.swing;

import java.util.ArrayDeque;
import java.util.Stack;

import nortantis.MapSettings;
import nortantis.editor.MapChange;

public class Undoer
{
	private ArrayDeque<MapChange> undoStack;
	private Stack<MapChange> redoStack;
	private MapSettings copyOfSettingsWhenEditorWasOpened;
	private MainWindow mainWindow;
	private final float maxUndoLevels = 200;
	boolean enabled;

	public Undoer(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;
	}
	
	public boolean isInitialized()
	{
		return copyOfSettingsWhenEditorWasOpened != null;
	}
	
	public void initialize(MapSettings settings)
	{
		undoStack = new ArrayDeque<>();
		redoStack = new Stack<>();
		copyOfSettingsWhenEditorWasOpened = settings.deepCopy();
	}
	
	/***
	 * Sets a point to which the user can undo changes. 
	 * @param updateType The type of update that was last made. 
	 * @param tool The tool that is setting the undo point.
	 */
	public void setUndoPoint(UpdateType updateType, EditorTool tool)
	{
		if (!enabled)
		{
			return;
		}
		
		MapSettings prevSettings = undoStack.isEmpty() ? copyOfSettingsWhenEditorWasOpened : undoStack.peek().settings;
		MapSettings currentSettings = mainWindow.getSettingsFromGUI(true);
		if (currentSettings.equals(prevSettings))
		{
			// Don't create an undo point if nothing changed.
			return;
		}
		
		redoStack.clear();
		undoStack.push(new MapChange(currentSettings, updateType, tool));
		
		// Limit the size of undoStack to prevent memory errors. Each undo point is about 2 MBs.
		while(undoStack.size() > maxUndoLevels)
		{
			undoStack.removeLast();
		}
		
		updateUndoRedoEnabled();
	}
	
	public void undo()
	{
		if (!enabled)
		{
			return;
		}
		
		MapChange change = undoStack.pop();
		redoStack.push(change);
		MapSettings settings;
		if (undoStack.isEmpty())
		{
			settings = copyOfSettingsWhenEditorWasOpened.deepCopy();
			mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(settings, true);
		}
		else
		{
			settings = undoStack.peek().settings.deepCopy();
			mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(settings, true);
		}
		
		// Keep the collection of text edits being drawn in sync with the settings
		mainWindow.updater.mapParts.textDrawer.setMapTexts(settings.edits.text);
		
		if (change.toolThatMadeChange != null)
		{
			if (mainWindow.toolsPanel.currentTool != change.toolThatMadeChange)
			{
				mainWindow.toolsPanel.handleToolSelected(change.toolThatMadeChange, true);
			}

			change.toolThatMadeChange.onAfterUndoRedo(change);
		}
		else
		{
			// This happens if you undo a change not associated with any particular tool, such as Clear Entire Map.
			mainWindow.toolsPanel.currentTool.onAfterUndoRedo(change);			
		}
		
		mainWindow.updater.createAndShowMapFromChange(change);
	}
	
	public void redo()
	{
		if (!enabled)
		{
			return;
		}

		MapSettings prevSettings = mainWindow.getSettingsFromGUI(true);
		MapChange change = redoStack.pop();
		undoStack.push(change);
		MapSettings newSettings = undoStack.peek().settings.deepCopy();
		mainWindow.loadSettingsAndEditsIntoThemeAndToolsPanels(newSettings, true);
		
		// Keep the collection of text edits being drawn in sync with the settings
		mainWindow.updater.mapParts.textDrawer.setMapTexts(newSettings.edits.text);

		MapChange changeWithPrevSettings = new MapChange(prevSettings, change.updateType, change.toolThatMadeChange);
		if (change.toolThatMadeChange != null)
		{
			// Switch to the tool that made the change.
			if (mainWindow.toolsPanel.currentTool != change.toolThatMadeChange)
			{
				mainWindow.toolsPanel.handleToolSelected(change.toolThatMadeChange, true);
			}
			
			change.toolThatMadeChange.onAfterUndoRedo(changeWithPrevSettings);
		}
		else
		{
			// This happens if you redo a change not associated with any particular tool, such as Clear Entire Map.
			mainWindow.toolsPanel.currentTool.onAfterUndoRedo(changeWithPrevSettings);
		}
		
		mainWindow.updater.createAndShowMapFromChange(change);
	}
	
	public void updateUndoRedoEnabled()
	{		
		boolean undoEnabled = enabled && undoStack.size() > 0;
		mainWindow.undoButton.setEnabled(undoEnabled);
		boolean redoEnabled = enabled && redoStack.size() > 0;
		mainWindow.redoButton.setEnabled(redoEnabled);
	}

	public boolean isEnabled()
	{
		return enabled;
	}
	
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}
}
