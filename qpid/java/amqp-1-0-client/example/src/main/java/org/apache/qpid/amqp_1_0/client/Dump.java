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
package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.commons.cli.Options;

public class Dump extends Util
{
    private static final String USAGE_STRING = "dump [options] <address>\n\nOptions:";


    protected Dump(String[] args)
    {
        super(args);
    }

    public static void main(String[] args)
    {
        new Dump(args).run();
    }

    @Override
    protected boolean hasLinkDurableOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasLinkNameOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasResponseQueueOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasSizeOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasBlockOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasStdInOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasTxnOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasModeOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasCountOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void printUsage(Options options)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void run()
    {
        final String queue = getArgs()[0];

        try
        {
            Connection conn = newConnection();

            Session session = conn.createSession();


            Sender s = session.createSender(queue, 10);

            Message message = new Message("dump me");
            message.setDeliveryTag(new Binary("dump".getBytes()));

            s.send(message);

            s.close();
            session.close();
            conn.close();

        } catch (Exception e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
