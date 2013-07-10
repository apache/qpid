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
package org.apache.qpid.server.store.jdbc;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.store.MessageStore;

public class JDBCMessageStoreFactory implements MessageStoreFactory
{

    @Override
    public String getType()
    {
        return JDBCMessageStore.TYPE;
    }

    @Override
    public MessageStore createMessageStore()
    {
        return new JDBCMessageStore();
    }

    @Override
    public Map<String, Object> convertStoreConfiguration(Configuration storeConfiguration)
    {
        Map<String,Object> convertedMap = new HashMap<String,Object>();
        convertedMap.put("jdbcBlobType", storeConfiguration.getString("sqlBlobType"));
        convertedMap.put("jdbcVarbinaryType", storeConfiguration.getString("sqlVarbinaryType"));
        if(storeConfiguration.containsKey("useBytesForBlob"))
        {
            convertedMap.put("jdbcUseBytesForBlob", storeConfiguration.getBoolean("useBytesForBlob"));
        }
        convertedMap.put("jdbcBigIntType", storeConfiguration.getString("sqlBigIntType"));
        convertedMap.put("connectionPool", storeConfiguration.getString("pool.type"));
        convertedMap.put("minConnectionsPerPartition", storeConfiguration.getInteger("pool.minConnectionsPerPartition",
                null));
        convertedMap.put("maxConnectionsPerPartition", storeConfiguration.getInteger("pool.maxConnectionsPerPartition",
                null));
        convertedMap.put("partitionCount", storeConfiguration.getInteger("pool.partitionCount", null));

        return convertedMap;
    }


    @Override
    public void validateAttributes(Map<String, Object> attributes)
    {
        Object connectionURL = attributes.get(JDBCMessageStore.CONNECTION_URL);
        if(!(connectionURL instanceof String))
        {
            Object storePath = attributes.get(VirtualHost.STORE_PATH);
            if(!(storePath instanceof String))
            {
                throw new IllegalArgumentException("Attribute '"+ JDBCMessageStore.CONNECTION_URL
                                                               +"' is required and must be of type String.");

            }
        }
    }

}
