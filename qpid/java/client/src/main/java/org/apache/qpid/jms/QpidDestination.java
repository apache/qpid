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
package org.apache.qpid.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.address.AddressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QpidDestination implements Destination, Referenceable
{
    private static final Logger _logger = LoggerFactory.getLogger(QpidDestination.class);
    private static final DestSyntax defaultDestSyntax;
    private DestSyntax _destSyntax = DestSyntax.ADDR;
    
    protected String destinationString;
    protected Address address;

    public String getDestinationString()
    {
        return destinationString;
    }
    
    public void setDestinationString(String str) throws JMSException
    {
    	if (destinationString != null)
    	{
    		throw new javax.jms.IllegalStateException("Once an address string is set, it cannot be set again");
    	}
        destinationString = str;
        parseDestinationString(str);
    }
    
    protected void parseDestinationString(String str) throws JMSException
    {
        _destSyntax = getDestType(str);
        str = stripSyntaxPrefix(str);
        
        if (_logger.isDebugEnabled())
        {
        	_logger.debug("Based on " + str + " the selected destination syntax is " + _destSyntax);
        }
        
        try 
        {
			if (_destSyntax == DestSyntax.BURL)
			{    
				address = DestinationStringParser.parseAddressString(str);     
			}
			else
			{
				address = DestinationStringParser.parseBURLString(str);  
			}
		}
        catch (AddressException e)
        {
			JMSException ex = new JMSException("Error parsing destination string, due to : " + e.getMessage());
			ex.initCause(e);
			ex.setLinkedException(e);
			throw ex;
		}        
    }
    
    protected Address getAddress()
    {
    	return address;
    }

    @Override
    public Reference getReference() throws NamingException
    {
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr(this.getClass().getName(), toString()),
                AMQConnectionFactory.class.getName(),
                null);          // factory location
    }


    // ------- utility methods -------
    
    static
    {
        defaultDestSyntax = DestSyntax.getSyntaxType(
                     System.getProperty(ClientProperties.DEST_SYNTAX,
                                        DestSyntax.ADDR.toString()));
    }
    
    public enum DestSyntax 
    {        
        BURL,ADDR;
        
        public static DestSyntax getSyntaxType(String s)
        {
            if (("BURL").equals(s))
            {
                return BURL;
            }
            else if (("ADDR").equals(s))
            {
                return ADDR;
            }
            else
            {
                throw new IllegalArgumentException("Invalid Destination Syntax Type" +
                                                   " should be one of {BURL|ADDR}");
            }
        }
    }
    
    public  static DestSyntax getDestType(String str)
    {
        if (str.startsWith("ADDR:"))                
        {
            return DestSyntax.ADDR;
        }
        else if (str.startsWith("BURL:"))
        {
            return DestSyntax.BURL;
        }
        else
        {
            return defaultDestSyntax;
        }
    }
    
    public static String stripSyntaxPrefix(String str)
    {
        if (str.startsWith("BURL:") || str.startsWith("ADDR:"))
        {
            return str.substring(5,str.length());
        }
        else
        {
            return str;
        }
    }
}
