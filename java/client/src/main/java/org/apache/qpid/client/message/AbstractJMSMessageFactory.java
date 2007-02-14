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
package org.apache.qpid.client.message;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;

import java.util.Iterator;
import java.util.List;

import javax.jms.JMSException;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;

public abstract class AbstractJMSMessageFactory implements MessageFactory
{
    private static final Logger _logger = Logger.getLogger(AbstractJMSMessageFactory.class);


    protected abstract AbstractJMSMessage createMessage(long messageNbr,
			ByteBuffer data, MessageHeaders contentHeader) throws AMQException;

	protected AbstractJMSMessage createMessageWithBody(long messageNbr,
			MessageHeaders contentHeader, List contents) throws AMQException {
		
		ByteBuffer data = ByteBuffer.allocate((int)contentHeader.getSize());        
        for (final Iterator it = contents.iterator();it.hasNext();)
        {
            byte[] bytes = (byte[]) it.next();
            data.put(bytes);
        }
        data.flip();
        
        _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data.remaining());

        return createMessage(messageNbr, data, contentHeader);
    }

    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered,
    										MessageHeaders contentHeader,
    										List contents) throws JMSException, AMQException
    {
        final AbstractJMSMessage msg = createMessageWithBody(messageNbr, contentHeader, contents);
        msg.setJMSRedelivered(redelivered);
        return msg;
    }
}
