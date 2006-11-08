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
package org.apache.qpid.server.store;

import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Adds some extra methods to the memory message store for testing purposes.
 */
public class TestableMemoryMessageStore extends MemoryMessageStore
{
    public TestableMemoryMessageStore()
    {
        _publishBodyMap = new ConcurrentHashMap<Long, BasicPublishBody>();
        _contentHeaderMap = new ConcurrentHashMap<Long, ContentHeaderBody>();
        _contentBodyMap = new ConcurrentHashMap<Long, List<ContentBody>>();
    }

    public ConcurrentMap<Long, BasicPublishBody> gePublishBodyMap()
    {
        return _publishBodyMap;
    }

    public ConcurrentMap<Long, ContentHeaderBody> getContentHeaderMap()
    {
        return _contentHeaderMap;
    }

    public ConcurrentMap<Long, List<ContentBody>> getContentBodyMap()
    {
        return _contentBodyMap;
    }
}
