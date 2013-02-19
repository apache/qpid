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
package org.apache.qpid.server.registry;

import org.apache.qpid.server.stats.StatisticsGatherer;

public interface IApplicationRegistry extends StatisticsGatherer
{
    /**
     * Initialise the application registry. All initialisation must be done in this method so that any components
     * that need access to the application registry itself for initialisation are able to use it. Attempting to
     * initialise in the constructor will lead to failures since the registry reference will not have been set.
     */
    void initialise() throws Exception;

    /**
     * Shutdown this Registry
     */
    void close();

}
