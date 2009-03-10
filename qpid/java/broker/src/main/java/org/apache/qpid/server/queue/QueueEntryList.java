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

public interface QueueEntryList
{
    AMQQueue getQueue();

    QueueEntry add(AMQMessage message);

    QueueEntry next(QueueEntry node);

    QueueEntryIterator iterator();

    QueueEntry getHead();

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
     * Immediately update memory usage based on the unload of this queueEntry, potentially start inhaler.
     * @param queueEntry the entry that has been unloaded
     */
    void entryUnloadedUpdateMemory(QueueEntry queueEntry);

    /**
     * Immediately update memory usage based on the load of this queueEntry
     * @param queueEntry the entry that has been loaded
     */
    void entryLoadedUpdateMemory(QueueEntry queueEntry);

    void stop();
}
