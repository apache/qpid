package org.apache.qpid.gentools;

import java.util.TreeMap;

@SuppressWarnings("serial")
public class AmqpDomainVersionMap extends TreeMap<String, AmqpVersionSet> implements VersionConsistencyCheck
{	
	public boolean isVersionConsistent(AmqpVersionSet globalVersionSet)
	{
		if (size() != 1)
			return false;
		return get(firstKey()).equals(globalVersionSet);
	}
}
