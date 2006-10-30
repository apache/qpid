package org.apache.qpid.gentools;

public interface VersionConsistencyCheck
{
	public boolean isVersionConsistent(AmqpVersionSet globalVersionSet);
}
