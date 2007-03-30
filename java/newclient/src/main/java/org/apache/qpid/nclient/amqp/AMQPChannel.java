package org.apache.qpid.nclient.amqp;

import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.framing.ChannelOkBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.framing.ChannelResumeBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPChannel
{

	/**
	 * Opens the channel
	 */
	public abstract ChannelOpenOkBody open(ChannelOpenBody channelOpenBody) throws AMQPException;

	/**
	 * Close the channel
	 */
	public abstract ChannelCloseOkBody close(ChannelCloseBody channelCloseBody) throws AMQPException;

	/**
	 * Channel Flow
	 */
	public abstract ChannelFlowOkBody flow(ChannelFlowBody channelFlowBody) throws AMQPException;

	/**
	 * Close the channel
	 */
	public abstract ChannelOkBody resume(ChannelResumeBody channelResumeBody) throws AMQPException;

}