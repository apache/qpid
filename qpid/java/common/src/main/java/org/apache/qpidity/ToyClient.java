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
        ssn.processed(reject);
    }

    public void headers(Session ssn, Struct ... headers)
    {
        for (Struct hdr : headers)
        {
            System.out.println("header: " + hdr);
        }
    }

    public void data(Session ssn, Frame frame)
    {
        System.out.println("got data: " + frame);
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
                                              });
        conn.getOutputHandler().handle(conn.getHeader().toByteBuffer());

        Channel ch = conn.getChannel(0);
        Session ssn = new Session();
        ssn.attach(ch);
        ssn.sessionOpen(1234);

        ssn.queueDeclare("asdf", null, null);
        ssn.sync();

        ssn.messageTransfer("asdf", (short) 0, (short) 1);
        ssn.headers(new DeliveryProperties(),
                    new MessageProperties());
        ssn.data("this is the data");
        ssn.end();

        ssn.messageTransfer("fdsa", (short) 0, (short) 1);
        ssn.data("this should be rejected");
        ssn.end();
    }

}
