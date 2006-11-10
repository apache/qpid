/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.AMQException;

class MessageTestHelper
{
    private final MessageStore _messageStore = new SkeletonMessageStore();

    MessageTestHelper() throws Exception
    {
        ApplicationRegistry.initialise(new TestApplicationRegistry());
    }

    AMQMessage message() throws AMQException
    {
        return message(false);
    }

    AMQMessage message(boolean immediate) throws AMQException
    {
        BasicPublishBody publish = new BasicPublishBody();
        publish.immediate = immediate;
        return new AMQMessage(_messageStore, publish, new ContentHeaderBody(), null);
    }

}
