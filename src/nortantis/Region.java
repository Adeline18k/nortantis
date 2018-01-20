package nortantis;

import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import hoten.geom.Point;
import hoten.voronoi.Center;

/**
 * Represents a political region on the map.
 * @author joseph
 *
 */
public class Region
{
	private Set<Center> centers;
	public Set<Center> getCenters() { return Collections.unmodifiableSet(centers); }
	public int id;
	public Color backgroundColor;
	
	public Region()
	{
		this.centers = new HashSet<>();
	}
	
	public void addAll(Collection<Center> toAdd)
	{
		for (Center c : toAdd)
		{
			add(c);
		}
	}
	
	public void removeAll(Collection<Center> toRemove)
	{
		for (Center c : toRemove)
		{
			remove(c);
		}
	}
	
	public void clear()
	{
		for (Center c : centers)
		{
			c.region = null;		
		}
		centers.clear();
	}
	
	public void add(Center c)
	{
		boolean addResult = centers.add(c);
		assert addResult == (c.region != this);
		c.region = this;		
	}
	
	public int size() { return centers.size(); }
	
	public void remove(Center c)
	{
		boolean removeResult = centers.remove(c);
		assert removeResult == (c.region == this);
		if (c.region == this)
		{
			c.region = null;
		}
	}
	
	public boolean contains(Center c)
	{
		return centers.contains(c);
	}
	
	public Point findCentroid()
	{
		return GraphImpl.findCentroid(centers);
	}
}
