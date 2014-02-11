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

import org.apache.qpid.server.message.ServerMessage;

public class StandardQueueEntryList extends OrderedQueueEntryList<StandardQueueEntry, StandardQueue, StandardQueueEntryList>
{

    private static final HeadCreator<StandardQueueEntry, StandardQueue, StandardQueueEntryList> HEAD_CREATOR = new HeadCreator<StandardQueueEntry, StandardQueue, StandardQueueEntryList>()
    {
        @Override
        public StandardQueueEntry createHead(final StandardQueueEntryList list)
        {
            return new StandardQueueEntry(list);
        }
    };

    public StandardQueueEntryList(final StandardQueue queue)
    {
        super(queue, HEAD_CREATOR);
    }


    protected StandardQueueEntry createQueueEntry(ServerMessage<?> message)
    {
        return new StandardQueueEntry(this, message);
    }

    static class Factory implements QueueEntryListFactory<StandardQueueEntry, StandardQueue, StandardQueueEntryList>
    {

        public StandardQueueEntryList createQueueEntryList(StandardQueue queue)
        {
            return new StandardQueueEntryList(queue);
        }
    }

}
