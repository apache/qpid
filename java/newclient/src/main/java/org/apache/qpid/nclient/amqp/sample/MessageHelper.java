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
package org.apache.qpid.nclient.amqp.sample;

import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.amqp.AMQPMessageCallBack;
import org.apache.qpid.nclient.core.AMQPException;

public class MessageHelper implements AMQPMessageCallBack
{

    public void append(MessageAppendBody messageAppendBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void checkpoint(MessageCheckpointBody messageCheckpointBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void close(MessageCloseBody messageCloseBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void open(MessageOpenBody messageOpenBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void recover(MessageRecoverBody messageRecoverBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void resume(MessageResumeBody messageResumeBody, long correlationId) throws AMQPException
    {
	// TODO Auto-generated method stub

    }

    public void transfer(MessageTransferBody messageTransferBody, long correlationId) throws AMQPException
    {
	System.out.println("The Broker has sent a message" + messageTransferBody.toString());
    }

}
