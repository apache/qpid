package org.apache.qpid.management.domain.handler.impl;

import org.apache.qpid.management.domain.handler.base.BaseMessageHandler;
import org.apache.qpid.transport.codec.Decoder;

/**
 * This is the handler responsible for processing the heartbeat indication response messages.
 * At the moment it simply updates the last refresh update timestamp of the domain model.
 * 
 * @author Andrea Gazzarini.
 */
public class HeartBeatIndicationMessageHandler extends BaseMessageHandler 
{
	public void process(Decoder decoder, int sequenceNumber) 
	{
		_domainModel.updateLastRefreshDate();
	}
}
