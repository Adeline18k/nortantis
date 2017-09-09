package nortantis;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import nortantis.nlp.CharacterNGram;
import util.Range;

public class NameGenerator
{
	private CharacterNGram nGram;
	double averageWordLength = 0;
	double maxWordLengthComparedToAverage;
	
	/**
	 * @param maxWordLengthComparedToAverage Any name generated which contains a word (separated by spaces) which is longer than
	 * maxWordLengthComparedToAverage * averageWordLength will be rejected.
	 */
	public NameGenerator(Random r, List<String> placeNames, double maxWordLengthComparedToAverage)
	{
		this.maxWordLengthComparedToAverage = maxWordLengthComparedToAverage;
		nGram = new CharacterNGram(r, 3);
		
		// Find the average word length.
		int sum = 0;
		int count = 0;
		for (String name : placeNames)
		{
			sum += name.length();
			count ++;
		}
		averageWordLength = ((double)sum)/count;
		
		// Convert all words to lower case.
		for (int i : new Range(placeNames.size()))
		{
			placeNames.set(i, placeNames.get(i).toLowerCase());
		}
		
		nGram.addData(placeNames);

		// Test to see how much the generator gives names in the training data.
//		int countSeen = 0;
//		for (@SuppressWarnings("unused") int i : new Range(1000))
//		{
//			String name = nGram.generateName();
//			if (placeNames.contains(name))
//				countSeen++;
//		}
//		Logger.println("Percent of generated names seen in training data (out of 1000 generated names): " + countSeen/10.0);
	}

	public String generateName() throws NotEnoughNamesException
	{		
		String name = null;
		String longestWord = null;
		do
		{
			name = nGram.generateNameNotCorpora();
			longestWord = Collections.max(Arrays.asList(name.split(" ")), new Comparator<String>()
			{
				public int compare(String s1, String s2)
				{
					return Integer.compare(s1.length(), s2.length());
				}
			});
		}
		while (longestWord.length() > averageWordLength * maxWordLengthComparedToAverage);
		// Capitalize first letter of generated names, including for multi-word names.
		name = capitalizeAllFirstLetter(name);
	
		return name;
	}
	
	private String capitalizeAllFirstLetter(String str)
	{
		char[] chars = str.toCharArray();
		for (int i : new Range(0, chars.length))
		{
			if ((i == 0 || chars[i - 1] == ' ') && Character.isLowerCase(chars[i]))
			{
				chars[i] = Character.toUpperCase(str.charAt(i));
			}
		}
		return  String.valueOf(chars);
	}
}
