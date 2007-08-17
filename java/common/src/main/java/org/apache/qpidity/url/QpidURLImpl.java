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
package org.apache.qpidity.url;

import org.apache.qpidity.BrokerDetails;
import org.apache.qpidity.BrokerDetailsImpl;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The format Qpid URL is based on the AMQP one.
 * The grammar is as follows:
 * <p> qpid_url          = "qpid:" port_addr_list ["/" future-parameters]
 * <p> port_addr_list 	 = [port_addr ","]* port_addr
 * <p> port_addr         = tcp_port_addr | tls_prot_addr | future_prot_addr
 * <p> tcp_port_addr     = tcp_id tcp_addr
 * <p> tcp_id            = "tcp:" | ""
 * <p> tcp_addr          = [host [":" port] ]
 * <p> host              = <as per [2]>
 * <p> port              = number
 * <p> tls_prot_addr     = tls_id tls_addr
 * <p> tls_id            = "tls:" | ""
 * <p> tls_addr          = [host [":" port] ]
 * <p> future_prot_addr  = future_prot_id future_prot_addr
 * <p> future_prot_id    = <placeholder, must end in ":". Example "sctp:">
 * <p> future_prot_addr  = <placeholder, protocl-specific address>
 * <p> future_parameters = <placeholder, not used in failover addresses>
 * 
 * Ex: qpid:virtualhost=test@client_id1:tcp:myhost.com:5672,virtualhost=prod,keystore=/opt/keystore@client_id2:tls:mysecurehost.com:5672
 */
public class QpidURLImpl implements QpidURL
{
    private static final char[] URL_START_SEQ = new char[]{'q','p','i','d',':'}; 
    private static final char PROPERTY_EQUALS_CHAR = '=';
    private static final char PROPERTY_SEPARATOR_CHAR = ',';
    private static final char ADDRESS_SEPERATOR_CHAR = ',';
    private static final char CLIENT_ID_TRANSPORT_SEPARATOR_CHAR = ':';
    private static final char TRANSPORT_HOST_SEPARATOR_CHAR = ':';
    private static final char HOST_PORT_SEPARATOR_CHAR = ':';
    private static final char AT_CHAR = '@';
    
    enum URLParserState
    {
       QPID_URL_START,       
       ADDRESS_START,
       PROPERTY_NAME,
       PROPERTY_EQUALS,
       PROPERTY_VALUE,
       PROPERTY_SEPARATOR,
       AT_CHAR,
       CLIENT_ID,
       CLIENT_ID_TRANSPORT_SEPARATOR,
       TRANSPORT,
       TRANSPORT_HOST_SEPARATOR,
       HOST,
       HOST_PORT_SEPARATOR,
       PORT,
       ADDRESS_END,
       ADDRESS_SEPERATOR,
       QPID_URL_END,
       ERROR;
    }
       
    //-- Constructors
    
    private char[] _url;    
    private List<BrokerDetails> _brokerDetailList = new ArrayList<BrokerDetails>();
    private String _error;
    private int _index = 0;
    private BrokerDetails _currentBroker;
    private String _currentPropName;
    private boolean _endOfURL = false;
    private URLParserState _currentParserState;
    
    public QpidURLImpl(String url) throws MalformedURLException
    {    
        _url = url.toCharArray();
        _endOfURL = false;
        _currentParserState = URLParserState.QPID_URL_START;
        URLParserState prevState = _currentParserState; // for error handling        
        BrokerDetails _brokerDetails = new BrokerDetailsImpl();
        try
        {   
            while ( _currentParserState != URLParserState.ERROR && _currentParserState != URLParserState.QPID_URL_END)
            {
                prevState = _currentParserState;
                _currentParserState = next();
            }   
            
            if(_currentParserState == URLParserState.ERROR)
            {
                _error = "Invalid URL format [current_state = " +  prevState + ", broker details parsed so far " + _currentBroker + " ] error at (" + _index +") due to " + _error;            
                MalformedURLException ex = new MalformedURLException(_error);
                throw ex;
            }
        }
        catch(ArrayIndexOutOfBoundsException e)
        {
            _error = "Invalid URL format [current_state = " +  prevState + ", broker details parsed so far " + _currentBroker + " ] error at (" + _index +")";            
            MalformedURLException ex = new MalformedURLException(_error);
            throw ex;
        }
    }
    
