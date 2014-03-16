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
 * The QMF2 API has a work-queue Callback approach. All asynchronous events are represented by a WorkItem object.
 * When a QMF event occurs it is translated into a WorkItem object and placed in a FIFO queue. It is left to the
 * application to drain this queue as needed.
 * <p>
 * This new API does require the application to provide a single callback. The callback is used to notify the
 * application that WorkItem object(s) are pending on the work queue. This callback is invoked by QMF when one or
 * more new WorkItem objects are added to the queue. To avoid any potential threading issues, the application is
 * not allowed to call any QMF API from within the context of the callback. The purpose of the callback is to
 * notify the application to schedule itself to drain the work queue at the next available opportunity.
 * <p>
 * For example, a console application may be designed using a select() loop. The application waits in the select()
 * for any of a number of different descriptors to become ready. In this case, the callback could be written to
 * simply make one of the descriptors ready, and then return. This would cause the application to exit the wait state,
 * and start processing pending events.
 * <p>
 * The callback is represented by the Notifier virtual base class. This base class contains a single method. An
 * application derives a custom notification handler from this class, and makes it available to the Console or Agent object.
 * <p>
 * The following diagram illustrates the Notifier and WorkQueue QMF2 API Event model.
 * <p>
 * Notes
 * <ol>
 *  <li>There is an alternative (simpler but not officially QMF2) API based on implementing the QmfEventListener.</li>
 *  <li>BlockingNotifier is not part of QMF2 either but is how most people would probably write a Notifier.</li>
 *  <li>It's generally not necessary to use a Notifier as the Console provides a blocking getNextWorkitem() method.</li>
 * </ol>
 * <p>
 * <img src="doc-files/WorkQueueEventModel.png"/>
 * 
 * @author Fraser Adams
 */
public interface Notifier extends QmfCallback
{
    /**
     * Called when the Console internal work queue becomes non-empty due to the arrival of one or more WorkItems.
     * <p>
     * This method will be called by the internal QMF management thread. It is illegal to invoke any QMF APIs
     * from within this callback. The purpose of this callback is to indicate that the application should schedule
     * itself to process the work items. A common implementation would be to call notify() to unblock a waiting Thread.
     *
     */
    public void indication();
}

