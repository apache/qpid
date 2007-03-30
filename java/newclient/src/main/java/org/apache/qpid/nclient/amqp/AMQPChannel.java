/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
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