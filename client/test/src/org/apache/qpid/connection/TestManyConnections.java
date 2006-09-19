/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.connection;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.testutil.VmOrRemoteTestCase;
import org.apache.log4j.Logger;
import org.junit.Test;

public class TestManyConnections extends VmOrRemoteTestCase
{
    private static final Logger _log = Logger.getLogger(TestManyConnections.class);

    private AMQConnection[] _connections;

    private void createConnection(int index, String brokerHosts, String clientID, String username, String password,
                                  String vpath) throws AMQException, URLSyntaxException
    {
        _connections[index] = new AMQConnection(brokerHosts, username, password,
                                                clientID, vpath);
    }

    private void createConnections(int count) throws AMQException, URLSyntaxException
    {
        _connections = new AMQConnection[count];
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++)
        {
            createConnection(i, "tcp://foo", "myClient" + i, "guest", "guest", "/test");
        }
        long endTime = System.currentTimeMillis();
        _log.info("Time to create " + count + " connections: " + (endTime - startTime) +
                  "ms");
    }

    @Test
    public void create10Connections() throws AMQException, URLSyntaxException
    {
        createConnections(10);
    }

    @Test
    public void create50Connections() throws AMQException, URLSyntaxException
    {
        createConnections(50);
    }

    @Test
    public void create100Connections() throws AMQException, URLSyntaxException
    {
        createConnections(100);
    }

    @Test
    public void create250Connections() throws AMQException, URLSyntaxException
    {
        createConnections(250);
    }

    @Test
    public void create500Connections() throws AMQException, URLSyntaxException
    {
        createConnections(500);
    }

    @Test
    public void create1000Connections() throws AMQException, URLSyntaxException
    {
        createConnections(1000);
    }

    @Test
    public void create5000Connections() throws AMQException, URLSyntaxException
    {
        createConnections(5000);
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TestManyConnections.class);
    }
}
