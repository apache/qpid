/*
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
 */

package org.apache.qpid.server.virtualhost.berkeleydb;


import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedContextDefault;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.SizeMonitoringSettings;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public interface BDBVirtualHost<X extends BDBVirtualHost<X>> extends VirtualHostImpl<X, AMQQueue<?>, ExchangeImpl<?>>, org.apache.qpid.server.store.FileBasedSettings, SizeMonitoringSettings
{

    String STORE_PATH = "storePath";

    // Default the JE cache to 5% of total memory, but no less than 10Mb and no more than 200Mb
    @ManagedContextDefault(name="je.maxMemory")
    long DEFAULT_JE_CACHE_SIZE = Math.max(10l*1024l*1024l,
                                          Math.min(200l*1024l*1024l,
                                                   Runtime.getRuntime().maxMemory()/20l));

    @ManagedAttribute(mandatory = true)
    String getStorePath();

    @ManagedAttribute(mandatory = true, defaultValue = "0")
    Long getStoreUnderfullSize();

    @ManagedAttribute(mandatory = true, defaultValue = "0")
    Long getStoreOverfullSize();
}
