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
package org.apache.qpid.server.protocol;

import org.apache.qpid.configuration.Configured;
import org.apache.qpid.server.registry.ApplicationRegistry;

public class HeartbeatConfig
{
    @Configured(path = "heartbeat.delay", defaultValue = "5")    
    public int delay = 5;//in secs
    @Configured(path = "heartbeat.timeoutFactor", defaultValue = "2.0")
    public double timeoutFactor = 2;

    public double getTimeoutFactor()
    {
        return timeoutFactor;
    }

    public void setTimeoutFactor(double timeoutFactor)
    {
        this.timeoutFactor = timeoutFactor;
    }

    public int getDelay()
    {
        return delay;
    }

    public void setDelay(int delay)
    {
        this.delay = delay;
    }

    int getTimeout(int writeDelay)
    {
        return (int) (timeoutFactor * writeDelay);
    }

    public static HeartbeatConfig getInstance()
    {
        return ApplicationRegistry.getInstance().getConfiguredObject(HeartbeatConfig.class);
    }

    public String toString()
    {
        return "HeartBeatConfig{delay = " + delay + " timeoutFactor = " + timeoutFactor + "}";
    }
}
