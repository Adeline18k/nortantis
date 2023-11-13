package nortantis;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.graph.geom.Point;
import nortantis.graph.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.swing.MapEdits;
import nortantis.util.AssetsPath;
import nortantis.util.Function;
import nortantis.util.HashMapF;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.Range;
import nortantis.util.ThreadHelper;
import nortantis.util.Tuple2;

public class IconDrawer
{
	public static final double mountainElevationThreshold = 0.58;
	public static final double hillElevationThreshold = 0.53;
	final double meanPolygonWidth;
	final int duneWidth;
	final double cityScale;
	// For hills and mountains, if a polygon is this number times meanPolygonWidth wide, no icon will be drawn on it.
	final double maxMeansToDraw = 5.0;
	double maxSizeToDrawIcon;
	// Max gap (in polygons) between mountains for considering them a single group. Warning:
	// there tend to be long polygons along edges, so if this value is much more than 2,
	// mountains near the ocean may be connected despite long distances between them..
	private final int maxGapSizeInMountainClusters = 2;
	private final int maxGapBetweenBiomeGroups = 2;
	// Mountain images are scaled by this.
	private final double mountainScale = 1.0;
	// Hill images are scaled by this.
	private final double hillScale = 0.5;
	private HashMapF<Center, List<IconDrawTask>> iconsToDraw;
	WorldGraph graph;
	Random rand;
	/**
	 * Used to store icons drawn when generating icons so they can be edited later by the editor.
	 */
	public Map<Integer, CenterIcon> centerIcons;
	public Map<Integer, CenterTrees> trees;
	private double averageCenterWidthBetweenNeighbors;
	private String cityIconType;
	private String imagesPath;
	private double resolutionScale;

	public IconDrawer(WorldGraph graph, Random rand, String cityIconsSetName, String customImagesPath, double resolutionScale)
	{
		iconsToDraw = new HashMapF<>(() -> new ArrayList<>(1));
		this.graph = graph;
		this.rand = rand;
		this.cityIconType = cityIconsSetName;
		if (customImagesPath != null && !customImagesPath.isEmpty())
		{
			this.imagesPath = customImagesPath;
		}
		else
		{
			this.imagesPath = AssetsPath.getInstallPath();
		}
		this.resolutionScale = resolutionScale;

		meanPolygonWidth = findMeanCenterWidth(graph);
		duneWidth = (int) (meanPolygonWidth * 1.2);
		maxSizeToDrawIcon = meanPolygonWidth * maxMeansToDraw;
		cityScale = meanPolygonWidth * (1.0 / 11.0);
		centerIcons = new HashMap<>();
		trees = new HashMap<>();

		averageCenterWidthBetweenNeighbors = findMeanCenterWidthBetweenNeighbors();
	}

	public static double findMeanCenterWidth(WorldGraph graph)
	{
		double widthSum = 0;
		int count = 0;
		for (Center center : graph.centers)
		{
			double width = center.findWidth();

			if (width > 0)
			{
				count++;
				widthSum += width;
			}
		}

		return widthSum / count;
	}

	private double findMeanCenterWidthBetweenNeighbors()
	{
		double sum = 0;
		for (Center c : graph.centers)
		{
			sum += findCenterWidthBetweenNeighbors(c);
		}
		return sum / graph.centers.size();
	}

