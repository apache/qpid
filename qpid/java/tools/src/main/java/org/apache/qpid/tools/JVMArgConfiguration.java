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

import javax.jms.Connection;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;

public class JVMArgConfiguration implements TestConfiguration
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

    private int random_msg_size_start_from = 1;

    private boolean cacheMessage = false;

    private boolean disableMessageID = false;

    private boolean disableTimestamp = false;

    private boolean durable = false;

    private int transaction_size = 0;

    private int ack_mode = Session.AUTO_ACKNOWLEDGE;

    private int msg_count = 10;

    private int warmup_count = 1;

    private boolean random_msg_size = false;

    private String msgType = "bytes";

    private boolean printStdDev = false;

    private int sendRate = 0;

    private boolean externalController = false;

    private boolean useUniqueDest = false; // useful when using multiple connections.

    private int ackFrequency = 100;

    private DecimalFormat df = new DecimalFormat("###.##");

    private int reportEvery = 0;

    private boolean isReportTotal = false;

    private boolean isReportHeader = true;

    private boolean isReportLatency = false;

    private int sendEOS = 0;

    private int connectionCount = 1;

    private int rollbackFrequency = 0;

    private boolean printHeaders;

    public JVMArgConfiguration()
    {

        url = System.getProperty("url",url);
        host = System.getProperty("host","");
        port = Integer.getInteger("port", -1);
        address = System.getProperty("address",address);

        msg_size  = Integer.getInteger("msg-size", 1024);
        cacheMessage = Boolean.getBoolean("cache-msg");
        disableMessageID = Boolean.getBoolean("disable-message-id");
        disableTimestamp = Boolean.getBoolean("disable-timestamp");
        durable = Boolean.getBoolean("durable");
        transaction_size = Integer.getInteger("tx",1000);
        ack_mode = Integer.getInteger("ack-mode",Session.AUTO_ACKNOWLEDGE);
        msg_count = Integer.getInteger("msg-count",msg_count);
        warmup_count = Integer.getInteger("warmup-count",warmup_count);
        random_msg_size = Boolean.getBoolean("random-msg-size");
        msgType = System.getProperty("msg-type","bytes");
        printStdDev = Boolean.getBoolean("print-std-dev");
        sendRate = Integer.getInteger("rate",0);
        externalController = Boolean.getBoolean("ext-controller");
        useUniqueDest = Boolean.getBoolean("use-unique-dest");
        random_msg_size_start_from = Integer.getInteger("random-msg-size-start-from", 1);
        reportEvery = Integer.getInteger("report-every");
        isReportTotal = Boolean.getBoolean("report-total");
        isReportHeader = (System.getProperty("report-header") == null) ? true : Boolean.getBoolean("report-header");
        isReportLatency = Boolean.getBoolean("report-latency");
        sendEOS = Integer.getInteger("send-eos");
        connectionCount = Integer.getInteger("con_count",1);
        ackFrequency = Integer.getInteger("ack-frequency");
        rollbackFrequency = Integer.getInteger("rollback-frequency");
        printHeaders = Boolean.getBoolean("print-headers");
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getUrl()
     */
    @Override
    public String getUrl()
    {
        return url;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getHost()
     */
    @Override
    public String getHost()
    {
        return host;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getPort()
     */
    @Override
    public int getPort()
    {
        return port;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getAddress()
     */
    @Override
    public String getAddress()
    {
        return address;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getAckMode()
     */
    @Override
    public int getAckMode()
    {
        return ack_mode;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getMsgCount()
     */
    @Override
    public int getMsgCount()
    {
        return msg_count;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getMsgSize()
     */
    @Override
    public int getMsgSize()
    {
        return msg_size;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getRandomMsgSizeStartFrom()
     */
    @Override
    public int getRandomMsgSizeStartFrom()
    {
        return random_msg_size_start_from;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isDurable()
     */
    @Override
    public boolean isDurable()
    {
        return durable;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isTransacted()
     */
    @Override
    public boolean isTransacted()
    {
        return transaction_size > 0;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getTransactionSize()
     */
    @Override
    public int getTransactionSize()
    {
        return transaction_size;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getWarmupCount()
     */
    @Override
    public int getWarmupCount()
    {
        return warmup_count;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isCacheMessage()
     */
    @Override
    public boolean isCacheMessage()
    {
        return cacheMessage;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isDisableMessageID()
     */
    @Override
    public boolean isDisableMessageID()
    {
        return disableMessageID;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isDisableTimestamp()
     */
    @Override
    public boolean isDisableTimestamp()
    {
        return disableTimestamp;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isRandomMsgSize()
     */
    @Override
    public boolean isRandomMsgSize()
    {
        return random_msg_size;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getMessageType()
     */
    @Override
    public String getMessageType()
    {
        return msgType;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isPrintStdDev()
     */
    @Override
    public boolean isPrintStdDev()
    {
        return printStdDev;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getSendRate()
     */
    @Override
    public int getSendRate()
    {
        return sendRate;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isExternalController()
     */
    @Override
    public boolean isExternalController()
    {
        return externalController;
    }

    public void setAddress(String addr)
    {
        address = addr;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#isUseUniqueDests()
     */
    @Override
    public boolean isUseUniqueDests()
    {
        return useUniqueDest;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getAckFrequency()
     */
    @Override
    public int getAckFrequency()
    {
        return ackFrequency;
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#createConnection()
     */
    @Override
    public Connection createConnection() throws Exception
    {
        if (getHost().equals("") || getPort() == -1)
        {
            return new AMQConnection(getUrl());
        }
        else
        {
            return new AMQConnection(getHost(),getPort(),"guest","guest","test","test");
        }
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.TestConfiguration#getDecimalFormat()
     */
    @Override
    public DecimalFormat getDecimalFormat()
    {
        return df;
    }

    @Override
    public int reportEvery()
    {
        return reportEvery;
    }

    @Override
    public boolean isReportTotal()
    {
        return isReportTotal;
    }

    @Override
    public boolean isReportHeader()
    {
        return isReportHeader;
    }

    @Override
    public boolean isReportLatency()
    {
        return isReportLatency;
    }

    @Override
    public int getSendEOS()
    {
        return sendEOS;
    }

    @Override
    public int getConnectionCount()
    {
        return connectionCount;
    }

    @Override
    public int getRollbackFrequency()
    {
        return rollbackFrequency;
    }

    @Override
    public boolean isPrintHeaders()
    {
        return printHeaders;
    }
}
