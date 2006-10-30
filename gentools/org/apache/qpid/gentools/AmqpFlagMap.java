package org.apache.qpid.gentools;

import java.util.TreeMap;

@SuppressWarnings("serial")
public class AmqpFlagMap extends TreeMap<Boolean, AmqpVersionSet> implements VersionConsistencyCheck
{
	public boolean isSet()
	{
		return containsKey(true);
	}
	
	public String toString()
	{
		AmqpVersionSet versionSet = get(true);
		if (versionSet != null)
			return versionSet.toString();
		return "";
	}
	
	public boolean isVersionConsistent(AmqpVersionSet globalVersionSet)
	{
		if (size() != 1)
			return false;
		return get(firstKey()).equals(globalVersionSet);
	}
}
