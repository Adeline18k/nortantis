package nortantis;

import hoten.geom.Point;
import hoten.voronoi.Center;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TectonicPlate
{
	public PlateType type;
	double growthProbability;
	PolarCoordinate velocity;
	public Set<Center> centers;
	
	// This is needed to give the object a deterministic hash code. If I use the object's address as the hash
	// code, it may change from one run to the next, and so HashSet iterates over the objects in a different
	// order sometimes.
	private int id;
	static int nextID = 0;
	public static void resetIds()
	{
		nextID = 0;
	}
	
	public TectonicPlate(double growthProbability, List<Center> allCenters)
	{
		this.growthProbability = growthProbability;
		this.id = nextID++;
		// Using CenterSet instead of HashSet here saves about 0.4 seconds when generating on graph with 18000 Centers.
		centers = new CenterSet(allCenters);
	}
	
	@Override
	public int hashCode()
	{
		return id;
	}
	
	public Point findCentroid()
	{
		return GraphImpl.findCentroid(centers);
	}

		
}
