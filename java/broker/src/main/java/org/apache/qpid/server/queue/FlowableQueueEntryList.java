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

import java.util.concurrent.atomic.AtomicLong;

public interface FlowableQueueEntryList extends QueueEntryList
{
    void setFlowed(boolean flowed);

    boolean isFlowed();

    int size();

    long dataSize();

    long memoryUsed();

    void setMemoryUsageMaximum(long maximumMemoryUsage);

    long getMemoryUsageMaximum();

    void setMemoryUsageMinimum(long minimumMemoryUsage);

    long getMemoryUsageMinimum();

    /**
     * Immediately unload Entry
     * @param queueEntry the entry to unload
     */
    public void unloadEntry(QueueEntry queueEntry);

    /**
     * Immediately load Entry
     * @param queueEntry the entry to load
     */
    public void loadEntry(QueueEntry queueEntry);

    void stop();
}
