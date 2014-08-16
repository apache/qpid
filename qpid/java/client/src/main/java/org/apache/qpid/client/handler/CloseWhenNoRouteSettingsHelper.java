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
package org.apache.qpid.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.properties.ConnectionStartProperties;

/**
 * Used during connection establishment to optionally set the "close when no route" client property
 */
class CloseWhenNoRouteSettingsHelper
{
    private static final Logger _log = LoggerFactory.getLogger(CloseWhenNoRouteSettingsHelper.class);

    /**
     * @param url the client's connection URL which may contain the option
     *            {@value ConnectionStartProperties#QPID_CLOSE_WHEN_NO_ROUTE}
     * @param serverProperties the properties received from the broker which may contain the option
     *                         {@value ConnectionStartProperties#QPID_CLOSE_WHEN_NO_ROUTE}
     * @param clientProperties the client properties to optionally set the close-when-no-route option on
     */
    public void setClientProperties(FieldTable clientProperties, ConnectionURL url, FieldTable serverProperties)
    {
        boolean brokerSupportsCloseWhenNoRoute =
                    serverProperties != null && serverProperties.containsKey(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE);
        boolean brokerCloseWhenNoRoute = brokerSupportsCloseWhenNoRoute &&
                    Boolean.parseBoolean(serverProperties.getString(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE));

        String closeWhenNoRouteOption = url.getOption(ConnectionURL.OPTIONS_CLOSE_WHEN_NO_ROUTE);
        if(closeWhenNoRouteOption != null)
        {
            if(brokerSupportsCloseWhenNoRoute)
            {
                boolean desiredCloseWhenNoRoute = Boolean.valueOf(closeWhenNoRouteOption);
                if(desiredCloseWhenNoRoute != brokerCloseWhenNoRoute)
                {
                    clientProperties.setBoolean(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE, desiredCloseWhenNoRoute);
                    _log.debug(
                            "Set client property {} to {}",
                            ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE, desiredCloseWhenNoRoute);
                }
                else
                {
                    _log.debug(
                            "Client's desired {} value {} already matches the server's",
                            ConnectionURL.OPTIONS_CLOSE_WHEN_NO_ROUTE, desiredCloseWhenNoRoute);
                }
            }
            else
            {
                _log.warn("The broker being connected to does not support the " + ConnectionURL.OPTIONS_CLOSE_WHEN_NO_ROUTE + " option");
            }
        }
    }
}