    //-- interface QpidURL
    public List<BrokerDetails> getAllBrokerDetails()
    {
        return _brokerDetailList;
    }

     public String getURL()
     {
         return new String(_url);
     }
     
     private URLParserState next()
     {
         switch(_currentParserState)      
         {
         case QPID_URL_START:             
             return checkSequence(URL_START_SEQ,URLParserState.ADDRESS_START);
         case ADDRESS_START:
             return startAddress();
         case PROPERTY_NAME:    
             return extractPropertyName();
         case PROPERTY_EQUALS:   
             _index++; // skip the equal sign
             return URLParserState.PROPERTY_VALUE;
         case PROPERTY_VALUE:    
             return extractPropertyValue();
         case PROPERTY_SEPARATOR:
             _index++; // skip ","
             return URLParserState.PROPERTY_NAME;
         case AT_CHAR:
             _index++; // skip the @ sign
         case CLIENT_ID:    
             return extractClientId();
         case CLIENT_ID_TRANSPORT_SEPARATOR:
             _index++; // skip ":"
             return URLParserState.TRANSPORT;
         case TRANSPORT:    
             return extractTransport();
         case TRANSPORT_HOST_SEPARATOR:
             _index++; // skip ":"
             return URLParserState.HOST;
         case HOST:
             return extractHost();
         case HOST_PORT_SEPARATOR:
             _index++; // skip ":"
             return URLParserState.PORT;
         case PORT:
             extractPort();
         case ADDRESS_END:
             return endAddress();
         case ADDRESS_SEPERATOR:
             _index++; // skip ","
             return URLParserState.ADDRESS_START;
         default:
             return URLParserState.ERROR;
         }
     }
     
     private URLParserState checkSequence(char[] expected,URLParserState nextPart)
     {   
         for(int i=0;i<expected.length;i++)
         {
             if(expected[i] != _url[_index])
             {
                 _error = "Excepted (" + expected[i] + ") at position " + _index + ", got (" + _url[_index] + ")";
                 return URLParserState.ERROR;
             }
             _index++;
         }
         return nextPart;
     }   
     
     private URLParserState startAddress()
     {
         _currentBroker = new BrokerDetailsImpl();
         return URLParserState.PROPERTY_NAME;
     }
     
     private URLParserState endAddress()
     {
         _brokerDetailList.add(_currentBroker);
         if(_endOfURL)
         {
             return URLParserState.QPID_URL_END;
         }
         else
         {
             return URLParserState.ADDRESS_SEPERATOR;
         }
     }
     
     private URLParserState extractPropertyName()
     {
         StringBuilder b = new StringBuilder();
         char next = _url[_index];
         while(next != PROPERTY_EQUALS_CHAR && next != AT_CHAR)
         {
             b.append(next);
             next = _url[++_index];
         }
         _currentPropName = b.toString();         
         if(_currentPropName.trim().equals(""))
         {
             _error = "Property name cannot be empty";
             return URLParserState.ERROR;
         }
         else if (next == PROPERTY_EQUALS_CHAR)
         {
             return URLParserState.PROPERTY_EQUALS;
         }
         else
         {
             return URLParserState.AT_CHAR;
         }
     }
     
     private URLParserState extractPropertyValue()
     {
         StringBuilder b = new StringBuilder();
         char next = _url[_index];
         while(next != PROPERTY_SEPARATOR_CHAR && next != AT_CHAR)
         {
             b.append(next);
             next = _url[++_index];
         }
         String propValue = b.toString();
         if(propValue.trim().equals(""))
         {
             _error = "Property values cannot be empty";
             return URLParserState.ERROR;
         }
         else
         {
             _currentBroker.setProperty(_currentPropName, propValue);
             if (next == PROPERTY_SEPARATOR_CHAR)
             {
                 return URLParserState.PROPERTY_SEPARATOR;
             }
             else
             {
                 return URLParserState.AT_CHAR;
             }
         }
     }
     
