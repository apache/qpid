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
package org.apache.qpidity;

import java.util.*;

import org.apache.qpidity.transport.*;
import org.apache.qpidity.transport.network.mina.MinaHandler;


/**
 * ToyClient
 *
 * @author Rafael H. Schloming
 */

class ToyClient extends SessionDelegate
{

    @Override public void messageReject(Session ssn, MessageReject reject)
    {
        for (Range range : reject.getTransfers())
        {
            for (long l = range.getLower(); l <= range.getUpper(); l++)
            {
                System.out.println("message rejected: " +
                                   ssn.getCommand((int) l));
            }
        }
    }

    @Override public void header(Session ssn, Header header)
    {
        for (Struct st : header.getStructs())
        {
            System.out.println("header: " + st);
        }
    }

    @Override public void data(Session ssn, Data data)
    {
        System.out.println("got data: " + data);
    }

    public static final void main(String[] args)
    {
        Connection conn = MinaHandler.connect("0.0.0.0", 5672,
                                              new ConnectionDelegate()
                                              {
                                                  public SessionDelegate getSessionDelegate()
                                                  {
                                                      return new ToyClient();
                                                  }
                                                  public void exception(Throwable t)
                                                  {
                                                      t.printStackTrace();
                                                  }
                                              });
        conn.send(new ConnectionEvent(0, new ProtocolHeader(1, 0, 10)));

        Channel ch = conn.getChannel(0);
        Session ssn = new Session();
        ssn.attach(ch);
        ssn.sessionOpen(1234);

        ssn.queueDeclare("asdf", null, null);
        ssn.sync();

        Map<String,Object> nested = new LinkedHashMap<String,Object>();
        nested.put("list", Arrays.asList("one", "two", "three"));
        Map<String,Object> map = new LinkedHashMap<String,Object>();

        map.put("str", "this is a string");

        map.put("+int", 3);
        map.put("-int", -3);
        map.put("maxint", Integer.MAX_VALUE);
        map.put("minint", Integer.MIN_VALUE);

        map.put("+short", (short) 1);
        map.put("-short", (short) -1);
        map.put("maxshort", (short) Short.MAX_VALUE);
        map.put("minshort", (short) Short.MIN_VALUE);

        map.put("float", (float) 3.3);
        map.put("double", 4.9);
        map.put("char", 'c');

        map.put("table", nested);
        map.put("list", Arrays.asList(1, 2, 3));
        map.put("binary", new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        ssn.messageTransfer("asdf", (short) 0, (short) 1);
        ssn.header(new DeliveryProperties(),
                   new MessageProperties().setApplicationHeaders(map));
        ssn.data("this is the data");
        ssn.endData();

        ssn.messageTransfer("fdsa", (short) 0, (short) 1);
        ssn.data("this should be rejected");
        ssn.endData();
        ssn.sync();

        Future<QueueQueryResult> future = ssn.queueQuery("asdf");
        System.out.println(future.get().getQueue());
        ssn.sync();
        ssn.close();
        conn.close();
    }

}
