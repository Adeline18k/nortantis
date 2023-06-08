package nortantis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import hoten.geom.Rectangle;
import hoten.voronoi.Center;
import hoten.voronoi.Edge;
import nortantis.MapSettings.OceanEffect;
import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIconType;
import nortantis.editor.EdgeEdit;
import nortantis.editor.MapEdits;
import nortantis.editor.RegionEdit;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Pair;
import nortantis.util.Range;

public class MapCreator
{
	private final double regionBlurColorScale = 0.7;
	/**
	 * Controls how dark coastlines can get, for both the land and water. Higher
	 * values are lighter.
	 */
	private static final float coastlineShadingScale = 5.27f;

	private Random r;
	// This is a base width for determining how large to draw text and effects.
	private static final double baseResolution = 1536;

	private static final int concentricWaveWidthBetweenWaves = 12;
	private static final int concentricWaveLineWidth = 2;

	public MapCreator()
	{
	}

	/**
	 * Updates a piece of a map, given a list of edges that changed. Also
	 * updates mapParts.landBackground for later text drawing. This doesn't
	 * include drawing text (it's not needed because the editor draws text as a
	 * separate step).
	 * 
	 * @param settings
	 *            Map settings for drawing
	 * @param mapParts
	 *            Assumed to be populated by createMap the last time the map was
	 *            generated at full size
	 * @param map
	 *            The full sized map to update
	 * @param edgesChanged
	 *            Edits that changed
	 */
	public void incrementalUpdateEdges(final MapSettings settings, MapParts mapParts, BufferedImage fullSizeMap, Set<Edge> edgesChanged)
	{
		// I could be a little more efficient by only re-drawing the edges that
		// changed, but re-drawing the centers too is good enough.
		Set<Center> centersChagned = getCentersFromEdges(mapParts.graph, edgesChanged);
		incrementalUpdate(settings, mapParts, fullSizeMap, centersChagned, edgesChanged);
	}

	/**
	 * Updates a piece of a map, given a list of centers that changed. Also
	 * updates mapParts.landBackground for later text drawing. This doesn't
	 * include drawing text (it's not needed because the editor draws text as a
	 * separate step).
	 * 
	 * @param settings
	 *            Map settings for drawing
	 * @param mapParts
	 *            Assumed to be populated by createMap the last time the map was
	 *            generated at full size
	 * @param map
	 *            The full sized map to update
	 * @param centerChanges
	 *            Edits for centers that need to be re-drawn
	 */
	public void incrementalUpdateCenters(final MapSettings settings, MapParts mapParts, BufferedImage fullSizeMap,
			Set<Center> centersChanged)
	{
		incrementalUpdate(settings, mapParts, fullSizeMap, centersChanged, null);
	}