     private URLParserState extractClientId()
     {
         //Check if atleast virtualhost is there
         if (_currentBroker.getProperties().get(BrokerDetails.VIRTUAL_HOST) == null)
         {
             _error = "Virtual host is a mandatory property";
             return URLParserState.ERROR;
         }
         else
         {
             _currentBroker.setVirtualHost(_currentBroker.getProperties().get(BrokerDetails.VIRTUAL_HOST));
             _currentBroker.getProperties().remove(BrokerDetails.VIRTUAL_HOST);
         }
         
         if (_currentBroker.getProperties().get(BrokerDetails.USERNAME) != null)
         {
             String username = _currentBroker.getProperties().get(BrokerDetails.USERNAME);
             _currentBroker.setUserName(username);
         }
         if (_currentBroker.getProperties().get(BrokerDetails.PASSWORD) != null)
         {
             String password = _currentBroker.getProperties().get(BrokerDetails.PASSWORD);
             _currentBroker.setPassword(password);
         }
         
         String clientId = buildUntil(CLIENT_ID_TRANSPORT_SEPARATOR_CHAR);
         if (clientId.trim().equals(""))
         {
             _error = "Client Id cannot be empty";
             return URLParserState.ERROR;
         }
         else
         {
             _currentBroker.setProperty(BrokerDetails.CLIENT_ID, clientId);
             return URLParserState.CLIENT_ID_TRANSPORT_SEPARATOR;
         }
     }
     
     private URLParserState extractTransport()
     {
         String transport = buildUntil(TRANSPORT_HOST_SEPARATOR_CHAR);
         if (transport.trim().equals(""))
         {
             _error = "Transport cannot be empty";
             return URLParserState.ERROR;
         }
         else
         {
             _currentBroker.setProtocol(transport);
             return URLParserState.TRANSPORT_HOST_SEPARATOR;
         }
     }
     
     private URLParserState extractHost()
     {
         String host = buildUntil(HOST_PORT_SEPARATOR_CHAR);
         if (host.trim().equals(""))
         {
             _error = "Host cannot be empty";
             return URLParserState.ERROR;
         }
         else
         {
             _currentBroker.setHost(host);
             return URLParserState.HOST_PORT_SEPARATOR;
         }
     }
     
     private URLParserState extractPort()
     {
         
         StringBuilder b = new StringBuilder();
         try
         {
             char next = _url[_index];
             while(next != ADDRESS_SEPERATOR_CHAR)
             {
                 b.append(next);
                 next = _url[++_index];
             }
         }
         catch(ArrayIndexOutOfBoundsException e)
         {
             _endOfURL = true;
         }         
         String portStr = b.toString();
         if (portStr.trim().equals(""))
         {
             _error = "Host cannot be empty";
             return URLParserState.ERROR;
         }         
         else
         {
             try
             {
                 int port = Integer.parseInt(portStr);
                 _currentBroker.setPort(port);
                 return URLParserState.ADDRESS_END;
             }
             catch(NumberFormatException e)
             {
                 _error = "Illegal number for port";
                 return URLParserState.ERROR;
             }
         }
     }
     
     private String buildUntil(char c)
     {
         StringBuilder b = new StringBuilder();
         char next = _url[_index];
         while(next != c)
         {
             b.append(next);
             next = _url[++_index];
         }
         return b.toString();
     }
     
     public static void main(String[] args)
     {
         String testurl = "qpid:virtualhost=test@client_id1:tcp:myhost.com:5672,virtualhost=prod,keystore=/opt/keystore@client_id2:tls:mysecurehost.com:5672";
         try
         {
             QpidURLImpl impl = new QpidURLImpl(testurl);
             for (BrokerDetails d : impl.getAllBrokerDetails())
             {
                 System.out.println(d);
             }
         }
         catch(Exception e)
         {
             e.printStackTrace();
         }
     }
}
