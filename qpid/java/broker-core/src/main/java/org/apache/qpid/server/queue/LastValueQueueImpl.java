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

import java.util.Map;

import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class LastValueQueueImpl extends AbstractQueue<LastValueQueueImpl> implements LastValueQueue<LastValueQueueImpl>
{
    public static final String DEFAULT_LVQ_KEY = "qpid.LVQ_key";


    protected LastValueQueueImpl(VirtualHostImpl virtualHost,
                                 Map<String, Object> attributes)
    {
        super(virtualHost, attributes, entryList(attributes));
    }

    private static LastValueQueueList.Factory entryList(final Map<String, Object> attributes)
    {

        String conflationKey = MapValueConverter.getStringAttribute(LVQ_KEY,
                                                                    attributes,
                                                                    DEFAULT_LVQ_KEY);

        // conflation key can still be null if it was present in the map with a null value
        return new LastValueQueueList.Factory(conflationKey == null ? DEFAULT_LVQ_KEY : conflationKey);
    }

    public String getConflationKey()
    {
        return ((LastValueQueueList)getEntries()).getConflationKey();
    }

    @Override
    public Object getAttribute(final String name)
    {
        if(LVQ_KEY.equals(name))
        {
            if(this instanceof LastValueQueueImpl)
            {
                return getConflationKey();
            }
        }
        return super.getAttribute(name);
    }

    @Override
    public String getLvqKey()
    {
        return getConflationKey();
    }
}
