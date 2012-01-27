package org.apache.qpid.nclient.util;
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


import org.apache.qpid.nclient.MessagePartListener;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageTransfer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is a simple message assembler.
 * Will call onMessage method of the adaptee
 * when all message data is read.
 *
 * This is a good convinience utility for handling
 * small messages
 */
public class MessagePartListenerAdapter implements MessagePartListener
{
	private MessageListener _adaptee;
    private ByteBufferMessage _currentMsg;

	public MessagePartListenerAdapter(MessageListener listener)
	{
		_adaptee = listener;
    }

    public void messageTransfer(MessageTransfer xfr)
    {
        _currentMsg = new ByteBufferMessage(xfr.getId());

        Header header = xfr.getHeader();
        _currentMsg.setDeliveryProperties(header.getDeliveryProperties());
        _currentMsg.setMessageProperties(header.getMessageProperties());


        ByteBuffer body = xfr.getBody();
        if (body == null)
        {
            body = ByteBuffer.allocate(0);
        }


        try
        {
            _currentMsg.appendData(body);
        }
        catch(IOException e)
        {
            // A chance for IO exception
            // doesn't occur as we are using
            // a ByteBuffer
        }

        _adaptee.onMessage(_currentMsg);
    }

}