	/**
	 * Updates a piece of a map, given a list of centers that changed. Also
	 * updates mapParts.landBackground for later text drawing. This doesn't
	 * include drawing text (it's not needed because the editor draws text as a
	 * separate step).
	 * 
	 * @param settings
	 *            Map settings for drawing
	 * @param mapParts
	 *            Assumed to be populated by createMap the last time the map was
	 *            generated at full size
	 * @param map
	 *            The full sized map to update
	 * @param centerChanges
	 *            Edits for centers that need to be re-drawn
	 * @param edgeChanges
	 *            If edges changed, this is the list of edge edits that changed
	 */
	private void incrementalUpdate(final MapSettings settings, MapParts mapParts, BufferedImage fullSizedMap, Set<Center> centersChanged,
			Set<Edge> edgesChanged)
	{
		// double startTime = System.currentTimeMillis();

		centersChanged = new HashSet<>(centersChanged);
		if (edgesChanged != null)
		{
			centersChanged.addAll(getCentersFromEdges(mapParts.graph, edgesChanged));
		}
		Rectangle centersChangedBounds = WorldGraph.getBoundingBox(centersChanged);

		if (centersChangedBounds == null)
		{
			// Nothing changed
			return;
		}

		double sizeMultiplier = calcSizeMultiplier(mapParts.background.mapBounds.getWidth());

		// To handle edge/effects changes outside centersChangedBounds box
		// caused by centers in centersChanged, pad the bounds of the
		// snippet to replace to include the width of ocean effects, land
		// effects, and with widest possible line that can be drawn,
		// whichever is largest.
		double effectsPadding = Math.ceil(Math.max(settings.oceanEffect == OceanEffect.ConcentricWaves
				? settings.concentricWaveCount * (concentricWaveLineWidth + concentricWaveWidthBetweenWaves)
				: settings.oceanEffectsLevel, settings.coastShadingLevel));
		// Increase effectsPadding by the width of a coastline, plus one pixel
		// extra just to be safe.
		effectsPadding += 2;

		// Make sure effectsPadding is at least half the width of the maximum
		// with any line can be drawn, which would probably be a very wide
		// river.
		// Since there is no easy way to know what that will be, just guess.
		effectsPadding = Math.max(effectsPadding, 8);

		effectsPadding *= sizeMultiplier;
		// The bounds to replace in the original map.
		Rectangle replaceBounds = centersChangedBounds.pad(effectsPadding, effectsPadding);
		// Expand snippetToReplaceBounds to include all icons the centers in
		// centersChanged drew the last time they were drawn.
		{
			Rectangle iconBounds = mapParts.iconDrawer.getBoundinbBoxOfIconsForCenters(centersChanged);
			if (iconBounds != null)
			{
				replaceBounds = replaceBounds.add(iconBounds);
			}
		}

		applyRegionEdits(mapParts.graph, settings.edits);
		applyCenterEdits(mapParts.graph, settings.edits, getCenterEditsForCenters(settings.edits, centersChanged));
		{
			Set<EdgeEdit> edgeEdits;
			if (edgesChanged != null)
			{
				edgeEdits = getEdgeEditsForEdges(settings.edits, edgesChanged);
			}
			else
			{
				edgeEdits = new HashSet<EdgeEdit>();
			}
			if (centersChanged != null)
			{
				edgeEdits.addAll(getEdgeEditsForCenters(settings.edits, centersChanged));				
			}
			applyEdgeEdits(mapParts.graph, settings.edits, edgeEdits);
		}

		mapParts.graph.updateCenterLookupTable(centersChanged);

		mapParts.iconDrawer.addOrUpdateIconsFromEdits(settings.edits, sizeMultiplier, centersChanged);

		// Now that we've updated icons to draw in centersChanged, check if we
		// need to expand replaceBounds to include those icons.
		{
			Rectangle updatedIconBounds = mapParts.iconDrawer.getBoundinbBoxOfIconsForCenters(centersChanged);
			if (updatedIconBounds != null)
			{
				replaceBounds = replaceBounds.add(updatedIconBounds);
			}
		}

		replaceBounds = replaceBounds.floor();

		// The bounds of the snippet to draw. This is larger than the snippet to
		// replace because ocean/land effects expand beyond the edges
		// that draw them, and we need those to be included in the snippet to
		// replace.
		Rectangle drawBounds = replaceBounds.pad(effectsPadding, effectsPadding).floor();

		Set<Center> centersToDraw = mapParts.graph.breadthFirstSearch(c -> c.isInBounds(drawBounds), centersChanged.iterator().next());

		mapParts.background.doSetupThatNeedsGraph(settings, mapParts.graph, centersToDraw, drawBounds, replaceBounds);

		// Draw mask for land vs ocean.
		BufferedImage landMask = new BufferedImage((int) drawBounds.width, (int) drawBounds.height, BufferedImage.TYPE_BYTE_BINARY);
		{
			Graphics2D g = landMask.createGraphics();
			mapParts.graph.drawLandAndOceanBlackAndWhite(g, centersToDraw, drawBounds);
		}

		BufferedImage mapSnippet = ImageHelper.maskWithColor(ImageHelper.copySnippet(mapParts.background.land, drawBounds.toAwTRectangle()),
				Color.black, landMask, false);

		mapSnippet = darkenLandNearCoastlinesAndRegionBorders(settings, mapParts.graph, sizeMultiplier, mapSnippet, landMask,
				mapParts.background, centersToDraw, drawBounds, false);

		// Store the current version of mapSnippet for a background when drawing
		// icons later.
		BufferedImage landBackground = ImageHelper.deepCopy(mapSnippet);

		if (settings.drawRegionColors)
		{
			Graphics2D g = mapSnippet.createGraphics();
			g.setColor(settings.coastlineColor);
			mapParts.graph.drawRegionBorders(g, sizeMultiplier, true, centersToDraw, drawBounds);
		}

		Set<Edge> edgesToDraw = getEdgesFromCenters(mapParts.graph, centersToDraw);
		if (settings.drawRivers)
		{
			drawRivers(settings, mapParts.graph, mapSnippet, sizeMultiplier, edgesToDraw, drawBounds);
		}

		if (settings.drawIcons)
		{
			mapParts.iconDrawer.drawAllIcons(mapSnippet, landBackground, drawBounds);
		}
		
		// Add the rivers to landBackground so that the text doesn't erase them.
		// I do this whether or not I draw text because I might draw the text later.
		// This is done after icon drawing so that rivers draw behind icons.
		if (settings.drawRivers)
		{
			drawRivers(settings, mapParts.graph, landBackground, sizeMultiplier, edgesToDraw, drawBounds);
		}

		// Draw ocean
		{
			BufferedImage oceanSnippet = mapParts.background.createOceanSnippet(drawBounds);
			if (settings.drawText || settings.alwaysUpdateLandBackgroundWithOcean)
			{
				// Needed for drawing text
				landBackground = ImageHelper.maskWithImage(landBackground, oceanSnippet, landMask);
			}

			mapSnippet = ImageHelper.maskWithImage(mapSnippet, oceanSnippet, landMask);
		}

		// Add effects to ocean along coastlines
		{
			BufferedImage oceanBlur = createOceanEffects(settings, mapParts.graph, sizeMultiplier, landMask, centersToDraw, drawBounds);
			if (oceanBlur != null)
			{
				mapSnippet = ImageHelper.maskWithColor(mapSnippet, settings.oceanEffectsColor, oceanBlur, true);
				landBackground = ImageHelper.maskWithColor(landBackground, settings.oceanEffectsColor, oceanBlur, true);
			}
		}

		// Draw coastlines.
		{
			Graphics2D g = mapSnippet.createGraphics();
			g.setColor(settings.coastlineColor);
			mapParts.graph.drawCoastlineWithLakeShores(g, sizeMultiplier, centersToDraw, drawBounds);
		}
		{
			Graphics2D g = landBackground.createGraphics();
			g.setColor(settings.coastlineColor);
			mapParts.graph.drawCoastlineWithLakeShores(g, sizeMultiplier, centersToDraw, drawBounds);
		}

		java.awt.Rectangle boundsInSourceToCopyFrom = new java.awt.Rectangle((int) replaceBounds.x - (int) drawBounds.x,
				(int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width, (int) replaceBounds.height);
		ImageHelper.copySnippetFromSourceAndPasteIntoTarget(fullSizedMap, mapSnippet, replaceBounds.upperLeftCornerAsAwtPoint(),
				boundsInSourceToCopyFrom);

		// Debug code
		// Graphics g = fullSizedMap.createGraphics();
		// g.setColor(Color.white);
		// g.drawRect((int) replaceBounds.x, (int) replaceBounds.y, (int)
		// replaceBounds.width, (int) replaceBounds.height);
		// g.setColor(Color.red);
		// g.drawRect((int) centersChangedBounds.x, (int)
		// centersChangedBounds.y, (int) centersChangedBounds.width, (int)
		// centersChangedBounds.height);
		// g.dispose();

		// Also update the snippet in landBackground because the text tool needs
		// that.
		ImageHelper.copySnippetFromSourceAndPasteIntoTarget(mapParts.landBackground, landBackground,
				replaceBounds.upperLeftCornerAsAwtPoint(), boundsInSourceToCopyFrom);

		// Print run time
		// double elapsedTime = System.currentTimeMillis() - startTime;
		// Logger.println("Time to do incremental update: " + elapsedTime /
		// 1000.0);
	}

	/**
	 * Draws a map.
	 * 
	 * @param settings
	 * @param maxDimensions
	 *            The maximum width and height (in pixels) at which to draw the
	 *            map. This is needed for creating previews. null means draw at
	 *            normal resolution. Warning: If maxDimensions is specified,
	 *            then settings.resolution will be modified to fit that size.
	 * @param mapParts
	 *            If not null, then parts of the map created while generating
	 *            will be stored in it.
	 * @return The map
	 */
	public BufferedImage createMap(final MapSettings settings, Dimension maxDimensions, MapParts mapParts) throws IOException
	{
		Logger.println("Creating the map");
		if (!Files.exists(Paths.get(settings.landBackgroundImage)))
			throw new IllegalArgumentException("Land background image file does not exists: " + settings.landBackgroundImage);
		if (!Files.exists(Paths.get(settings.oceanBackgroundImage)))
			throw new IllegalArgumentException("Ocean background image file does not exists: " + settings.oceanBackgroundImage);

		double startTime = System.currentTimeMillis();

		r = new Random(settings.randomSeed);

		Background background;
		if (mapParts != null && mapParts.background != null)
		{
			background = mapParts.background;
		}
		else
		{
			Logger.println("Generating the background image.");
			background = new Background(settings, maxDimensions);
		}

		if (mapParts != null)
		{
			mapParts.background = background;
		}

		double sizeMultiplier = calcSizeMultiplier(background.mapBounds.getWidth());
		if (mapParts != null)
		{
			mapParts.sizeMultiplier = sizeMultiplier;
		}

		TextDrawer textDrawer = null;
		if (mapParts == null || mapParts.textDrawer == null)
		{
			// Create the TextDrawer regardless off settings.drawText because
			// the editor might be generating the map without text
			// now, but want to show the text later, so in that case we would
			// want to generate the texts using the TextDrawer but
			// not show them.
			textDrawer = new TextDrawer(settings, sizeMultiplier);

			if (mapParts != null)
			{
				mapParts.textDrawer = textDrawer;
			}
		}
		else
		{
			textDrawer = mapParts.textDrawer;
		}

		WorldGraph graph;
		if (mapParts == null || mapParts.graph == null)
		{
			Logger.println("Creating the graph.");
			graph = createGraph(settings, background.mapBounds.getWidth(), background.mapBounds.getHeight(), r, sizeMultiplier);
			if (mapParts != null)
			{
				mapParts.graph = graph;
			}
		}
		else
		{
			graph = mapParts.graph;
		}

		applyRegionEdits(graph, settings.edits);
		applyCenterEdits(graph, settings.edits, null);
		applyEdgeEdits(graph, settings.edits, null);

		background.doSetupThatNeedsGraph(settings, graph, null, null, null);
		if (mapParts == null)
		{
			background.landBeforeRegionColoring = null;
		}

		IconDrawer iconDrawer;
		boolean needToAddIcons;
		if (mapParts == null || mapParts.iconDrawer == null)
		{
			iconDrawer = new IconDrawer(graph, new Random(r.nextLong()), settings.cityIconSetName);
			if (mapParts != null)
			{
				mapParts.iconDrawer = iconDrawer;
			}

			needToAddIcons = !settings.edits.hasIconEdits;
		}
		else
		{
			iconDrawer = mapParts.iconDrawer;
			needToAddIcons = false; // The icon drawer is from cache, so it
									// already knows what icons to draw.
			r.nextLong(); // Use the random number generator the same as if I
							// had created the icon drawer.
		}

		List<Set<Center>> mountainGroups = null;
		List<Set<Center>> mountainAndHillGroups = null;
		if (needToAddIcons)
		{
			iconDrawer.markMountains();
			iconDrawer.markHills();
			iconDrawer.markCities(settings.cityProbability);
			Pair<List<Set<Center>>> pair = iconDrawer.findMountainAndHillGroups();
			// All mountain ranges and smaller groups of mountains (include
			// mountains that are alone).
			mountainGroups = pair.getFirst();
			// All mountain ranges and smaller groups of mountains extended to
			// include nearby hills.
			mountainAndHillGroups = pair.getSecond();
			pair = null;
			if (mapParts != null)
			{
				mapParts.mountainGroups = mountainGroups;
			}
		}
		else
		{
			iconDrawer.addOrUpdateIconsFromEdits(settings.edits, sizeMultiplier, graph.centers);
		}

		// Draw mask for land vs ocean.
		Logger.println("Adding land.");
		BufferedImage landMask = new BufferedImage(graph.getWidth(), graph.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			Graphics2D g = landMask.createGraphics();
			graph.drawLandAndOceanBlackAndWhite(g, graph.centers, null);
		}

		// Combine land and ocean images.
		BufferedImage map = ImageHelper.maskWithColor(background.land, Color.black, landMask, false);

		if (mapParts == null)
		{
			background.land = null;
		}

		map = darkenLandNearCoastlinesAndRegionBorders(settings, graph, sizeMultiplier, map, landMask, background, null, null, true);

		// Store the current version of the map for a background when drawing
		// icons later.
		BufferedImage landBackground = ImageHelper.deepCopy(map);

		if (settings.drawRegionColors)
		{
			Graphics2D g = map.createGraphics();
			g.setColor(settings.coastlineColor);
			graph.drawRegionBorders(g, sizeMultiplier, true, null, null);
		}

		if (settings.drawRivers)
		{
			// Add rivers.
			Logger.println("Adding rivers.");
			drawRivers(settings, graph, map, sizeMultiplier, null, null);
		}

		List<IconDrawTask> cities = null;
		if (needToAddIcons)
		{
			Logger.println("Adding mountains and hills.");
			iconDrawer.addMountainsAndHills(mountainGroups, mountainAndHillGroups);
			if (mapParts != null)
				mapParts.mountainGroups = mountainGroups;

			Logger.println("Adding sand dunes.");
			iconDrawer.addSandDunes();

			Logger.println("Adding trees.");
			iconDrawer.addTrees();

			Logger.println("Adding cities.");
			cities = iconDrawer.addOrUnmarkCities(sizeMultiplier, true);

			if (mapParts != null)
			{
				mapParts.cityDrawTasks = cities;
			}
		}

		if (settings.drawRoads)
		{
			// TODO put back
			// RoadDrawer roadDrawer = new RoadDrawer(r, settings, graph,
			// iconDrawer);
			// roadDrawer.markRoads();
			// roadDrawer.drawRoads(map, sizeMultiplier);
		}

		if (settings.drawIcons)
		{
			Logger.println("Drawing all icons.");
			iconDrawer.drawAllIcons(map, landBackground, null);
		}

		// Add the rivers to landBackground so that the text doesn't erase them.
		// I do this whether or not I draw text because I might draw the text later.
		// This is done after icon drawing so that rivers draw behind icons.
		if (settings.drawRivers)
		{
			drawRivers(settings, graph, landBackground, sizeMultiplier, null, null);
		}

		Logger.println("Drawing ocean.");
		{
			if (background.ocean.getWidth() != graph.getWidth() || background.ocean.getHeight() != graph.getHeight())
			{
				throw new IllegalArgumentException(
						"The given ocean background image does not" + " have the same aspect ratio as the given land background image.");
			}

			if (settings.drawText || settings.alwaysUpdateLandBackgroundWithOcean)
			{
				// Needed for drawing text
				landBackground = ImageHelper.maskWithImage(landBackground, background.ocean, landMask);
			}

			map = ImageHelper.maskWithImage(map, background.ocean, landMask);

			if (mapParts == null)
			{
				background.ocean = null;
			}
		}

		{
			BufferedImage oceanBlur = createOceanEffects(settings, graph, sizeMultiplier, landMask, null, null);
			if (oceanBlur != null)
			{
				Logger.println("Adding effects to ocean along coastlines.");
				map = ImageHelper.maskWithColor(map, settings.oceanEffectsColor, oceanBlur, true);
				landBackground = ImageHelper.maskWithColor(landBackground, settings.oceanEffectsColor, oceanBlur, true);
			}
		}

		// Draw coastlines.
		{
			Graphics2D g = map.createGraphics();
			g.setColor(settings.coastlineColor);
			graph.drawCoastlineWithLakeShores(g, sizeMultiplier, null, null);
		}
		{
			Graphics2D g = landBackground.createGraphics();
			g.setColor(settings.coastlineColor);
			graph.drawCoastlineWithLakeShores(g, sizeMultiplier, null, null);
		}

		if (mapParts != null)
			mapParts.landBackground = landBackground;

		if (settings.drawText)
		{
			Logger.println("Adding text.");

			if (settings.drawRegionColors)
			{
				Graphics2D g = landBackground.createGraphics();
				g.setColor(settings.coastlineColor);
				graph.drawRegionBorders(g, sizeMultiplier, true, null, null);
			}
		}
		// Call drawText below regardless of settings.drawText to create the
		// MapText objects even when text is not shown.
		textDrawer.drawText(graph, map, landBackground, mountainGroups, cities);
		landBackground = null;

		if (settings.drawBorder)
		{
			Logger.println("Adding border.");
			map = addBorderToMap(settings, map, background);
			if (mapParts == null)
			{
				background.borderBackground = null;
			}
		}

		if (settings.frayedBorder)
		{
			Logger.println("Adding frayed edges.");
			WorldGraph frayGraph = GraphCreator.createSimpleGraph(background.borderBounds.getWidth(), background.borderBounds.getHeight(),
					settings.frayedBorderSize, new Random(r.nextLong()), sizeMultiplier, settings.pointPrecision, true);
			BufferedImage frayedBorderMask = new BufferedImage(frayGraph.getWidth(), frayGraph.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			frayGraph.drawBorderWhite(frayedBorderMask.createGraphics());

			int blurLevel = (int) (settings.frayedBorderBlurLevel * sizeMultiplier);
			if (blurLevel > 0)
			{
				float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
				BufferedImage frayedBorderBlur = ImageHelper.convolveGrayscale(frayedBorderMask, kernel, true);

				map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, frayedBorderBlur, true);

			}
			map = ImageHelper.setAlphaFromMask(map, frayedBorderMask, true);
		}
		else
		{
			// Use the random number generator the same whether or not we draw a
			// frayed border.
			r.nextLong();
		}
		background = null;

		if (settings.grungeWidth > 0)
		{
			Logger.println("Adding grunge.");
			// 104567 is an arbitrary number added so that the grunge is not the
			// same pattern as
			// the background.
			BufferedImage clouds = FractalBGGenerator.generate(new Random(settings.backgroundRandomSeed + 104567), settings.fractalPower,
					(int) map.getWidth(), (int) map.getHeight(), 0.75f);
			// Whiten the middle of clouds.
			darkenMiddleOfImage(settings.resolution, clouds, settings.grungeWidth);

			// Add the cloud mask to the map.
			map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, clouds, true);
		}

		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Total time to generate map (in seconds): " + elapsedTime / 1000.0);

		Logger.println("Done creating map.");

		return map;
	}

