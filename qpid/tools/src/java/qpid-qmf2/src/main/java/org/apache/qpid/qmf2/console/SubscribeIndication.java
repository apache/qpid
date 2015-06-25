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
package org.apache.qpid.qmf2.console;

import java.util.List;

/**
 * Holds the result of a subscription data indication from the Agent.
 *
 * @author Fraser Adams
 */
public final class SubscribeIndication
{
    private final String _consoleHandle;
    private final List<QmfConsoleData> _data;

    /**
     * Construct a SubscribeIndication from a consoleHandle and list of QmfConsoleData.
     * @param consoleHandle the handle containing the correlation ID.
     * @param data the list of QmfConsoleData to pass to the Console application.
     */
    public SubscribeIndication(final String consoleHandle, final List<QmfConsoleData> data)
    {
        _consoleHandle = consoleHandle;
        _data = data;
    }

    /**
     * Return the console handle as passed to the createSubscription() call.
     * @return the console handle as passed to the createSubscription() call.
     */
    public String getConsoleHandle()
    {
        return _consoleHandle;
    }

    /**
     * Return a list containing all updated QmfData objects associated with the Subscripion.
     * @return a list containing all updated QmfData objects associated with the Subscripion.
     */
    public List<QmfConsoleData> getData()
    {
        return _data;
    }
}



