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

public interface TestConfiguration
{
    enum MessageType {
        BYTES, TEXT, MAP, OBJECT;

        public static MessageType getType(String s) throws Exception
        {
            if ("text".equalsIgnoreCase(s))
            {
                return TEXT;
            }
            else if ("bytes".equalsIgnoreCase(s))
            {
                return BYTES;
            }
            /*else if ("map".equalsIgnoreCase(s))
            {
                return MAP;
            }
            else if ("object".equalsIgnoreCase(s))
            {
                return OBJECT;
            }*/
            else
            {
                throw new Exception("Unsupported message type");
            }
        }
    };

    public final static String TIMESTAMP = "ts";

    public final static String EOS = "eos";

    public final static String SEQUENCE_NUMBER = "sn";

    public String getUrl();

    public String getHost();

    public int getPort();

    public String getAddress();

    public int getAckMode();

    public int getMsgCount();

    public int getMsgSize();

    public int getRandomMsgSizeStartFrom();

    public boolean isDurable();

    public boolean isTransacted();

    public int getTransactionSize();

    public int getWarmupCount();

    public boolean isCacheMessage();

    public boolean isDisableMessageID();

    public boolean isDisableTimestamp();

    public boolean isRandomMsgSize();

    public String getMessageType();

    public boolean isPrintStdDev();

    public int getSendRate();

    public boolean isExternalController();

    public boolean isUseUniqueDests();

    public int getAckFrequency();

    public Connection createConnection() throws Exception;

    public DecimalFormat getDecimalFormat();

    public int reportEvery();

    public boolean isReportTotal();

    public boolean isReportHeader();

    public boolean isReportLatency();

    public int getSendEOS();

    public int getConnectionCount();

    public int getRollbackFrequency();

    public boolean isPrintHeaders();
}