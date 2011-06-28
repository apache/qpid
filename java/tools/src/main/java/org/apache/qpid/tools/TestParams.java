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

import javax.jms.Session;

public class TestParams
{
    /*
     * By default the connection URL is used.
     * This allows a user to easily specify a fully fledged URL any given property.
     * Ex. SSL parameters
     *
     * By providing a host & port allows a user to simply override the URL.
     * This allows to create multiple clients in test scripts easily,
     * without having to deal with the long URL format.
     */
    private String url = "amqp://guest:guest@clientid/testpath?brokerlist='tcp://localhost:5672'";

    private String host = "";

    private int port = -1;

    private String address = "queue; {create : always}";

    private int msg_size = 1024;

    private int msg_type = 1;   // not used yet

    private boolean cacheMessage = false;

    private boolean disableMessageID = false;

    private boolean disableTimestamp = false;

    private boolean durable = false;

    private boolean transacted = false;

    private int transaction_size = 1000;

    private int ack_mode = Session.AUTO_ACKNOWLEDGE;

    private int msg_count = 10;

    private int warmup_count = 1;

    private boolean random_msg_size = false;

    private String msgType = "bytes";

    public TestParams()
    {

        url = System.getProperty("url",url);
        host = System.getProperty("host","");
        port = Integer.getInteger("port", -1);
        address = System.getProperty("address",address);

        msg_size  = Integer.getInteger("msg_size", 1024);
        msg_type = Integer.getInteger("msg_type",1);
        cacheMessage = Boolean.getBoolean("cache_msg");
        disableMessageID = Boolean.getBoolean("disableMessageID");
        disableTimestamp = Boolean.getBoolean("disableTimestamp");
        durable = Boolean.getBoolean("durable");
        transacted = Boolean.getBoolean("transacted");
        transaction_size = Integer.getInteger("trans_size",1000);
        ack_mode = Integer.getInteger("ack_mode",Session.AUTO_ACKNOWLEDGE);
        msg_count = Integer.getInteger("msg_count",msg_count);
        warmup_count = Integer.getInteger("warmup_count",warmup_count);
        random_msg_size = Boolean.getBoolean("random_msg_size");
        msgType = System.getProperty("msg_type","bytes");
    }

    public String getUrl()
    {
        return url;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public String getAddress()
    {
        return address;
    }

    public int getAckMode()
    {
        return ack_mode;
    }

    public int getMsgCount()
    {
        return msg_count;
    }

    public int getMsgSize()
    {
        return msg_size;
    }

    public int getMsgType()
    {
        return msg_type;
    }

    public boolean isDurable()
    {
        return durable;
    }

    public boolean isTransacted()
    {
        return transacted;
    }

    public int getTransactionSize()
    {
        return transaction_size;
    }

    public int getWarmupCount()
    {
        return warmup_count;
    }

    public boolean isCacheMessage()
    {
        return cacheMessage;
    }

    public boolean isDisableMessageID()
    {
        return disableMessageID;
    }

    public boolean isDisableTimestamp()
    {
        return disableTimestamp;
    }

    public boolean isRandomMsgSize()
    {
        return random_msg_size;
    }

    public String getMessageType()
    {
        return msgType;
    }
}
