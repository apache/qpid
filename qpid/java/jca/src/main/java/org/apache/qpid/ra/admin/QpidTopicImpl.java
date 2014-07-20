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
package org.apache.qpid.ra.admin;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.ra.inflow.QpidActivation;

public class QpidTopicImpl extends AMQTopic implements QpidTopic, Externalizable
{
    private static final long serialVersionUID = -2872685901706214228L;
    private String _url;

    public QpidTopicImpl()
    {
    	super();
    }
    public QpidTopicImpl(final String address) throws Exception
    {
        super(address);
        this._url = address;
    }

    @Override
    public Reference getReference() throws NamingException
    {
        return new Reference(this.getClass().getName(), new StringRefAddr(this.getClass().getName(), toURL()),
                AdminObjectFactory.class.getName(), null);
    }

    @Override
    public String toURL()
    {
        return _url;
    }

    public void setDestinationAddress(String destinationAddress) throws Exception
    {
        this._url = destinationAddress;
        setDestinationString(this._url);
    }


    public String getDestinationAddress()
    {
    	return this._url;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        this._url = (String)in.readObject();

        try
        {
            setDestinationString(this._url);

        }
        catch(Exception e)
        {
        	throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        out.writeObject(this._url);
    }
}
