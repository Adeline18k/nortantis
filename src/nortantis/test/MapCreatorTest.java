package nortantis.test;

import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

public class MapCreatorTest
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		// Create the expected images if they don't already exist.
		// Note that this means that if you haven't already created the images, you run these tests before making changes that will need to be tested.
		
		Helper.createFolder(Paths.get("unit test files", "expected maps").toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", "failed maps").toString()));
		
		// For each map in the 'unit test files/map settings' folder, create the associated map in 'unit test files/expected maps'. 
		String[] mapSettingsFileNames = new File(Paths.get("unit test files", "map settings").toString()).list();
		
		for (String settingsFileName : mapSettingsFileNames)
		{
			String expectedMapFilePath = getExpectedMapFilePath(settingsFileName);
			if (!new File(expectedMapFilePath).exists())
			{
				MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString()); 
				MapCreator mapCreator = new MapCreator();
				Logger.println("Creating map '" + expectedMapFilePath + "'");
				BufferedImage map = mapCreator.createMap(settings, null, null);
				ImageHelper.write(map, expectedMapFilePath);
			}
			
		}
	}

	@Test
	public void frayedEdge_regionColors_textureImageBackground()
	{
		generateAndCompare("frayedEdge_regionColors_textureImageBackground.properties");
	}
	
	@Test
	public void noText_NoRegions_SquareBackground_ConcentricWaves()
	{
		generateAndCompare("noText_NoRegions_SquareBackground_ConcentricWaves.properties");
	}
	
	@Test
	public void noText_NoRegions_SquareBackground_ConcentricWaves_WithEdits()
	{
		generateAndCompare("noText_NoRegions_SquareBackground_ConcentricWaves_WithEdits.properties");
	}
	
	@Test
	public void preventCreatingOnlyOneTectonicPlate()
	{
		generateAndCompare("preventCreatingOnlyOneTectonicPlate.properties");
	}
	
	@Test
	public void noText_WithCities_GoldenRatio()
	{
		generateAndCompare("noText_WithCities_GoldenRatio.properties");
	}
	
	@Test
	public void noText_WithCities_GoldenRatio_withEdits()
	{
		generateAndCompare("noText_WithCities_GoldenRatio_withEdits.properties");
	}
	
	@Test
	public void allTypesOfEdits()
	{
		generateAndCompare("allTypesOfEdits.properties");
	}
	
	private static String getExpectedMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "expected maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}
	
	private static String getActualMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "failed maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}
	
	private void generateAndCompare(String settingsFileName)
	{
		BufferedImage expected = ImageHelper.read(getExpectedMapFilePath(settingsFileName));
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating map from '" + settingsPath + "'");
		BufferedImage actual;
		try
		{
			actual = mapCreator.createMap(settings, null, null);
		} 
		catch (IOException e)
		{
			fail("Unable to generate map due to exception: " + e.getMessage());
			return;
		}

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			Helper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, getActualMapFilePath(settingsFileName));
			fail(comparisonErrorMessage);
		}
	}
	
	private String checkIfImagesEqual(BufferedImage image1, BufferedImage image2)
	{
		if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight())
		{
			for (int x = 0; x < image1.getWidth(); x++)
			{
				for (int y = 0; y < image1.getHeight(); y++)
				{
					if (image1.getRGB(x, y) != image2.getRGB(x, y))
					{
						return "Images differ at pixel (" + x + ", " + y + ").";
					}
				}
			}
		} 
		else
		{
			return "Images have differing dimensions.";
		}
		return null;
	}

}