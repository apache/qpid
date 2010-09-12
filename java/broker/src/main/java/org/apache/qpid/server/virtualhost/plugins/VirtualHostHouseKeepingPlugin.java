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
package org.apache.qpid.server.virtualhost.plugins;

import org.apache.log4j.Logger;
import org.apache.qpid.server.virtualhost.HouseKeepingTask;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.concurrent.TimeUnit;

public abstract class VirtualHostHouseKeepingPlugin extends HouseKeepingTask implements VirtualHostPlugin
{
    protected final Logger _logger = Logger.getLogger(getClass());

    public VirtualHostHouseKeepingPlugin(VirtualHost vhost)
    {
        super(vhost);
    }


    /**
     * Long value representing the delay between repeats
     *
     * @return
     */
    public abstract long getDelay();

    /**
     * Option to specify what the delay value represents
     *
     * @return
     *
     * @see java.util.concurrent.TimeUnit for valid value.
     */
    public abstract TimeUnit getTimeUnit();
}
