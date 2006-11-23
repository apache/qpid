/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.client.message;

import org.apache.qpid.framing.*;

import java.util.List;
import java.util.LinkedList;

/**
 * This class contains everything needed to process a JMS message. It assembles the
 * deliver body, the content header and the content body/ies.
 *
 * Note that the actual work of creating a JMS message for the client code's use is done
 * outside of the MINA dispatcher thread in order to minimise the amount of work done in
 * the MINA dispatcher thread.
 *
 */
public class UnprocessedMessage
{
    private long _bytesReceived = 0;

    public BasicDeliverBody deliverBody;
    public BasicReturnBody bounceBody; // TODO: check change (gustavo)
    public int channelId;
    public ContentHeaderBody contentHeader;

    /**
     * List of ContentBody instances. Due to fragmentation you don't know how big this will be in general
     */
    public List bodies = new LinkedList();

    public void receiveBody(ContentBody body) throws UnexpectedBodyReceivedException
    {
        bodies.add(body);
        if (body.payload != null)
        {
            _bytesReceived += body.payload.remaining();
        }
    }

    public boolean isAllBodyDataReceived()
    {
        return _bytesReceived == contentHeader.bodySize;
    }
}
