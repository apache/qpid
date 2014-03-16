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
 * A QmfEventListener object is used to receive asynchronously delivered WorkItems.
 * <p>
 * This provides an alternative (simpler) API to the official QMF2 WorkQueue API that some (including the Author)
 * may prefer over the official API.
 * <p>
 * The following diagram illustrates the QmfEventListener Event model.
 * <p>
 * Notes
 * <ol>
 *  <li>This is provided as an alternative to the official QMF2 WorkQueue and Notifier Event model.</li>
 *  <li>Agent and Console methods are sufficiently thread safe that it is possible to call them from a callback fired
 *      from the onEvent() method that may have been called from the JMS MessageListener. Internally the synchronous
 *      and asynchronous calls are processed on different JMS Sessions to facilitate this</li>
 * </ol>
 * <p>
 * <img src="doc-files/QmfEventListenerModel.png"/>
 * 
 * @author Fraser Adams
 */
public interface QmfEventListener extends QmfCallback
{
    /**
     * Passes a WorkItem to the listener. 
     *
     * @param item the WorkItem passed to the listener
     */
    public void onEvent(WorkItem item);
}
