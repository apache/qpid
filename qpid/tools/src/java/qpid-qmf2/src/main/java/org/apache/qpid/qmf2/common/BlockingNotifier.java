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
package org.apache.qpid.qmf2.common;

/**
 * Implementation of the Notifier Interface that provides a waitForWorkItem() method which blocks until the
 * Console has called the indication() method indicating that there are WorkItems available.
 * <p>
 * This class isn't part of the QMF2 API however it's almost certainly how most clients would choose to use the
 * Notifier API so it seems useful to provide an implementation.
 * 
 * @author Fraser Adams
 */
public final class BlockingNotifier implements Notifier
{
    private boolean _waiting = true;

    /**
     * This method blocks until the indication() method has been called, this is generally called by the Console
     * when new WorkItems are made available.
     */
    public synchronized void waitForWorkItem()
    {
        while (_waiting)
        {
            try
            {
                wait();
            }
            catch (InterruptedException ie)
            {
                continue;
            }
        }
        _waiting = true;
    }

    /**
     * Called to indicate the availability of WorkItems. This method unblocks waitForWorkItem()
     */
    public synchronized void indication()
    {
        _waiting = false;
        notifyAll();
    }
}

