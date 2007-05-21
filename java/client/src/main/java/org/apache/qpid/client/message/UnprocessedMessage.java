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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.framing.BasicReturnBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;

/**
 * This class contains everything needed to process a JMS message. It assembles the deliver body, the content header and
 * the content body/ies.
 *
 * Note that the actual work of creating a JMS message for the client code's use is done outside of the MINA dispatcher
 * thread in order to minimise the amount of work done in the MINA dispatcher thread.
 */
public class UnprocessedMessage
{
    private long _bytesReceived = 0;

    private final BasicDeliverBody _deliverBody;
    private final BasicReturnBody _bounceBody; // TODO: check change (gustavo)
    private final int _channelId;
    private ContentHeaderBody _contentHeader;

    /** List of ContentBody instances. Due to fragmentation you don't know how big this will be in general */
    private List<ContentBody> _bodies;

    public UnprocessedMessage(int channelId, BasicDeliverBody deliverBody)
    {
        _deliverBody = deliverBody;
        _channelId = channelId;
        _bounceBody = null;
    }


    public UnprocessedMessage(int channelId, BasicReturnBody bounceBody)
    {
        _deliverBody = null;
        _channelId = channelId;
        _bounceBody = bounceBody;
    }

    public void receiveBody(ContentBody body) //throws UnexpectedBodyReceivedException
    {

        if (body.payload != null)
        {
            final long payloadSize = body.payload.remaining();

            if (_bodies == null)
            {
                if (payloadSize == getContentHeader().bodySize)
                {
                    _bodies = Collections.singletonList(body);
                }
                else
                {
                    _bodies = new ArrayList<ContentBody>();
                    _bodies.add(body);
                }

            }
            else
            {
                _bodies.add(body);
            }
            _bytesReceived += payloadSize;
        }
    }

    public boolean isAllBodyDataReceived()
    {
        return _bytesReceived == getContentHeader().bodySize;
    }

    public BasicDeliverBody getDeliverBody()
    {
        return _deliverBody;
    }

    public BasicReturnBody getBounceBody()
    {
        return _bounceBody;
    }


    public int getChannelId()
    {
        return _channelId;
    }


    public ContentHeaderBody getContentHeader()
    {
        return _contentHeader;
    }

    public void setContentHeader(ContentHeaderBody contentHeader)
    {
        this._contentHeader = contentHeader;
    }

    public List<ContentBody> getBodies()
    {
        return _bodies;
    }

}
