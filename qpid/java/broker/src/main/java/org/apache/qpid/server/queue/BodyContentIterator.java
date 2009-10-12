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
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.Iterator;


class BodyContentIterator implements Iterator<ContentChunk>
{
    private static final Logger _log = Logger.getLogger(BodyContentIterator.class);


    private AMQMessageHandle _messageHandle;
    private int _index = -1;


    public BodyContentIterator(AMQMessageHandle messageHandle)
    {
        _messageHandle = messageHandle;
    }


    public boolean hasNext()
    {
        try
        {
            return _index < (_messageHandle.getBodyCount() - 1);
        }
        catch (AMQException e)
        {
            _log.error("Error getting body count: " + e, e);

            return false;
        }
    }

    public ContentChunk next()
    {
        try
        {
            return _messageHandle.getContentChunk(++_index);
        }
        catch (AMQException e)
        {
            throw new RuntimeException("Error getting content body: " + e, e);
        }
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
