package org.apache.qpid.gentools;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

@SuppressWarnings("serial")
public class AmqpOrdinalFieldMap extends TreeMap<Integer, String[]> implements Comparable
{
	public int compareTo(Object obj)
	{
		AmqpOrdinalFieldMap o = (AmqpOrdinalFieldMap)obj;
		Set<Integer> thisKeySet = keySet();
		Set<Integer> oKeySet = o.keySet();
		if (!thisKeySet.equals(oKeySet)) // Not equal, but why?
		{
			// Size difference
			int sizeDiff = thisKeySet.size() - oKeySet.size(); // -ve if this < other
			if (sizeDiff != 0)
				return sizeDiff;
			// Conetent difference
			Iterator<Integer> itr = thisKeySet.iterator();
			Iterator<Integer> oItr = oKeySet.iterator();
			while (itr.hasNext() && oItr.hasNext())
			{
				int diff = itr.next() - oItr.next(); // -ve if this < other
				if (diff != 0)
					return diff;
			}
			// We should never get here...
			System.err.println("AmqpOrdinalFieldMap.compareTo(): " +
				"WARNING - unable to find cause of keySet difference.");
		}
		// Keys are equal, now check the String[]s
		Iterator<Integer> itr = thisKeySet.iterator();
		Iterator<Integer> oItr = oKeySet.iterator();
		while (itr.hasNext() && oItr.hasNext())
		{
			String[] thisPair = get(itr.next());
			String[] oPair = o.get(oItr.next());
			// Size difference
			int sizeDiff = thisPair.length - oPair.length; // -ve if this < other
			if (sizeDiff != 0)
				return sizeDiff;
			// Conetent difference
			for (int i=0; i<thisPair.length; i++)
			{
				int diff = thisPair[i].compareTo(oPair[i]);
				if (diff != 0)
					return diff;
			}
		}
		return 0;
	}
}
