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
package org.apache.qpid.tools;

import java.text.DecimalFormat;
import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;

public class PerfBase
{
    TestParams params;
    Connection con;
    Session session;
    Destination dest;
    Destination feedbackDest;
    DecimalFormat df = new DecimalFormat("###.##");

    public PerfBase()
    {
        params = new TestParams();
    }

    public void setUp() throws Exception
    {        

        if (params.getHost().equals("") || params.getPort() == -1)
        {
            con = new AMQConnection(params.getUrl());
        }
        else
        {
            con = new AMQConnection(params.getHost(),params.getPort(),"guest","guest","test","test");
        }
        con.start();
        session = con.createSession(params.isTransacted(),
                                    params.isTransacted()? Session.SESSION_TRANSACTED:params.getAckMode());

        dest = new AMQAnyDestination(params.getAddress());
    }

    public void handleError(Exception e,String msg)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(msg);
        sb.append(" ");
        sb.append(e.getMessage());
        System.err.println(sb.toString());
        e.printStackTrace();
    }
}

