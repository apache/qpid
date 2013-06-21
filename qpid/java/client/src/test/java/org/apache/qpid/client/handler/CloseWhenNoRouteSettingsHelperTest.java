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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.test.utils.QpidTestCase;

public class CloseWhenNoRouteSettingsHelperTest extends QpidTestCase
{
    private static final String FALSE_STR = Boolean.toString(false);
    private static final String TRUE_STR = Boolean.toString(true);

    private final CloseWhenNoRouteSettingsHelper _closeWhenNoRouteSettingsHelper = new CloseWhenNoRouteSettingsHelper();

    public void testCloseWhenNoRouteNegotiation()
    {
        test("Nothing should be set if option not in URL",
                null,
                true,
                null);
        test("Client should disable broker's enabled option",
                FALSE_STR,
                true,
                false);
        test("Client should be able to disable broker's enabled option",
                TRUE_STR,
                false,
                true);
        test("Client should not enable option if unsupported by broker",
                TRUE_STR,
                null,
                null);
        test("Malformed client option should evaluate to false",
                "malformed boolean",
                true,
                false);
    }

    private void test(String message, String urlOption, Boolean serverOption, Boolean expectedClientProperty)
    {
        ConnectionURL url = mock(ConnectionURL.class);
        when(url.getOption(ConnectionURL.OPTIONS_CLOSE_WHEN_NO_ROUTE)).thenReturn(urlOption);

        FieldTable serverProperties = new FieldTable();
        if(serverOption != null)
        {
            serverProperties.setBoolean(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE, serverOption);
        }

        FieldTable clientProperties = new FieldTable();

        _closeWhenNoRouteSettingsHelper.setClientProperties(clientProperties, url, serverProperties);

        assertEquals(message, expectedClientProperty, clientProperties.getBoolean(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE));
    }
}
