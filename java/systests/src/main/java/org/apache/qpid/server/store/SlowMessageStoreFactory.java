package org.apache.qpid.server.store;/*
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.plugin.MessageStoreFactory;

public class SlowMessageStoreFactory implements MessageStoreFactory
{
    @Override
    public String getType()
    {
        return "SLOW";
    }

    @Override
    public MessageStore createMessageStore()
    {
        return new SlowMessageStore();
    }

    @Override
    public Map<String, Object> convertStoreConfiguration(Configuration storeConfiguration)
    {
        Map<String, Object> convertedMap = new HashMap<String, Object>();
        Configuration delaysConfig = storeConfiguration.subset("delays");

        @SuppressWarnings("unchecked")
        Iterator<String> delays = delaysConfig.getKeys();

        Map<String,Long> delaysMap = new HashMap<String, Long>();

        while (delays.hasNext())
        {
            String key = delays.next();

            if (key.endsWith("pre"))
            {
                delaysMap.put("pre"+key.substring(0, key.length() - 4), delaysConfig.getLong(key));
            }
            else if (key.endsWith("post"))
            {
                delaysMap.put("post"+key.substring(0, key.length() - 5), delaysConfig.getLong(key));
            }
        }

        if(!delaysMap.isEmpty())
        {
            convertedMap.put("slowMessageStoreDelays",delaysMap);
        }


        convertedMap.put("realStore", storeConfiguration.getString("realStore", null));


        return convertedMap;
    }
}