	/**
	 * If land near coastlines and region borders should be darkened, then t his
	 * creates a copy of mapOrSnippet but with that darkening. Otherwise, it
	 * returns mapOrSnippet.
	 */
	private BufferedImage darkenLandNearCoastlinesAndRegionBorders(MapSettings settings, WorldGraph graph, double sizeMultiplier,
			BufferedImage mapOrSnippet, BufferedImage landMask, Background background, Collection<Center> centersToDraw,
			Rectangle drawBounds, boolean addLoggingEntry)
	{
		BufferedImage coastShading;
		int blurLevel = (int) (settings.coastShadingLevel * sizeMultiplier);

		final float scaleForDarkening = coastlineShadingScale;
		int maxPixelValue = ImageHelper.getMaxPixelValue(BufferedImage.TYPE_BYTE_GRAY);
		double targetStrokeWidth = sizeMultiplier;
		float scale = ((float) settings.coastShadingColor.getAlpha()) / ((float) (maxPixelValue)) * scaleForDarkening
				* calcMultiplyertoCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);

		if (blurLevel > 0)
		{
			if (addLoggingEntry)
			{
				Logger.println("Darkening land near shores.");
			}
			float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);

			BufferedImage coastlineAndLakeShoreMask = new BufferedImage(mapOrSnippet.getWidth(), mapOrSnippet.getHeight(),
					BufferedImage.TYPE_BYTE_BINARY);
			{
				Graphics2D g = coastlineAndLakeShoreMask.createGraphics();
				g.setColor(Color.white);
				graph.drawCoastlineWithLakeShores(g, targetStrokeWidth, centersToDraw, drawBounds);
			}

