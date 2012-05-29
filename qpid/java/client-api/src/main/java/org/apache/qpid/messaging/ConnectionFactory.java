/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acts as the factory that loads a particular API implementation.
 */
public abstract class ConnectionFactory
{
    private final static ConnectionFactory _instance;
    private static final Logger _logger = LoggerFactory.getLogger(ConnectionFactory.class);

    static
    {
        String className = System.getProperty("qpid.connection-factory",
                "org.apache.qpid.messaging.cpp.CppConnectionFactory"); // will default to java
        try
        {
            _instance = (ConnectionFactory) Class.forName(className).newInstance();
        }
        catch (Exception e)
        {
            _logger.error("Error loading connection factory class",e);
            throw new Error("Error loading connection factory class",e);
        }
    }

    public static ConnectionFactory get()
    {
        return _instance;
    }

    public abstract Connection createConnection(String url);
}
