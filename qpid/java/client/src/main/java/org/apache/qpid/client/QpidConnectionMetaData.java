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
package org.apache.qpid.client;

import java.util.Enumeration;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

public class QpidConnectionMetaData implements ConnectionMetaData
{



    QpidConnectionMetaData(AMQConnection conn)
    {
    }

    public int getJMSMajorVersion() throws JMSException
    {
        return 1;
    }

    public int getJMSMinorVersion() throws JMSException
    {
        return 1;
    }

    public String getJMSProviderName() throws JMSException
    {
        return "Apache Qpid";
    }

    public String getJMSVersion() throws JMSException
    {
        return "1.1";
    }

    public Enumeration getJMSXPropertyNames() throws JMSException
    {
        return CustomJMXProperty.asEnumeration();
    }

    public int getProviderMajorVersion() throws JMSException
    {
        return 0;
    }

    public int getProviderMinorVersion() throws JMSException
    {
        return 8;
    }

    public String getProviderVersion() throws JMSException
    {
        return "QPID (Client: [" + getClientVersion() + "] ; Broker [" + getBrokerVersion() + "] ; Protocol: [ "
                + getProtocolVersion() + "] )";
    }

    private String getProtocolVersion()
    {
        // TODO - Implement based on connection negotiated protocol
        return "0.8";
    }

    public String getBrokerVersion()
    {
        // TODO - get broker version
        return "<unkown>";
    }

    public String getClientVersion()
    {
        // TODO - get client build version from properties file or similar
        return "<unknown>";
    }


}