			if (settings.drawRegionColors)
			{
				Graphics2D g = coastlineAndLakeShoreMask.createGraphics();
				g.setColor(Color.white);
				graph.drawRegionBorders(g, sizeMultiplier, false, centersToDraw, drawBounds);
				coastShading = ImageHelper.convolveGrayscaleThenScale(coastlineAndLakeShoreMask, kernel, scale);
				// Remove the land blur from the ocean side of the borders and
				// color the blur
				// according to each region's blur color.
				coastShading = ImageHelper.maskWithColor(coastShading, Color.black, landMask, false);
				Map<Integer, Color> colors = new HashMap<>();
				if (graph.regions.size() > 0)
				{
					for (Map.Entry<Integer, Region> regionEntry : graph.regions.entrySet())
					{
						Region reg = regionEntry.getValue();
						Color color = new Color((int) (reg.backgroundColor.getRed() * regionBlurColorScale),
								(int) (reg.backgroundColor.getGreen() * regionBlurColorScale),
								(int) (reg.backgroundColor.getBlue() * regionBlurColorScale));
						colors.put(reg.id, color);
					}
				}
				else
				{
					colors.put(0, settings.landColor);
				}
				return ImageHelper.maskWithMultipleColors(mapOrSnippet, colors, background.regionIndexes, coastShading, true);
			}
			else
			{
				coastShading = ImageHelper.convolveGrayscaleThenScale(coastlineAndLakeShoreMask, kernel, scale);

				// Remove the land blur from the ocean side of the borders.
				coastShading = ImageHelper.maskWithColor(coastShading, Color.black, landMask, false);

				return ImageHelper.maskWithColor(mapOrSnippet, settings.coastShadingColor, coastShading, true);
			}
		}
		return mapOrSnippet;
	}

	private static BufferedImage createOceanEffects(MapSettings settings, WorldGraph graph, double sizeMultiplier, BufferedImage landMask,
			Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		if (drawBounds == null)
		{
			drawBounds = graph.bounds;
		}

		BufferedImage oceanEffects = null;
		int oceanEffectsLevelScaled = (int) (settings.oceanEffectsLevel * sizeMultiplier);
		if (((settings.oceanEffect == OceanEffect.Ripples || settings.oceanEffect == OceanEffect.Blur) && oceanEffectsLevelScaled > 0)
				|| (settings.oceanEffect == OceanEffect.ConcentricWaves && settings.concentricWaveCount > 0))
		{
			double targetStrokeWidth = sizeMultiplier;
			BufferedImage coastlineMask = new BufferedImage((int) drawBounds.width, (int) drawBounds.height,
					BufferedImage.TYPE_BYTE_BINARY);
			{
				Graphics2D g = coastlineMask.createGraphics();
				g.setColor(Color.white);

				graph.drawCoastline(g, targetStrokeWidth, centersToDraw, drawBounds);
			}

			if (settings.oceanEffect == OceanEffect.Ripples || settings.oceanEffect == OceanEffect.Blur)
			{
				float[][] kernel;
				if (settings.oceanEffect == OceanEffect.Ripples)
				{
					kernel = ImageHelper.createPositiveSincKernel(oceanEffectsLevelScaled, 1.0 / sizeMultiplier);
				}
				else
				{
					kernel = ImageHelper.createGaussianKernel((int) (settings.oceanEffectsLevel * sizeMultiplier));
				}
				int maxPixelValue = ImageHelper.getMaxPixelValue(BufferedImage.TYPE_BYTE_GRAY);
				final float scaleForDarkening = coastlineShadingScale;
				float scale = ((float) settings.oceanEffectsColor.getAlpha()) / ((float) (maxPixelValue)) * scaleForDarkening
						* calcMultiplyertoCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
				oceanEffects = ImageHelper.convolveGrayscaleThenScale(coastlineMask, kernel, scale);
				// Remove the ocean blur from the land side of the borders.
				oceanEffects = ImageHelper.maskWithColor(oceanEffects, Color.black, landMask, true);
			}
			else
			{
				oceanEffects = new BufferedImage((int) drawBounds.width, (int) drawBounds.height, BufferedImage.TYPE_BYTE_GRAY);

				double widthBetweenWaves = concentricWaveWidthBetweenWaves * sizeMultiplier;
				double lineWidth = concentricWaveLineWidth * sizeMultiplier;
				double largestLineWidth = settings.concentricWaveCount * (widthBetweenWaves + lineWidth);
				for (int i : new Range(0, settings.concentricWaveCount))
				{
					{
						double whiteWidth = largestLineWidth - (i * (widthBetweenWaves + lineWidth));
						if (whiteWidth <= 0)
						{
							continue;
						}
						BufferedImage blur = ImageHelper.convolveGrayscale(coastlineMask,
								ImageHelper.createGaussianKernel((int) whiteWidth), true);
						ImageHelper.threshold(blur, 1, settings.oceanEffectsColor.getAlpha());
						ImageHelper.add(oceanEffects, blur);
					}

					{
						double blackWidth = largestLineWidth - (i * (widthBetweenWaves + lineWidth)) - lineWidth;
						if (blackWidth <= 0)
						{
							continue;
						}
						BufferedImage blur = ImageHelper.convolveGrayscale(coastlineMask,
								ImageHelper.createGaussianKernel((int) blackWidth), true);
						ImageHelper.threshold(blur, 1);
						ImageHelper.subtract(oceanEffects, blur);
					}
				}

				oceanEffects = ImageHelper.maskWithColor(oceanEffects, Color.black, landMask, true);
			}
		}
		return oceanEffects;
	}

	private static float calcMultiplyertoCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(double targetStrokeWidth)
	{
		if (targetStrokeWidth >= 1f)
		{
			return 1f;
		}

		// The stroke will be drawn a 1 pixel wide because that is the smallest
		// it can be drawn, but that will make the coastline shading relatively
		// much darker than it should be. In this case multiplying the convolved
		// shading values by the target stroke width lowers them appropriately.
		return (float) targetStrokeWidth;
	}

	private static void assignRandomRegionColors(WorldGraph graph, MapSettings settings)
	{

		float[] landHsb = new float[3];
		Color.RGBtoHSB(settings.landColor.getRed(), settings.landColor.getGreen(), settings.landColor.getBlue(), landHsb);

		List<Color> regionColorOptions = new ArrayList<>();
		Random rand = new Random(settings.regionsRandomSeed);
		for (@SuppressWarnings("unused")
		int i : new Range(graph.regions.size()))
		{
			regionColorOptions
					.add(generateRegionColor(rand, landHsb, settings.hueRange, settings.saturationRange, settings.brightnessRange));
		}

		assignRegionColors(graph, regionColorOptions);
	}

	/**
	 * Assigns the color of each political region.
	 */
	private static void assignRegionColors(WorldGraph graph, List<Color> colorOptions)
	{
		for (int i : new Range(graph.regions.size()))
		{
			graph.regions.get(i).backgroundColor = colorOptions.get(i % colorOptions.size());
		}
	}

	private static Color generateRegionColor(Random rand, float[] landHsb, float hueRange, float saturationRange, float brightnessRange)
	{
		float hue = (float) (landHsb[0] * 360 + (rand.nextDouble() - 0.5) * hueRange);
		float saturation = ImageHelper.bound((int) (landHsb[1] * 255 + (rand.nextDouble() - 0.5) * saturationRange));
		float brightness = ImageHelper.bound((int) (landHsb[2] * 255 + (rand.nextDouble() - 0.5) * brightnessRange));
		return ImageHelper.colorFromHSB(hue, saturation, brightness);
	}

	public static Color generateColorFromBaseColor(Random rand, Color base, float hueRange, float saturationRange, float brightnessRange)
	{
		float[] hsb = new float[3];
		Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), hsb);
		return generateRegionColor(rand, hsb, hueRange, saturationRange, brightnessRange);
	}

	private static WorldGraph createGraph(MapSettings settings, double width, double height, Random r, double sizeMultiplier)
	{
		WorldGraph graph = GraphCreator.createGraph(width, height, settings.worldSize, settings.edgeLandToWaterProbability,
				settings.centerLandToWaterProbability, new Random(r.nextLong()), sizeMultiplier, settings.lineStyle,
				settings.pointPrecision);

		// Setup region colors even if settings.drawRegionColors = false because
		// edits need them in case someone edits a map without region colors,
		// then later enables region colors.
		assignRandomRegionColors(graph, settings);

		return graph;
	}

	public static double calcSizeMultiplier(double mapWidth)
	{
		return mapWidth / baseResolution;
	}

	private static void applyRegionEdits(WorldGraph graph, MapEdits edits)
	{
		if (edits == null || edits.regionEdits.isEmpty())
		{
			return;
		}

		for (RegionEdit edit : edits.regionEdits.values())
		{
			Region region = graph.regions.get(edit.regionId);
			if (region == null)
			{
				region = new Region();
				region.id = edit.regionId;
				region.backgroundColor = edit.color;
				graph.regions.put(edit.regionId, region);
			}
			else
			{
				region.backgroundColor = edit.color;
			}
		}
	}

	private static void applyCenterEdits(WorldGraph graph, MapEdits edits, List<CenterEdit> centerChanges)
	{
		if (edits == null || edits.centerEdits.isEmpty())
		{
			return;
		}

		if (edits.centerEdits.size() != graph.centers.size())
		{
			throw new IllegalArgumentException(
					"The map edits have " + edits.centerEdits.size() + " polygons, but the world size is " + graph.centers.size());
		}

		if (centerChanges == null)
		{
			centerChanges = edits.centerEdits;
		}

		for (CenterEdit cEdit : centerChanges)
		{
			Center center = graph.centers.get(cEdit.index);
			boolean needsRebuild = center.isWater != cEdit.isWater;
			center.isWater = cEdit.isWater;
			center.isLake = cEdit.isLake;

			Integer regionId = cEdit.regionId;
			if (regionId != null)
			{
				Region region = graph.regions.get(regionId);
				// region can be null if the map is edited while drawing it. If
				// that happens, then the region color of this center will be
				// updated the next time the map draws.
				if (region != null)
				{
					if (center.region != null && center.region.id != region.id)
					{
						needsRebuild = true;
					}
					region.addAndSetRegion(center);
					// We don't know which region the center came from, so
					// remove it from of them except the one it is in.
					for (Region r : graph.regions.values())
					{
						if (r.id != region.id)
						{
							r.remove(center);
						}
					}
				}
			}

			if (center.isWater && center.region != null)
			{
				center.region.remove(center);
				center.region = null;
				needsRebuild = true;
			}

			if (needsRebuild)
			{
				graph.rebuildNoisyEdgesForCenter(center);
			}

			if (cEdit.icon != null && cEdit.icon.iconType == CenterIconType.Mountain)
			{
				// This is so that if you edit mountains before text, the text
				// drawer generates names for your mountains.
				center.isMountain = true;
			}
		}
	}

	private static void applyEdgeEdits(WorldGraph graph, MapEdits edits, Collection<EdgeEdit> edgeChanges)
	{
		if (edits == null || edits.edgeEdits.isEmpty())
		{
			return;
		}
		if (edits.edgeEdits.size() != graph.edges.size())
		{
			throw new IllegalArgumentException(
					"The map edits have " + edits.edgeEdits.size() + " edges, but graph has " + graph.edges.size() + " edges.");
		}

		if (edgeChanges == null)
		{
			edgeChanges = edits.edgeEdits;
		}

		for (EdgeEdit eEdit : edgeChanges)
		{
			Edge edge = graph.edges.get(eEdit.index);
			boolean needsRebuild = false;
			if (eEdit.riverLevel != edge.river && edge.d0 != null)
			{
				needsRebuild = true;
			}
			graph.edges.get(eEdit.index).river = eEdit.riverLevel;
			if (needsRebuild)
			{
				graph.rebuildNoisyEdgesForCenter(edge.d0);
			}
		}
	}

	private BufferedImage addBorderToMap(MapSettings settings, BufferedImage map, Background background)
	{
		int borderWidthScaled = (int) (settings.borderWidth * settings.resolution);

		if (borderWidthScaled == 0)
		{
			return map;
		}

		Graphics2D g = background.borderBackground.createGraphics();
		background.borderBackground.getGraphics().drawImage(map, borderWidthScaled, borderWidthScaled, null);
		map = background.borderBackground;

		Path allBordersPath = Paths.get(AssetsPath.get(), "borders");
		Path borderPath = Paths.get(allBordersPath.toString(), settings.borderType);
		if (!Files.exists(borderPath))
		{
			throw new RuntimeException(
					"The selected border type '" + settings.borderType + "' does not have a folder for images in " + allBordersPath + ".");
		}

		// Corners
		BufferedImage upperLeftCorner = loadImageWithStringInFileName(borderPath, "upper_left_corner.", false);
		if (upperLeftCorner != null)
		{
			upperLeftCorner = ImageHelper.scaleByWidth(upperLeftCorner, borderWidthScaled);
		}
		BufferedImage upperRightCorner = loadImageWithStringInFileName(borderPath, "upper_right_corner.", false);
		if (upperRightCorner != null)
		{
			upperRightCorner = ImageHelper.scaleByWidth(upperRightCorner, borderWidthScaled);
		}
		BufferedImage lowerLeftCorner = loadImageWithStringInFileName(borderPath, "lower_left_corner.", false);
		if (lowerLeftCorner != null)
		{
			lowerLeftCorner = ImageHelper.scaleByWidth(lowerLeftCorner, borderWidthScaled);
		}
		BufferedImage lowerRightCorner = loadImageWithStringInFileName(borderPath, "lower_right_corner.", false);
		if (lowerRightCorner != null)
		{
			lowerRightCorner = ImageHelper.scaleByWidth(lowerRightCorner, borderWidthScaled);
		}

		if (upperLeftCorner == null)
		{
			if (upperRightCorner != null)
			{
				upperLeftCorner = createCornerFromCornerByFlipping(upperRightCorner, CornerType.upperRight, CornerType.upperLeft);
			}
			else if (lowerLeftCorner != null)
			{
				upperLeftCorner = createCornerFromCornerByFlipping(lowerLeftCorner, CornerType.lowerLeft, CornerType.upperLeft);
			}
			else if (lowerRightCorner != null)
			{
				upperLeftCorner = createCornerFromCornerByFlipping(lowerRightCorner, CornerType.lowerRight, CornerType.upperLeft);
			}
			else
			{
				throw new RuntimeException("Couldn't find any corner images in " + borderPath);
			}
		}
		if (upperRightCorner == null)
		{
			upperRightCorner = createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.upperRight);
		}
		if (lowerLeftCorner == null)
		{
			lowerLeftCorner = createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.lowerLeft);
		}
		if (lowerRightCorner == null)
		{
			lowerRightCorner = createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.lowerRight);
		}

		g.drawImage(upperLeftCorner, 0, 0, null);
		g.drawImage(upperRightCorner, (int) background.borderBounds.getWidth() - borderWidthScaled, 0, null);
		g.drawImage(lowerLeftCorner, 0, (int) background.borderBounds.getHeight() - borderWidthScaled, null);
		g.drawImage(lowerRightCorner, (int) background.borderBounds.getWidth() - borderWidthScaled,
				(int) background.borderBounds.getHeight() - borderWidthScaled, null);

		// Edges
		BufferedImage topEdge = loadImageWithStringInFileName(borderPath, "top_edge.", false);
		if (topEdge != null)
		{
			topEdge = ImageHelper.scaleByHeight(topEdge, borderWidthScaled);
		}
		BufferedImage bottomEdge = loadImageWithStringInFileName(borderPath, "bottom_edge.", false);
		if (bottomEdge != null)
		{
			bottomEdge = ImageHelper.scaleByHeight(bottomEdge, borderWidthScaled);
		}
		BufferedImage leftEdge = loadImageWithStringInFileName(borderPath, "left_edge.", false);
		if (leftEdge != null)
		{
			leftEdge = ImageHelper.scaleByWidth(leftEdge, borderWidthScaled);
		}
		BufferedImage rightEdge = loadImageWithStringInFileName(borderPath, "right_edge.", false);
		if (rightEdge != null)
		{
			rightEdge = ImageHelper.scaleByHeight(rightEdge, borderWidthScaled);
		}

		if (topEdge == null)
		{
			if (rightEdge != null)
			{
				topEdge = createEdgeFromEdge(rightEdge, EdgeType.Right, EdgeType.Top);
			}
			else if (leftEdge != null)
			{
				topEdge = createEdgeFromEdge(leftEdge, EdgeType.Left, EdgeType.Top);
			}
			else if (bottomEdge != null)
			{
				topEdge = createEdgeFromEdge(bottomEdge, EdgeType.Bottom, EdgeType.Top);
			}
			else
			{
				throw new RuntimeException("Couldn't find any edge images in " + borderPath);
			}
		}
		if (rightEdge == null)
		{
			rightEdge = createEdgeFromEdge(topEdge, EdgeType.Top, EdgeType.Right);
		}
		if (leftEdge == null)
		{
			leftEdge = createEdgeFromEdge(topEdge, EdgeType.Top, EdgeType.Left);
		}
		if (bottomEdge == null)
		{
			bottomEdge = createEdgeFromEdge(topEdge, EdgeType.Top, EdgeType.Bottom);
		}

		// Draw the edges

		// Top and bottom edges
		for (int i : new Range(2))
		{
			BufferedImage edge = i == 0 ? topEdge : bottomEdge;
			final int y = i == 0 ? 0 : map.getHeight() - borderWidthScaled;

			int end = map.getWidth() - borderWidthScaled;
			int increment = edge.getWidth();
			for (int x = borderWidthScaled; x < end; x += increment)
			{
				int distanceRemaining = end - x;
				if (distanceRemaining >= increment)
				{
					g.drawImage(edge, x, y, null);
				}
				else
				{
					// The image is too long/tall to draw in the remaining
					// space.
					BufferedImage partToDraw = ImageHelper.extractRegion(edge, 0, 0, distanceRemaining, borderWidthScaled);
					g.drawImage(partToDraw, x, y, null);
				}
			}
		}

		// Left and right edges
		for (int i : new Range(2))
		{
			BufferedImage edge = i == 0 ? leftEdge : rightEdge;
			final int x = i == 0 ? 0 : map.getWidth() - borderWidthScaled;

			int end = map.getHeight() - borderWidthScaled;
			int increment = edge.getHeight();
			for (int y = borderWidthScaled; y < end; y += increment)
			{
				int distanceRemaining = end - y;
				if (distanceRemaining >= increment)
				{
					g.drawImage(edge, x, y, null);
				}
				else
				{
					// The image is too long/tall to draw in the remaining
					// space.
					BufferedImage partToDraw = ImageHelper.extractRegion(edge, 0, 0, borderWidthScaled, distanceRemaining);
					g.drawImage(partToDraw, x, y, null);
				}
			}
		}

		g.dispose();

		return map;
	}

	private BufferedImage createEdgeFromEdge(BufferedImage edgeIn, EdgeType edgeTypeIn, EdgeType outputType)
	{
		switch (edgeTypeIn)
		{
		case Bottom:
			switch (outputType)
			{
			case Bottom:
				return edgeIn;
			case Left:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			case Right:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			case Top:
				return ImageHelper.flipOnYAxis(edgeIn);
			}
		case Left:
			switch (outputType)
			{
			case Bottom:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			case Left:
				return edgeIn;
			case Right:
				return ImageHelper.flipOnXAxis(edgeIn);
			case Top:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			}
		case Right:
			switch (outputType)
			{
			case Bottom:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			case Left:
				return ImageHelper.flipOnXAxis(edgeIn);
			case Right:
				return edgeIn;
			case Top:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			}
		case Top:
			switch (outputType)
			{
			case Bottom:
				return ImageHelper.flipOnYAxis(edgeIn);
			case Left:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			case Right:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			case Top:
				return edgeIn;
			}
		}

		throw new IllegalStateException("Unable to create a border edge from the edges given");
	}

	private enum EdgeType
	{
		Top, Bottom, Left, Right
	}

	private BufferedImage createCornerFromCornerByFlipping(BufferedImage cornerIn, CornerType inputCornerType, CornerType outputType)
	{
		switch (inputCornerType)
		{
		case lowerLeft:
			switch (outputType)
			{
			case lowerLeft:
				return cornerIn;
			case lowerRight:
				return ImageHelper.flipOnXAxis(cornerIn);
			case upperLeft:
				return ImageHelper.flipOnYAxis(cornerIn);
			case upperRight:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			}
			break;
		case lowerRight:
			switch (outputType)
			{
			case lowerLeft:
				return ImageHelper.flipOnXAxis(cornerIn);
			case lowerRight:
				return cornerIn;
			case upperLeft:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			case upperRight:
				return ImageHelper.flipOnYAxis(cornerIn);
			}
		case upperLeft:
			switch (outputType)
			{
			case lowerLeft:
				return ImageHelper.flipOnYAxis(cornerIn);
			case lowerRight:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			case upperLeft:
				return cornerIn;
			case upperRight:
				return ImageHelper.flipOnXAxis(cornerIn);
			}
		case upperRight:
			switch (outputType)
			{
			case lowerLeft:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			case lowerRight:
				return ImageHelper.flipOnYAxis(cornerIn);
			case upperLeft:
				return ImageHelper.flipOnXAxis(cornerIn);
			case upperRight:
				return cornerIn;
			}
		}

		throw new IllegalStateException("Unable to flip corner image.");
	}

	private enum CornerType
	{
		upperLeft, upperRight, lowerLeft, lowerRight
	}

	private BufferedImage loadImageWithStringInFileName(Path path, String inFileName, boolean throwExceptionIfMissing)
	{
		File[] cornerArray = new File(path.toString()).listFiles(file -> file.getName().contains(inFileName));
		if (cornerArray.length == 0)
		{
			if (throwExceptionIfMissing)
				throw new RuntimeException(
						"Unable to find a file containing \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
			else
				return null;
		}
		if (cornerArray.length > 1)
		{
			throw new RuntimeException("More than one file contains \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
		}

		return ImageHelper.read(cornerArray[0].getPath());
	}

	/**
	 * Makes the middle area of a gray scale image darker following a Gaussian
	 * blur drop off.
	 */
	private void darkenMiddleOfImage(double resolutionScale, BufferedImage image, int grungeWidth)
	{
		// Draw a white box.

		int blurLevel = (int) (grungeWidth * resolutionScale);
		if (blurLevel == 0)
			blurLevel = 1; // Avoid an exception later.
		// Create a white no-filled in rectangle, then blur it. To be much more
		// efficient, I only create
		// the upper left corner plus 1 pixel in both directions since the
		// corners and edges are all the
		// same except rotated and the edges are all the same except their
		// length.
		int blurBoxWidth = blurLevel * 2 + 1;
		// There is a blurLevel wide buffer below is so that in the convolution
		// the border from one side of the box won't spread (wrap) to the other
		// side.
		// I would be especially bad if it did because
		// ImageHelper.convolveGrayscale pads images to be powers of 2 in the
		// width and height.
		// The white rectangles also draw an extra blurLevel from blurBoxWidth,
		// totaling blurLevel*2.
		BufferedImage blurBox = new BufferedImage(blurBoxWidth + blurLevel * 2, blurBoxWidth + blurLevel * 2,
				BufferedImage.TYPE_BYTE_BINARY);
		Graphics g = blurBox.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0, 0, blurBoxWidth + blurLevel, blurBoxWidth + blurLevel);

		int rectWidth = (int) (resolutionScale);
		if (rectWidth == 0)
			rectWidth = 1;

		// Erase the white rectangle border from the right and button sides.
		g.setColor(Color.black);
		g.fillRect(rectWidth, rectWidth, blurBoxWidth + blurLevel, blurBoxWidth + blurLevel);

		// Use Gaussian blur on the box.
		float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
		blurBox = ImageHelper.convolveGrayscale(blurBox, kernel, true);

		// Multiply the image by blurBox. Also remove the padded edges off of
		// blurBox.
		assert image.getType() == BufferedImage.TYPE_BYTE_GRAY;
		WritableRaster imageRaster = image.getRaster();
		Raster blurBoxRaster = blurBox.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				float imageLevel = imageRaster.getSample(x, y, 0);

				// Retrieve the blur level as though blurBox has all 4 quadrants
				// and middle created, even has only the upper left.
				int blurBoxX;
				if (x > blurLevel)
				{
					if (image.getWidth() - x < blurLevel)
					{
						// x is under the right corner.
						blurBoxX = image.getWidth() - x;
					}
					else
					{
						// x is between the corners.
						blurBoxX = blurBoxWidth + 1;
					}
				}
				else
				{
					// x is under the left corner.
					blurBoxX = x;
				}

				int blurBoxY;
				if (y > blurLevel)
				{
					if (image.getHeight() - y < blurLevel)
					{
						// y is under the right corner.
						blurBoxY = image.getHeight() - y;
					}
					else
					{
						// x is between the corners.
						blurBoxY = blurBoxWidth + 1;
					}
				}
				else
				{
					// y is under the left corner.
					blurBoxY = y;
				}
				float blurBoxLevel = blurBoxRaster.getSample(blurBoxX, blurBoxY, 0);

				imageRaster.setSample(x, y, 0, (imageLevel * blurBoxLevel) / 255f);
			}
	}

	public static void drawRivers(MapSettings settings, WorldGraph graph, BufferedImage map, double sizeMultiplier,
			Collection<Edge> edgesToDraw, Rectangle drawBounds)
	{
		Graphics2D g = map.createGraphics();
		g.setColor(settings.riverColor);
		// Draw rivers thin.
		graph.drawRivers(g, sizeMultiplier, edgesToDraw, drawBounds);
	}

	public static Set<String> getAvailableBorderTypes()
	{
		File[] directories = new File(Paths.get(AssetsPath.get(), "borders").toString()).listFiles(File::isDirectory);
		return new TreeSet<String>(Arrays.stream(directories).map(file -> file.getName()).collect(Collectors.toList()));
	}

	public BufferedImage createHeightMap(MapSettings settings)
	{
		r = new Random(settings.randomSeed);
		DimensionDouble mapBounds = new Background(settings, null).calcMapBoundsAndAdjustResolutionIfNeeded(settings, null);
		double sizeMultiplier = calcSizeMultiplier(mapBounds.getWidth());
		WorldGraph graph = createGraph(settings, mapBounds.getWidth(), mapBounds.getHeight(), r, sizeMultiplier);
		return GraphCreator.createHeightMap(graph, new Random(settings.randomSeed));
	}

	private Set<Center> getCentersFromEdges(WorldGraph graph, Set<Edge> edges)
	{
		Set<Center> centers = new HashSet<Center>();
		for (Edge edge : edges)
		{
			if (edge.d0 != null)
			{
				centers.add(edge.d0);
			}

			if (edge.d1 != null)
			{
				centers.add(edge.d1);
			}
		}

		return centers;
	}

	private Set<Edge> getEdgesFromCenters(WorldGraph graph, Collection<Center> centers)
	{
		Set<Edge> edges = new HashSet<>();
		for (Center center : centers)
		{
			edges.addAll(center.borders);
		}
		return edges;
	}

	private List<CenterEdit> getCenterEditsForCenters(MapEdits edits, Collection<Center> centers)
	{
		return centers.stream().map(center -> edits.centerEdits.get(center.index)).collect(Collectors.toList());
	}

	private Set<EdgeEdit> getEdgeEditsForEdges(MapEdits edits, Collection<Edge> edges)
	{
		return edges.stream().map(edge -> edits.edgeEdits.get(edge.index)).collect(Collectors.toSet());
	}

	private Set<EdgeEdit> getEdgeEditsForCenters(MapEdits edits, Collection<Center> centers)
	{
		Set<EdgeEdit> edgeEdits = new HashSet<>();
		for (Center center : centers)
		{
			for (Edge edge : center.borders)
			{
				EdgeEdit edgeEdit = edits.edgeEdits.get(edge.index);
				if (edgeEdit != null)
				{
					edgeEdits.add(edgeEdit);
				}

			}
		}
		return edgeEdits;
	}

	// public static void main(String[] args) throws IOException
	// {
	// if (args.length > 1)
	// Logger.println("usage: MapCreator.java properties_filename");
	//
	// String propsFilename = "map_settings.properties";
	// if (args.length > 0)
	// propsFilename = args[0];
	// Properties props = new Properties();
	// props.load(new FileInputStream(propsFilename));
	//
	// MapSettings settings = new MapSettings(propsFilename);
	//
	// // settings.randomSeed = System.currentTimeMillis();
	//
	// BufferedImage map;
	// MapCreator creator = new MapCreator();
	//
	// try
	// {
	// map = creator.createMap(settings, null, null);
	// }
	// finally
	// {
	// ImageHelper.shutdownThreadPool();
	// }
	//
	// ImageHelper.openImageInSystemDefaultEditor(map, "map_" +
	// settings.randomSeed);
	//
	// }

}
