/*
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
 */
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.ConnectionMetaData;

import javax.jms.JMSException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;

public class ConnectionMetaDataImpl implements ConnectionMetaData
{
    private static final int JMS_MAJOR_VERSION = 1;
    private static final int JMS_MINOR_VERSION = 1;

    private static final int PROVIDER_MAJOR_VERSION = 1;
    private static final int PROVIDER_MINOR_VERSION = 0;


    private final int _amqpMajorVersion;
    private final int _amqpMinorVersion;
    private final int _amqpRevisionVersion;
    private static final Collection<String> _jmsxProperties = Arrays.asList("JMSXGroupID", "JMSXGroupSeq");

    public ConnectionMetaDataImpl(final int amqpMajorVersion, final int amqpMinorVersion, final int amqpRevisionVersion)
    {
        _amqpMajorVersion = amqpMajorVersion;
        _amqpMinorVersion = amqpMinorVersion;
        _amqpRevisionVersion = amqpRevisionVersion;
    }

    public String getJMSVersion() throws JMSException
    {
        return getJMSMajorVersion() + "." + getJMSMinorVersion();
    }

    public int getJMSMajorVersion() throws JMSException
    {
        return JMS_MAJOR_VERSION;
    }

    public int getJMSMinorVersion() throws JMSException
    {
        return JMS_MINOR_VERSION;
    }

    public String getJMSProviderName() throws JMSException
    {
        return "AMQP.ORG";
    }

    public String getProviderVersion() throws JMSException
    {
        return getProviderMajorVersion() + "." + getProviderMinorVersion();
    }

    public int getProviderMajorVersion() throws JMSException
    {
        return PROVIDER_MAJOR_VERSION;
    }

    public int getProviderMinorVersion() throws JMSException
    {
        return PROVIDER_MINOR_VERSION;
    }

    public Enumeration getJMSXPropertyNames() throws JMSException
    {

        return Collections.enumeration(_jmsxProperties);
    }

    public int getAMQPMajorVersion()
    {
        return _amqpMajorVersion;
    }

    public int getAMQPMinorVersion()
    {
        return _amqpMinorVersion;
    }

    public int getAMQPRevisionVersion()
    {
        return _amqpRevisionVersion;
    }
}
