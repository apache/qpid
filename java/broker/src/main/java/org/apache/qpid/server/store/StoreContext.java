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
package org.apache.qpid.server.store;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A context that the store can use to associate with a transactional context. For example, it could store
 * some kind of txn id.
 *
 * @author Apache Software Foundation
 */
public class StoreContext
{
    private static final Logger _logger = Logger.getLogger(StoreContext.class);

    private static final String DEFAULT_NAME = "StoreContext";
    private String _name;
    private Object _payload;
    private HashMap<Long, ArrayList<AMQQueue>> _enqueueMap;
    private HashMap<Long, ArrayList<AMQQueue>> _dequeueMap;
    private boolean _async;

    public StoreContext()
    {
        this(DEFAULT_NAME);
    }

    public StoreContext(String name)
    {
        this(name,false);
    }

    /**
     *
     * @param name The name of this Transaction
     * @param asynchrouous Is this Transaction Asynchronous
     */
    public StoreContext(String name, boolean asynchrouous)
    {
        _name = name;
        _async = asynchrouous;
    }

    public StoreContext(boolean asynchronous)
    {
        this(DEFAULT_NAME, asynchronous);
    }

    public Object getPayload()
    {
        return _payload;
    }

    public void setPayload(Object payload)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("public void setPayload(Object payload = " + payload + "): called");
        }
        _payload = payload;
    }

    /**
     * Prints out the transactional context as a string, mainly for debugging purposes.
     *
     * @return The transactional context as a string.
     */
    public String toString()
    {
        return "<_name = " + _name + ", _payload = " + _payload + ">";
    }

    public Map<Long, ArrayList<AMQQueue>> getEnqueueMap()
    {
        return _enqueueMap;
    }

    public Map<Long, ArrayList<AMQQueue>> getDequeueMap()
    {
        return _dequeueMap;
    }

    /**
     * Record the enqueues for processing if we abort
     *
     * @param queues
     * @param messageId
     *
     * @throws AMQException
     */
    public void enqueueMessage(ArrayList<AMQQueue> queues, Long messageId) throws AMQException
    {
        if (inTransaction())
        {
            ArrayList<AMQQueue> enqueues = _enqueueMap.get(messageId);

            if (enqueues == null)
            {
                enqueues = new ArrayList<AMQQueue>();
                _enqueueMap.put(messageId, enqueues);
            }

            for (AMQQueue q : queues)
            {
                if (!enqueues.contains(q))
                {
                    enqueues.add(q);
                }
            }

        }
    }

    /**
     * Record the dequeue for processing on commit
     *
     * @param queue
     * @param messageId
     *
     * @throws AMQException
     */
    public void dequeueMessage(AMQQueue queue, Long messageId) throws AMQException
    {
        if (inTransaction())
        {
            ArrayList<AMQQueue> dequeues = _dequeueMap.get(messageId);

            if (dequeues == null)
            {
                dequeues = new ArrayList<AMQQueue>();
                _dequeueMap.put(messageId, dequeues);
            }

            dequeues.add(queue);
        }
    }

    public void beginTransaction() throws AMQException
    {
        _enqueueMap = new HashMap<Long, ArrayList<AMQQueue>>();
        _dequeueMap = new HashMap<Long, ArrayList<AMQQueue>>();
    }

    public void commitTransaction() throws AMQException
    {
        _dequeueMap.clear();
    }

    public void abortTransaction() throws AMQException
    {
        _enqueueMap.clear();
    }

    public boolean inTransaction()
    {
        return _payload != null;
    }

    public boolean isAsync()
    {
        return _async;
    }
}
