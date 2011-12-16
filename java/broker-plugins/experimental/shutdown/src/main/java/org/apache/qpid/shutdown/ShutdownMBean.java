/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.qpid.shutdown;

import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;

/**
 * Shutdown plugin JMX MBean interface.
 * 
 * Shuts the Qpid broker down via JMX.
 */
public interface ShutdownMBean
{
    static final String TYPE = "Shutdown";

    /**
     * Broker will be shut down immediately.
     */
    @MBeanOperation(name="shutdown", description="Shut down immediately")
    public void shutdown();

    /**
     * Broker will be shutdown after the specified delay
     * 
     * @param delay the number of ms to wait
     */
    @MBeanOperation(name="shutdown", description="Shutdown after the specified delay (ms)")
    public void shutdown(@MBeanOperationParameter(name="when", description="delay (ms)")long delay);

    /**
     * Broker will be shutdown at the specified date and time.
     * 
     * @param when the date and time to shutdown
     */
    @MBeanOperation(name="shutdownAt", description="Shutdown at the specified date and time (yyyy/MM/dd HH:mm:ss)")
    public void shutdownAt(@MBeanOperationParameter(name="when", description="shutdown date/time (yyyy/MM/dd HH:mm:ss)")String when);
}
