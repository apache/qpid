/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.nclient.jms;

import org.apache.qpid.common.QpidProperties;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import java.util.Enumeration;

/**
 * Implements javax.jms.ConnectionMetaData
 * A ConnectionMetaDataImpl provides information describing the JMS <code>Connection</code>.
 */
public class ConnectionMetaDataImpl implements ConnectionMetaData
{

    /**
     * A singleton instance.
     */
    static ConnectionMetaDataImpl _singleton = new ConnectionMetaDataImpl();

    // ------------------------  The metadata
    // JMS major version
    private static final int JMS_MAJOR_VERSION = 1;
    // JMS minor version
    private static final int JMS_MINOR_VERSION = 1;
    // JMS version
    private static final String JMS_VERSION = "1.1";
    // Provider name
    private static final String PROVIDER_NAME = "Apache " + QpidProperties.getProductName();
    // Provider major version
    private static final int PROVIDER_MAJOR_VERSION = 0;
    // Provider minor version
    private static final int PROVIDER_MINOR_VERSION = 10;
    // Provider version
    private static final String PROVIDER_VERSION = QpidProperties.getProductName() + " (Client: [" + QpidProperties.getBuildVersion() + "]  ; Protocol: [ 0.10 ] )";

    /**
     * Prevent instantiation.
     */
    private ConnectionMetaDataImpl()
    {
    }

    /**
     * Get the singleton instance of ConnectionMetaDataImpl.
     *
     * @return the singleton instance of ConnectionMetaDataImpl.
     */
    public static ConnectionMetaDataImpl getInstance()
    {
        return _singleton;
    }

    //-- Connection MetaData API

    /**
     * Gets the JMS API version.
     *
     * @return the JMS API version
     * @throws JMSException Never
     */
    public String getJMSVersion() throws JMSException
    {
        return JMS_VERSION;
    }


    /**
     * Gets the JMS major version number.
     *
     * @return the JMS API major version number
     * @throws JMSException Never
     */
    public int getJMSMajorVersion() throws JMSException
    {
        return JMS_MAJOR_VERSION;
    }


    /**
     * Gets the JMS minor version number.
     *
     * @return the JMS API minor version number
     * @throws JMSException Never
     */
    public int getJMSMinorVersion() throws JMSException
    {
        return JMS_MINOR_VERSION;
    }


    /**
     * Gets Qpid name.
     *
     * @return Qpid name
     * @throws JMSException Never
     */
    public String getJMSProviderName() throws JMSException
    {
        return PROVIDER_NAME;
    }

    /**
     * Gets Qpid version.
     *
     * @return Qpid version
     * @throws JMSException Never
     */
    public String getProviderVersion() throws JMSException
    {
        return PROVIDER_VERSION;
        // TODO: We certainly can dynamically get the server version.
    }

    /**
     * Gets Qpid major version number.
     *
     * @return Qpid major version number
     * @throws JMSException Never
     */
    public int getProviderMajorVersion() throws JMSException
    {
        return PROVIDER_MAJOR_VERSION;
    }

    /**
     * Gets Qpid minor version number.
     *
     * @return Qpid minor version number
     * @throws JMSException Never
     */
    public int getProviderMinorVersion() throws JMSException
    {
        return PROVIDER_MINOR_VERSION;
    }

    /**
     * Gets an enumeration of the JMSX property names.
     *
     * @return an Enumeration of JMSX property names
     * @throws JMSException if cannot retrieve metadata due to some internal error.
     */
    public Enumeration getJMSXPropertyNames() throws JMSException
    {
        return CustomJMSXProperty.asEnumeration();
    }

}
