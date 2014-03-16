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
package org.apache.qpid.qmf2.test;

//import javax.jms.Connection;

// Misc Imports
import java.io.*;

// QMF2 Imports
import org.apache.qpid.qmf2.util.ConnectionHelper;

/**
 * A class used to test the ConnectionHelper utility, which provides support for a range of AMQP URL formats
 * and a basic wrapper for Connection creation.
 *
 * @author Fraser Adams
 */

public final class URLTest
{
    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        System.out.println("Running URLTest, this tests ConnectionHelper.createConnectionURL() for various URL formats.");
        System.out.println("It doesn't actually create a connection, it just displays the resulting ConnectionURL");

        String url;
        url = ConnectionHelper.createConnectionURL("localhost");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("localhost:5672", "{username: foo, password: bar, reconnect: true, reconnect_timeout: 5, reconnect_limit: 5000, tcp-nodelay: true, heartbeat: 10, protocol: ssl}");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("guest/guest@localhost:5672", "{reconnect: true, tcp-nodelay: true}");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp:localhost:5672, tcp:localhost:5673", "{reconnect: true, tcp-nodelay: true, failover: roundrobin, cyclecount: 20}");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://foo:bar@host1:1234/vhost?clientid=baz");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://localhost");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://vm:foo:1234,tcp:foo:5678/");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://host1,host2,host3/?retry=2");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://host1,host2?retry=2,host3");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://grok:hostname?flavour=strawberry;frobnication=on/vhost");
        System.out.println(url);

        url = ConnectionHelper.createConnectionURL("amqp://host?ssl=true");
        System.out.println(url);
    }
}
