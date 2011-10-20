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
package org.apache.qpid.test.utils;

import org.apache.log4j.Logger;

public class SpawnedBrokerHolder implements BrokerHolder
{
    private static final Logger LOGGER = Logger.getLogger(SpawnedBrokerHolder.class);

    private final Process _process;

    public SpawnedBrokerHolder(final Process process)
    {
        if(process == null)
        {
            throw new IllegalArgumentException("Process must not be null");
        }

        _process = process;
    }

    public void shutdown()
    {
        LOGGER.info("Destroying broker process");

        _process.destroy();

        try
        {
            _process.waitFor();
            LOGGER.info("broker exited: " + _process.exitValue());
        }
        catch (InterruptedException e)
        {
            LOGGER.error("Interrupted whilst waiting for process destruction");
            Thread.currentThread().interrupt();
        }
    }
}