	public void markMountains()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation > mountainElevationThreshold && !c.isCoast && !c.isBorder && c.findWidth() < maxSizeToDrawIcon)
			{
				c.isMountain = true;
			}
		}
	}

	public void markHills()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation < mountainElevationThreshold && c.elevation > hillElevationThreshold && !c.isCoast
					&& c.findWidth() < maxSizeToDrawIcon)

			{
				c.isHill = true;
			}
		}
	}

	public void markCities(double cityProbability)
	{
		for (Center c : graph.centers)
		{
			if (!c.isMountain && !c.isHill && !c.isWater && !c.isBorder && !c.neighbors.stream().anyMatch(n -> n.isBorder))
			{
				// I'm generating these numbers now instead of waiting to see if they are needed in the if statements below because
				// there is a problem in the graph such that maps generated at different resolutions can have slight differences in their
				// rivers, which appears to be caused by floating point precision issues while calculating elevation of corners.
				// Thus, the slightest change in a river on one corner could cause a center to change whether it's a river, which
				// would modify the way the random number generator is called, which would then change everything else used by that
				// random number generator after it. But this fix only reduces the issue, since other things will also change
				// when rivers move.
				double cityByRiverProbability = rand.nextDouble();
				double cityByCoastProbability = rand.nextDouble();
				double randomCityProbability = rand.nextDouble();
				double byMountainCityProbability = rand.nextDouble();
				if (c.isRiver() && cityByRiverProbability <= cityProbability * 2)
				{
					c.isCity = true;
				}
				else if (c.isCoast && cityByCoastProbability <= cityProbability * 2)
				{
					c.isCity = true;
				}
				else if (c.neighbors.stream().anyMatch(n -> n.isMountain) && byMountainCityProbability <= cityProbability * 1.6)
				{
					c.isCity = true;
				}
				else if (randomCityProbability <= cityProbability)
				{
					c.isCity = true;
				}
			}
		}
	}

	public List<Set<Center>> findMountainGroups()
	{
		List<Set<Center>> mountainGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.isMountain;
			}
		});

		return mountainGroups;

	}

	/**
	 * Finds and marks mountain ranges, and groups smaller than ranges, and surrounding hills.
	 */
	public List<Set<Center>> findMountainAndHillGroups()
	{
		List<Set<Center>> mountainAndHillGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.isMountain || center.isHill;
			}
		});

		// Assign mountain group ids to each center that is in a mountain group.
		int curId = 0;
		for (Set<Center> group : mountainAndHillGroups)
		{
			for (Center c : group)
			{
				c.mountainRangeId = curId;
			}
			curId++;
		}

		return mountainAndHillGroups;

	}


	/**
	 * This is used to add icon to draw tasks from map edits rather than using the generator to add them. The actual drawing of the icons is
	 * done later.
	 */
	public void addOrUpdateIconsFromEdits(MapEdits edits, double sizeMultiplyer, Collection<Center> centersToUpdateIconsFor,
			double treeHeightScale)
	{
		clearIconsForCenters(centersToUpdateIconsFor);

		ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(imagesPath)
				.getAllIconGroupsAndMasksForType(IconType.mountains);
		ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.hills);
		List<ImageAndMasks> duneImages = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.sand).get("dunes");
		Map<String, Tuple2<ImageAndMasks, Integer>> cityImages = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities,
				cityIconType);

		for (Center center : centersToUpdateIconsFor)
		{
			CenterEdit cEdit = edits.centerEdits.get(center.index);
			if (cEdit.icon != null)
			{
				if (cEdit.icon.iconType == CenterIconType.Mountain && cEdit.icon.iconGroupId != null && !mountainImagesById.isEmpty())
				{
					String groupId = cEdit.icon.iconGroupId;
					if (!mountainImagesById.containsKey(groupId))
					{
						// Someone removed the icon group. Choose a new group.
						groupId = chooseNewGroupId(mountainImagesById.keySet(), groupId);
					}
					if (mountainImagesById.get(groupId).size() > 0)
					{
						int scaledSize = findScaledMountainSize(center);
						ImageAndMasks imageAndMasks = mountainImagesById.get(groupId)
								.get(cEdit.icon.iconIndex % mountainImagesById.get(groupId).size());
						iconsToDraw.getOrCreate(center)
								.add(new IconDrawTask(imageAndMasks, IconType.mountains, center.loc, scaledSize, true, false));
					}
				}
				else if (cEdit.icon.iconType == CenterIconType.Hill && cEdit.icon.iconGroupId != null && !hillImagesById.isEmpty())
				{
					String groupId = cEdit.icon.iconGroupId;
					if (!hillImagesById.containsKey(groupId))
					{
						// Someone removed the icon group. Choose a new group.
						groupId = chooseNewGroupId(hillImagesById.keySet(), groupId);
					}
					if (hillImagesById.get(groupId).size() > 0)
					{
						int scaledSize = findScaledHillSize(center);
						ImageAndMasks imageAndMasks = hillImagesById.get(groupId)
								.get(cEdit.icon.iconIndex % hillImagesById.get(groupId).size());
						iconsToDraw.getOrCreate(center)
								.add(new IconDrawTask(imageAndMasks, IconType.hills, center.loc, scaledSize, true, false));
					}
				}
				else if (cEdit.icon.iconType == CenterIconType.Dune && duneWidth > 0 && duneImages != null && !duneImages.isEmpty())
				{
					ImageAndMasks imageAndMasks = duneImages.get(cEdit.icon.iconIndex % duneImages.size());
					iconsToDraw.getOrCreate(center).add(new IconDrawTask(imageAndMasks, IconType.sand, center.loc, duneWidth, true, false));
				}
				else if (cEdit.icon.iconType == CenterIconType.City && cityImages != null && !cityImages.isEmpty())
				{
					String cityIconName = null;
					if (cityImages.containsKey(cEdit.icon.iconName))
					{
						cityIconName = cEdit.icon.iconName;
					}
					else if (cityImages.size() > 0)
					{
						// Either the city image is missing, or the icon set name changed. Choose a new image in a deterministic but
						// random way.
						cityIconName = chooseNewCityIconName(cityImages.keySet(), cEdit.icon.iconName);
						if (cityIconName != null)
						{
							// Store the city icon name so that if someone later adds or removes other city icons, it doesn't affect
							// which one is used for this center.
							cEdit.icon.iconName = cityIconName;
						}
					}
					if (cityIconName != null)
					{
						ImageAndMasks imageAndMasks = cityImages.get(cityIconName).getFirst();
						iconsToDraw.getOrCreate(center).add(new IconDrawTask(imageAndMasks, IconType.cities, center.loc,
								(int) (cityImages.get(cityIconName).getSecond() * cityScale), true, true, cityIconName));
					}
				}

			}

			if (cEdit.trees != null)
			{
				trees.put(cEdit.index, cEdit.trees);
			}
		}

		drawTreesForCenters(centersToUpdateIconsFor, treeHeightScale);
	}

	public static String chooseNewCityIconName(Set<String> cityNamesToChooseFrom, String oldIconName)
	{
		List<CityType> oldTypes = TextDrawer.findCityTypeFromCityFileName(oldIconName);
		List<String> compatibleCities = cityNamesToChooseFrom.stream()
				.filter(name -> TextDrawer.findCityTypeFromCityFileName(name).stream().anyMatch(type -> oldTypes.contains(type)))
				.collect(Collectors.toList());
		if (compatibleCities.isEmpty())
		{
			int index = Math.abs(oldIconName.hashCode() % cityNamesToChooseFrom.size());
			return new ArrayList<>(cityNamesToChooseFrom).get(index);
		}
		int index = Math.abs(oldIconName.hashCode() % compatibleCities.size());
		return compatibleCities.get(index);
	}

	public boolean doesCityFitOnLand(Center center, CenterIcon cityIcon, boolean allowTopsOfIconsToOverlapOcean)
	{
		if (center == null || cityIcon == null)
		{
			return true;
		}

		Map<String, Tuple2<ImageAndMasks, Integer>> cityImages = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities,
				cityIconType);
		Tuple2<ImageAndMasks, Integer> tuple = cityImages.get(cityIcon.iconName);
		if (tuple == null)
		{
			// Not a city icon
			return false;
		}

		ImageAndMasks imageAndMasks = cityImages.get(cityIcon.iconName).getFirst();

		// Create an icon draw task just for checking if the city fits on land. It won't actually be drawn.
		IconDrawTask task = new IconDrawTask(imageAndMasks, IconType.cities, center.loc,
				(int) (cityImages.get(cityIcon.iconName).getSecond() * cityScale), true, true, cityIcon.iconName);
		return !isIconTouchingWater(task, allowTopsOfIconsToOverlapOcean);
	}

	private void clearIconsForCenters(Collection<Center> centers)
	{
		for (Center center : centers)
		{
			if (iconsToDraw.containsKey(center))
			{
				iconsToDraw.get(center).clear();
			}

			if (trees.containsKey(center.index))
			{
				trees.put(center.index, null);
			}

			if (centerIcons.containsKey(center.index))
			{
				centerIcons.put(center.index, null);
			}
		}
	}

	private String chooseNewGroupId(Set<String> groupIds, String oldGroupId)
	{
		int index = Math.abs(oldGroupId.hashCode() % groupIds.size());
		return groupIds.toArray(new String[groupIds.size()])[index];
	}

	/**
	 * Finds groups of centers that accepted according to a given function. A group is a set of centers for which there exists a path from
	 * any member of the set to any other such that you never have to skip over more than maxGapSize centers not accepted at once to get to
	 * that other center. If distanceThreshold > 1, the result will include those centers which connect centeres that are accepted.
	 */
	private static List<Set<Center>> findCenterGroups(WorldGraph graph, int maxGapSize, Function<Center, Boolean> accept)
	{
		List<Set<Center>> groups = new ArrayList<>();
		// Contains all explored centers in this graph. This prevents me from making a new group
		// for every center.
		Set<Center> explored = new HashSet<>();
		for (Center center : graph.centers)
		{
			if (accept.apply(center) && !explored.contains(center))
			{
				// Do a breadth-first-search from that center, creating a new group.
				// "frontier" maps centers to their distance from a center of the desired biome.
				// 0 means it is of the desired biome.
				Map<Center, Integer> frontier = new HashMap<>();
				frontier.put(center, 0);
				Set<Center> group = new HashSet<>();
				group.add(center);
				while (!frontier.isEmpty())
				{
					Map<Center, Integer> nextFrontier = new HashMap<>();
					for (Map.Entry<Center, Integer> entry : frontier.entrySet())
					{
						for (Center n : entry.getKey().neighbors)
						{
							if (!explored.contains(n))
							{
								if (accept.apply(n))
								{
									explored.add(n);
									group.add(n);
									nextFrontier.put(n, 0);
								}
								else if (entry.getValue() < maxGapSize)
								{
									int newDistance = entry.getValue() + 1;
									nextFrontier.put(n, newDistance);
								}
							}
						}
					}
					frontier = nextFrontier;
				}
				groups.add(group);

			}
		}
		return groups;
	}

	/**
	 * 
	 * = * @param mask A gray scale image which is white where the background should be drawn, and black where the map should be drawn
	 * instead of the background. This is necessary so that when I draw an icon that is transparent (such as a hand drawn mountain), I
	 * cannot see other mountains through it.
	 */
	private void drawIconWithBackgroundAndMask(BufferedImage mapOrSnippet, ImageAndMasks imageAndMasks, BufferedImage backgroundOrSnippet,
			int xCenter, int yCenter, boolean ignoreMaxSize, boolean allowTopsOfIconsToOverlapOcean)
	{
		BufferedImage icon = imageAndMasks.image;
		BufferedImage contentMask = imageAndMasks.getOrCreateContentMask();

		if (mapOrSnippet.getWidth() != backgroundOrSnippet.getWidth())
			throw new IllegalArgumentException();
		if (mapOrSnippet.getHeight() != backgroundOrSnippet.getHeight())
			throw new IllegalArgumentException();
		if (contentMask.getWidth() != icon.getWidth())
			throw new IllegalArgumentException("The given content mask's width does not match the icon's width.");
		if (contentMask.getHeight() != icon.getHeight())
			throw new IllegalArgumentException("The given content mask's height does not match the icon's height.");
		BufferedImage shadingMask = null;
		if (allowTopsOfIconsToOverlapOcean)
		{
			shadingMask = imageAndMasks.getOrCreateShadingMask();
			if (shadingMask.getWidth() != icon.getWidth())
				throw new IllegalArgumentException("The given shading mask's width does not match the icon's width.");
			if (shadingMask.getHeight() != icon.getHeight())
				throw new IllegalArgumentException("The given shading mask's height does not match the icon's height.");

		}

		int xLeft = xCenter - icon.getWidth() / 2;
		int yBottom = yCenter - icon.getHeight() / 2;

		Raster maskRaster = contentMask.getRaster();
		for (int x : new Range(icon.getWidth()))
			for (int y : new Range(icon.getHeight()))
			{
				Color iconColor = new Color(icon.getRGB(x, y), true);
				double alpha = iconColor.getAlpha() / 255.0;
				// grey level of mask at the corresponding pixel in mask.
				double maskLevel = maskRaster.getSampleDouble(x, y, 0);
				Color bgColor;
				Color mapColor;
				// Find the location on the background and map where this pixel will be drawn.
				int xLoc = xLeft + x;
				int yLoc = yBottom + y;
				try
				{
					bgColor = new Color(backgroundOrSnippet.getRGB(xLoc, yLoc));
					mapColor = new Color(mapOrSnippet.getRGB(xLoc, yLoc));
				}
				catch (IndexOutOfBoundsException e)
				{
					// Skip this pixel.
					continue;
				}

				int red = (int) (alpha * (iconColor.getRed())
						+ (1 - alpha) * (maskLevel * bgColor.getRed() + (1 - maskLevel) * mapColor.getRed()));
				int green = (int) (alpha * (iconColor.getGreen())
						+ (1 - alpha) * (maskLevel * bgColor.getGreen() + (1 - maskLevel) * mapColor.getGreen()));
				int blue = (int) (alpha * (iconColor.getBlue())
						+ (1 - alpha) * (maskLevel * bgColor.getBlue() + (1 - maskLevel) * mapColor.getBlue()));

				mapOrSnippet.setRGB(xLoc, yLoc, new Color(red, green, blue).getRGB());
			}
	}

	/**
	 * Draws all icons in iconsToDraw that touch drawBounds.
	 * 
	 * I draw all the icons at once this way so that I can sort the icons by the y-coordinate of the base of each icon. This way icons lower
	 * on the map are drawn in front of those that are higher.
	 */
	public void drawAllIcons(BufferedImage mapOrSnippet, BufferedImage background, Rectangle drawBounds,
			boolean allowTopsOfIconsToOverlapOcean)
	{
		List<IconDrawTask> tasks = new ArrayList<IconDrawTask>();
		for (Map.Entry<Center, List<IconDrawTask>> entry : iconsToDraw.entrySet())
		{
			if (!entry.getKey().isWater)
			{
				for (final IconDrawTask task : entry.getValue())
				{
					if (drawBounds == null || task.overlaps(drawBounds))
					{
						if (!task.ignoreMaxSize && task.scaledWidth > maxSizeToDrawIcon)
						{
							task.failedToDraw = true;
							continue;
						}

						// Updates to the line below will will likely need to also update doesCityFitOnLand.
						if (!isIconTouchingWater(task, allowTopsOfIconsToOverlapOcean))
						{
							tasks.add(task);
						}
						else
						{
							task.failedToDraw = true;
						}
					}
				}
			}
		}
		Collections.sort(tasks);

		// Force mask creation now if it hasn't already happened so that so that multiple threads don't try to create the same masks at the
		// same time and end up repeating work.
		for (final IconDrawTask task : tasks)
		{
			task.imageAndMasks.getOrCreateContentMask();
			if (allowTopsOfIconsToOverlapOcean)
			{
				task.imageAndMasks.getOrCreateShadingMask();
			}
		}

		// Scale the icons in parallel.
		{
			List<Runnable> jobs = new ArrayList<>();
			for (final IconDrawTask task : tasks)
			{
				jobs.add(new Runnable()
				{
					@Override
					public void run()
					{
						task.scaleIcon(allowTopsOfIconsToOverlapOcean);
					}
				});
			}
			ThreadHelper.getInstance().processInParallel(jobs, true);
		}

		int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
		int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

		for (final IconDrawTask task : tasks)
		{
			drawIconWithBackgroundAndMask(mapOrSnippet, task.imageAndMasks, background, ((int) task.centerLoc.x) - xToSubtract,
					((int) task.centerLoc.y) - yToSubtract, task.ignoreMaxSize, allowTopsOfIconsToOverlapOcean);
		}
	}

	/**
	 * Removes icon edits for centers if the icon failed to draw. That way it doesn't show up later when you draw more land around the
	 * center and thus give the icon enough space to draw.
	 * 
	 * @param edits
	 * @param graph
	 */
	public void removeIconEditsThatFailedToDraw(MapEdits edits, WorldGraph graph)
	{
		for (CenterEdit cEdit : edits.centerEdits)
		{
			// I only handle center icons, not trees, since I don't want to stop drawing all trees for a center just because
			// some of them ended up in the ocean.

			if (cEdit.icon == null)
			{
				continue;
			}

			Center center = graph.centers.get(cEdit.index);
			List<IconDrawTask> tasks = iconsToDraw.get(center);
			if (tasks != null && tasks.stream().anyMatch(task -> task.type != IconType.trees && task.failedToDraw))
			{
				cEdit.icon = null;
				List<IconDrawTask> tasksToRemove = tasks.stream().filter(task -> task.type != IconType.trees && task.failedToDraw)
						.collect(Collectors.toList());
				for (IconDrawTask toRemove : tasksToRemove)
				{
					tasks.remove(toRemove);
				}
			}
		}
	}

	/**
	 * Adds icon draw tasks to draw cities. Side effect � if a city is placed where it cannot be drawn, this will un-mark it as a city.
	 * 
	 * @return IconDrawTask of each city icon added. Needed to avoid drawing text on top of cities.
	 */
	public List<IconDrawTask> addOrUnmarkCities(double sizeMultiplyer, boolean addIconDrawTasks, boolean allowTopsOfIconsToOverlapOcean)
	{
		Map<String, Tuple2<ImageAndMasks, Integer>> cityIcons = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities,
				cityIconType);
		if (cityIcons.isEmpty())
		{
			Logger.println("Cities will not be drawn because there are no city icons.");
			return new ArrayList<>(0);
		}

		List<String> cityNames = new ArrayList<>(cityIcons.keySet());

		List<IconDrawTask> cities = new ArrayList<>();

		for (Center c : graph.centers)
		{
			if (c.isCity)
			{
				String cityName = cityNames.get(rand.nextInt(cityNames.size()));
				int scaledWidth = (int) (cityIcons.get(cityName).getSecond() * cityScale);
				ImageAndMasks imageAndMasks = cityIcons.get(cityName).getFirst();

				IconDrawTask task = new IconDrawTask(imageAndMasks, IconType.cities, c.loc, scaledWidth, true, true, cityName);
				// Updates to the line below will will likely need to also update doesCityFitOnLand.
				if (!isIconTouchingWater(task, allowTopsOfIconsToOverlapOcean) && !isNeighborACity(c))
				{
					if (addIconDrawTasks)
					{
						iconsToDraw.getOrCreate(c).add(task);
						centerIcons.put(c.index, new CenterIcon(CenterIconType.City, cityName));
					}

					cities.add(task);
				}
				else
				{
					c.isCity = false;
				}
			}
		}

		return cities;
	}

	private boolean isNeighborACity(Center center)
	{
		return center.neighbors.stream().anyMatch(c -> c.isCity);
	}

	/**
	 * Creates tasks for drawing mountains and hills.
	 * 
	 * @return
	 */
	public void addOrUnmarkMountainsAndHills(List<Set<Center>> mountainAndHillGroups, boolean allowTopsOfIconsToOverlapOcean)
	{
		// Maps mountain range ids (the ids in the file names) to list of mountain images and their masks.
		ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(imagesPath)
				.getAllIconGroupsAndMasksForType(IconType.mountains);

		// Maps mountain range ids (the ids in the file names) to list of hill images and their masks.
		// The hill image file names must use the same ids as the mountain ranges.
		ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.hills);

		// Warn if images are missing
		for (String hillGroupId : hillImagesById.keySet())
		{
			if (!mountainImagesById.containsKey(hillGroupId))
			{
				Logger.println("No mountain images found for the hill group \"" + hillGroupId + "\". Those hill images will be ignored.");
			}
		}
		for (String mountainGroupId : mountainImagesById.keySet())
		{
			if (!hillImagesById.containsKey(mountainGroupId))
			{
				Logger.println("No hill images found for the mountain group \"" + mountainGroupId
						+ "\". That mountain group will not have hills.");
			}
		}

		// Maps from the mountainRangeId of Centers to the range id's from the mountain image file names.
		Map<Integer, String> rangeMap = new TreeMap<>();

		for (Set<Center> group : mountainAndHillGroups)
		{
			for (Center c : group)
			{
				String fileNameRangeId = rangeMap.get(c.mountainRangeId);
				if ((fileNameRangeId == null))
				{
					fileNameRangeId = new ArrayList<>(mountainImagesById.keySet()).get(rand.nextInt(mountainImagesById.keySet().size()));
					rangeMap.put(c.mountainRangeId, fileNameRangeId);
				}

				if (c.isMountain)
				{
					List<ImageAndMasks> imagesInRange = mountainImagesById.get(fileNameRangeId);

					// I'm deliberately putting this line before checking center size so that the
					// random number generator is used the same no matter what resolution the map
					// is drawn at.
					int i = rand.nextInt(imagesInRange.size());

					int scaledSize = findScaledMountainSize(c);

					// Make sure the image will be at least 1 pixel wide.
					if (scaledSize >= 1)
					{
						IconDrawTask task = new IconDrawTask(imagesInRange.get(i), IconType.mountains, c.loc, scaledSize, true, false);

						if (!isIconTouchingWater(task, allowTopsOfIconsToOverlapOcean))
						{
							// Draw the image such that it is centered in the center of c.
							iconsToDraw.getOrCreate(c).add(task);
							centerIcons.put(c.index, new CenterIcon(CenterIconType.Mountain, fileNameRangeId, i));
						}
						else
						{
							c.isMountain = false;
						}
					}
				}
				else if (c.isHill)
				{
					List<ImageAndMasks> imagesInGroup = hillImagesById.get(fileNameRangeId);

					if (imagesInGroup != null && !imagesInGroup.isEmpty())
					{
						int i = rand.nextInt(imagesInGroup.size());

						int scaledSize = findScaledHillSize(c);

						// Make sure the image will be at least 1 pixel wide.
						if (scaledSize >= 1)
						{
							IconDrawTask task = new IconDrawTask(imagesInGroup.get(i), IconType.hills, c.loc, scaledSize, true, false);
							if (!isIconTouchingWater(task, allowTopsOfIconsToOverlapOcean))
							{
								iconsToDraw.getOrCreate(c).add(task);
								centerIcons.put(c.index, new CenterIcon(CenterIconType.Hill, fileNameRangeId, i));
							}
							else
							{
								c.isHill = false;
							}
						}
					}
				}
			}
		}
	}

	private int findScaledMountainSize(Center c)
	{
		// Find the center's size along the x axis.
		double cSize = findCenterWidthBetweenNeighbors(c);
		return (int) (cSize * mountainScale);
	}

	private int findScaledHillSize(Center c)
	{
		// Find the center's size along the x axis.
		double cSize = findCenterWidthBetweenNeighbors(c);
		return (int) (cSize * hillScale);
	}

	public void addSandDunes()
	{
		ListMap<String, ImageAndMasks> sandGroups = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.sand);
		if (sandGroups == null || sandGroups.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand images were found.");
			return;
		}

		// Load the sand dune images.
		List<ImageAndMasks> duneImages = sandGroups.get("dunes");

		if (duneImages == null || duneImages.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand dune images were found.");
			return;
		}

		List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.biome.equals(Biome.TEMPERATE_DESERT);
			}
		});

		// This is the probability that a temperate desert will be a dune field.
		double duneProbabilityPerBiomeGroup = 0.6;
		double duneProbabilityPerCenter = 0.5;

		if (duneWidth == 0)
		{
			return;
		}

		for (Set<Center> group : groups)
		{
			if (rand.nextDouble() < duneProbabilityPerBiomeGroup)
			{
				for (Center c : group)
				{
					if (rand.nextDouble() < duneProbabilityPerCenter)
					{
						c.isSandDunes = true;

						int i = rand.nextInt(duneImages.size());
						iconsToDraw.getOrCreate(c).add(new IconDrawTask(duneImages.get(i), IconType.sand, c.loc, duneWidth, true, false));
						centerIcons.put(c.index, new CenterIcon(CenterIconType.Dune, "sand", i));
					}
				}

			}
		}
	}

	public void addTrees(double treeHeightScale)
	{
		addCenterTrees();
		drawTreesForCenters(graph.centers, treeHeightScale);
	}

	public static Set<TreeType> getTreeTypesForBiome(Biome biome)
	{
		Set<TreeType> result = new TreeSet<TreeType>();
		for (final ForestType forest : forestTypes)
		{
			if (forest.biome == biome)
			{
				result.add(forest.treeType);
			}
		}
		return result;
	}

	public static Set<Biome> getBiomesForTreeType(TreeType type)
	{
		Set<Biome> result = new TreeSet<>();
		for (final ForestType forest : forestTypes)
		{
			if (forest.treeType == type)
			{
				result.add(forest.biome);
			}
		}
		return result;
	}

	private static List<ForestType> forestTypes;
	static
	{
		forestTypes = new ArrayList<>();
		forestTypes.add(new ForestType(TreeType.Deciduous, Biome.TEMPERATE_RAIN_FOREST, 0.5, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.TAIGA, 1.0, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.SHRUBLAND, 1.0, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.HIGH_TEMPERATE_DECIDUOUS_FOREST, 1.0, 0.25));
		forestTypes.add(new ForestType(TreeType.Cacti, Biome.HIGH_TEMPERATE_DESERT, 1.0 / 8.0, 0.1));
	}

	private void addCenterTrees()
	{
		trees.clear();

		for (final ForestType forest : forestTypes)
		{
			if (forest.biomeFrequency != 1.0)
			{
				List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, new Function<Center, Boolean>()
				{
					public Boolean apply(Center center)
					{
						return center.biome.equals(forest.biome);
					}
				});
				for (Set<Center> group : groups)
				{
					if (rand.nextDouble() < forest.biomeFrequency)
					{
						for (Center c : group)
						{
							if (canGenerateTreesOnCenter(c))
							{
								trees.put(c.index, new CenterTrees(forest.treeType.toString().toLowerCase(), forest.density, c.treeSeed));
							}
						}
					}
				}
			}
		}

		// Process forest types that don't use biome groups separately for efficiency.
		for (Center c : graph.centers)
		{
			for (ForestType forest : forestTypes)
			{
				if (forest.biomeFrequency == 1.0)
				{
					if (forest.biome.equals(c.biome))
					{
						if (canGenerateTreesOnCenter(c))
						{
							trees.put(c.index, new CenterTrees(forest.treeType.toString().toLowerCase(), forest.density, c.treeSeed));
						}
					}
				}

			}
		}
	}

	private boolean canGenerateTreesOnCenter(Center c)
	{
		return c.elevation < mountainElevationThreshold && !c.isWater && !c.isCoast;
	}

	/**
	 * Draws all trees in this.trees.
	 */
	public void drawTreesForCenters(Collection<Center> centersToDraw, double treeHeightScale)
	{
		// Load the images and masks.
		ListMap<String, ImageAndMasks> treesById = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.trees);
		if (treesById == null || treesById.isEmpty())
		{
			Logger.println("Trees will not be drawn because no tree images were found.");
			return;
		}

		// Make the tree images small. I make them all the same height.
		int scaledHeight = (int) (averageCenterWidthBetweenNeighbors * treeHeightScale);
		if (scaledHeight == 0)
		{
			// Don't draw trees if they would all be size zero.
			return;
		}

		// Scale the tree images. Note that images in treesById should not be modified because treesById is cached and so can be re-used.
		ListMap<String, ImageAndMasks> scaledTreesById = new ListMap<>();
		for (Map.Entry<String, List<ImageAndMasks>> entry : treesById.entrySet())
		{
			String groupName = entry.getKey();
			List<ImageAndMasks> imageGroup = entry.getValue();
			List<ImageAndMasks> scaledImageGroup = new ArrayList<>();
			scaledTreesById.put(groupName, scaledImageGroup);

			for (ImageAndMasks imageAndMasks : imageGroup)
			{
				ImageAndMasks scaled = new ImageAndMasks(
						ImageCache.getInstance(imagesPath).getScaledImageByHeight(imageAndMasks.image, scaledHeight));
				scaledTreesById.add(groupName, scaled);
			}
		}

		// The purpose of the number below is to make it so that adjusting the height of trees also adjusts the density so that the spacing
		// between trees remains
		// looking about the same. As for how I calculated this number, the minimum treeHeightScale is 0.1, and each tick on the tree height
		// slider increases by 0.05,
		// with the highest possible value being 0.85. So I then fitted a curve to (0.1, 12), (0.35, 2), (0.5, 1.0), (0.65, 0.6) and (0.85,
		// 0.3).
		// The first point is the minimum tree height. The second is the default. The third is the old default. The fourth is the maximum.
		double densityScale = 2.0 * ((71.5152) * (treeHeightScale * treeHeightScale * treeHeightScale * treeHeightScale)
				- 178.061 * (treeHeightScale * treeHeightScale * treeHeightScale) + 164.876 * (treeHeightScale * treeHeightScale)
				- 68.633 * treeHeightScale + 11.3855);

		for (Center c : centersToDraw)
		{
			CenterTrees cTrees = trees.get(c.index);
			if (cTrees != null)
			{
				if (cTrees.treeType != null && scaledTreesById.containsKey(cTrees.treeType))
				{
					drawTreesAtCenterAndCorners(graph, cTrees.density * densityScale, scaledTreesById.get(cTrees.treeType), c);
				}
			}
		}
	}

	private void drawTreesAtCenterAndCorners(WorldGraph graph, double density, List<ImageAndMasks> images, Center center)
	{
		CenterTrees cTrees = trees.get(center.index);
		Random rand = new Random(cTrees.randomSeed);
		drawTrees(graph, images, center.loc, density, center, rand);

		// Draw trees at the neighboring corners too.
		// Note that corners use their own Random instance because the random seed of that random needs to not depend on the center or else
		// trees would be placed differently based on which center drew first.
		for (Corner corner : center.corners)
		{
			if (shouldCenterDrawTreesForCorner(center, corner))
			{
				drawTrees(graph, images, corner.loc, density, center, rand);
			}
		}
	}

	/**
	 * Ensures at most 1 center draws trees at each corner.
	 */
	private boolean shouldCenterDrawTreesForCorner(Center center, Corner corner)
	{
		Center centerWithSmallestIndex = null;
		for (Center t : corner.touches)
		{
			CenterTrees tTrees = trees.get(t.index);
			if (tTrees == null || tTrees.treeType == null || tTrees.treeType.equals(""))
			{
				continue;
			}

			if (centerWithSmallestIndex == null || (t.index < centerWithSmallestIndex.index))
			{
				centerWithSmallestIndex = t;
			}
		}

		if (centerWithSmallestIndex != null)
		{
			return center.index == centerWithSmallestIndex.index;
		}
		return true;
	}

	private static class ForestType
	{
		TreeType treeType;
		Biome biome;
		double density;
		double biomeFrequency;

		/**
		 * @param biomeProb
		 *            If this is not 1.0, groups of centers of biome type "biome" will be found and each groups will have this type of
		 *            forest with probability biomeProb.
		 */
		public ForestType(TreeType treeType, Biome biome, double density, double biomeFrequency)
		{
			this.treeType = treeType;
			this.biome = biome;
			this.density = density;
			this.biomeFrequency = biomeFrequency;
		}
	};

	private double findCenterWidthBetweenNeighbors(Center c)
	{
		Center eastMostNeighbor = Collections.max(c.neighbors, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		});
		Center westMostNeighbor = Collections.min(c.neighbors, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		});
		double cSize = Math.abs(eastMostNeighbor.loc.x - westMostNeighbor.loc.x);
		return cSize;
	}

	private void drawTrees(WorldGraph graph, List<ImageAndMasks> images, Point loc, double forestDensity, Center center, Random rand)
	{
		if (images == null || images.isEmpty())
		{
			return;
		}

		// Convert the forestDensity into an integer number of trees to draw such that the expected
		// value is forestDensity.
		double fraction = forestDensity - (int) forestDensity;
		int extra = rand.nextDouble() < fraction ? 1 : 0;
		int numTrees = ((int) forestDensity) + extra;

		for (int i = 0; i < numTrees; i++)
		{
			int index = rand.nextInt(images.size());
			ImageAndMasks imageAndMasks = images.get(index);

			// Draw the image such that it is centered in the center of c.
			int x = (int) (loc.x);
			int y = (int) (loc.y);

			final double scale = (averageCenterWidthBetweenNeighbors / 10.0);
			x += rand.nextGaussian() * scale;
			y += rand.nextGaussian() * scale;

			iconsToDraw.getOrCreate(center).add(
					new IconDrawTask(imageAndMasks, IconType.trees, new Point(x, y), (int) imageAndMasks.image.getWidth(), false, false));
		}
	}

	private boolean isIconTouchingWater(IconDrawTask iconTask, boolean allowTopsOfIconsToOverlapOcean)
	{
		// TODO Use allowTopsOfIconsToOverlapOcean

		if (iconTask.imageAndMasks.getOrCreateContentMask().getType() != BufferedImage.TYPE_BYTE_BINARY)
			throw new IllegalArgumentException("Mask type must be TYPE_BYTE_BINARY for checking whether icons touch water.");

		final int imageUpperLeftX = (int) iconTask.centerLoc.x - iconTask.scaledWidth / 2;
		final int imageUpperLeftY = (int) iconTask.centerLoc.y - iconTask.scaledHeight / 2;

		// Determining what points to check needs to be done in a resolution invariant way. Otherwise, generating the map at
		// a higher or lower resolution could cause icons to appear or disappear if they are very close to water.
		// Although, due to pixels being discrete, this technique isn't entirely resolution invariant.

		final double stepSize = 2.0 / resolutionScale; // The constant in this number is in number of pixels at 100% resolution.
		final double widthResolutionInvariant = iconTask.scaledWidth / resolutionScale;
		final double heightResolutionInvariant = iconTask.scaledHeight / resolutionScale;


		Raster mRaster = iconTask.imageAndMasks.getOrCreateContentMask().getRaster();
		final double xScale = (((double) iconTask.imageAndMasks.getOrCreateContentMask().getWidth()) / iconTask.scaledWidth)
				* resolutionScale;
		final double yScale = (((double) iconTask.imageAndMasks.getOrCreateContentMask().getHeight()) / iconTask.scaledHeight)
				* resolutionScale;
		for (double x = 0; x < widthResolutionInvariant; x += stepSize)
		{
			for (double y = 0; y < heightResolutionInvariant; y += stepSize)
			{
				// Only check pixels where the mask level is greater than 0 because we don't care if transparent
				// pixels outside the image's content overlap with water.
				int xInMask = (int) (x * xScale);
				int yInMask = (int) (y * yScale);
				if (xInMask < 0 || xInMask >= mRaster.getWidth())
				{
					continue;
				}
				if (yInMask < 0 || yInMask >= mRaster.getHeight())
				{
					continue;
				}

				// Require trees to be a little further from water because they look bad, in my opinion, when there's a bunch of them
				// right against the coast.
				if ((iconTask.type != IconType.trees && mRaster.getSampleDouble(xInMask, yInMask, 0) > 0)
						|| (iconTask.type == IconType.trees && overlapsOrIsNearMask(mRaster, xInMask, yInMask)))
				{
					Center center = graph.findClosestCenter(imageUpperLeftX + x * resolutionScale, imageUpperLeftY + y * resolutionScale);
					if (center.isWater)
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean overlapsOrIsNearMask(Raster mRaster, int xInMask, int yInMask)
	{
		// This is the number of pixels at 100% resolution of offset icons will have from water.
		final int bufferSize = (int) (5.0 * resolutionScale);

		final int increment = Math.max(1, (int) (5.0 * resolutionScale));

		for (int bx = -bufferSize; bx <= bufferSize; bx += increment)
		{
			if (xInMask + bx < 0 || xInMask + bx >= mRaster.getWidth())
			{
				continue;
			}

			for (int by = -bufferSize; by <= bufferSize; by += increment)
			{
				if (yInMask + by < 0 || yInMask + by >= mRaster.getHeight())
				{
					continue;
				}

				if (mRaster.getSampleDouble(xInMask + bx, yInMask + by, 0) > 0)
				{
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Creates a bounding box that includes all icons drawn for a center the last time icons were drawn for it.
	 * 
	 * @param center
	 *            Center to get icons bounds for.
	 * @return A rectangle if center had icons drawn. Null otherwise.
	 */
	private Rectangle getBoundingBoxOfIconsForCenter(Center center)
	{
		if (iconsToDraw.get(center) == null)
		{
			return null;
		}

		Rectangle bounds = null;
		for (IconDrawTask iconTask : iconsToDraw.get(center))
		{
			if (bounds == null)
			{
				bounds = iconTask.createBounds();
			}
			else
			{
				bounds.add(iconTask.createBounds());
			}
		}

		return bounds;
	}

	/**
	 * Creates a bounding box that includes all icons drawn for a collection of centers from the last time icons were drawn.
	 * 
	 * @param centers
	 *            A collection of centers to get icons bounds for.
	 * @return A rectangle if any of the centers had icons drawn. Null otherwise.
	 */
	public Rectangle getBoundingBoxOfIconsForCenters(Collection<Center> centers)
	{
		Rectangle bounds = null;
		for (Center center : centers)
		{
			if (bounds == null)
			{
				bounds = getBoundingBoxOfIconsForCenter(center);
			}
			else
			{
				Rectangle b = getBoundingBoxOfIconsForCenter(center);
				if (b != null)
				{
					bounds.add(b);
				}
			}
		}
		return bounds;
	}
}
