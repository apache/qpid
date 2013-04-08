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
 * Implementation of QmfEventListener that wraps a Notifier instance. This class populates the WorkItem
 * queue then invokes the Notifier's indication() method to notify clients of available data.
 * <p>
 * This approach allows us to support two separate asynchronous notification APIs without too much effort.
 *
 * @author Fraser Adams
 */
public final class NotifierWrapper implements QmfEventListener
{
    private final Notifier  _notifier;
    private final WorkQueue _workQueue;

    /**
     * Wraps a Notifier and WorkQueue so that they me be triggered by a QmfEventListener onEvent() call.
     * @param notifier the Notifier instance that will be triggered when NotifierWrapper receives a WorkItem.
     * @param workQueue the WorkQueue instance that the WorkItem will be placed on.
     */
    public NotifierWrapper(final Notifier notifier, final WorkQueue workQueue)
    {
        _notifier  = notifier;
        _workQueue = workQueue;
    }

    /**
     * This method adds the WorkItem to the WorkQueue then notifies any clients through the Notifier.indication().
     *
     * @param item the WorkItem to add to the queue
     */
    public void onEvent(final WorkItem item)
    {
        _workQueue.addWorkItem(item);
        _notifier.indication();
    }
}
