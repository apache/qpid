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
package org.apache.qpid.client;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;

/** Provides support for orderly shutdown of an object. */
public abstract class Closeable
{
    /**
     * We use an atomic boolean so that we do not have to synchronized access to this flag. Synchronizing access to this
     * flag would mean have a synchronized block in every method.
     */
    protected final AtomicBoolean _closed = new AtomicBoolean(false);

    protected void checkNotClosed() throws JMSException
    {
        if (isClosed())
        {
            throw new IllegalStateException("Object " + toString() + " has been closed");
        }
    }

    public boolean isClosed()
    {
//        synchronized (_closed)
        {
            return _closed.get();
        }
    }

    public abstract void close() throws JMSException;
}
