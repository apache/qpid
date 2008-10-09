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
package org.apache.qpid.management.configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.qpid.management.Names;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Director used for coordinating the build process of configuration.
 * This is the only component which has a read-write permission on Configuration object.
 * 
 * @author Andrea Gazzarini
 */
public class Configurator extends DefaultHandler
{    
    /**
     * Default (empty) parser used when there's no need to process data (non relevant elements).
     */
    final static IParser DEFAULT_PARSER = new IParser() {

        public void setCurrrentAttributeValue (String value)
        {
        }

        public void setCurrentAttributeName (String name)
        {
        }
    };
    
    IParser _typeMappingParser = new TypeMappingParser();
    IParser _accessModeMappingParser = new AccessModeMappingParser();
    IParser _brokerConfigurationParser = new BrokerConnectionDataParser();
    IParser _managementQueueHandlerParser = new ManagementQueueMessageListenerParser();
    IParser _methodReplyQueueHandlerParser = new MethodReplyQueueMessageListenerParser();

    IParser _currentParser = DEFAULT_PARSER;
    
    /**
     * Delegates the processing to the current parser.
     */
    @Override
    public void characters (char[] ch, int start, int length) throws SAXException
    {
        String value = new String(ch,start,length).trim();
        if (value.length() != 0) {
            _currentParser.setCurrrentAttributeValue(value);
        }
    }

    /**
     * Here is defined what parser needs to be used for processing the current data.
     */
    @Override
    public void startElement (String uri, String localName, String name, Attributes attributes) throws SAXException
    {
        switch(Tag.get(name)) {
            case TYPE_MAPPINGS : {
                _currentParser = _typeMappingParser;
                break;
            }
            case ACCESS_MODE_MAPPINGS: 
            {
                _currentParser = _accessModeMappingParser;
                break;
            }
            case BROKERS: 
            {
                _currentParser = _brokerConfigurationParser;
                break;
            }
            case MANAGEMENT_QUEUE: 
            {
                _currentParser = _managementQueueHandlerParser;
                break;
            }  
            case METHOD_REPLY_QUEUE: 
            {
                _currentParser = _methodReplyQueueHandlerParser;
                break;
            } 
        }
    }
    
    @Override
    public void endElement (String uri, String localName, String name) throws SAXException
    {
        _currentParser.setCurrentAttributeName(name);
    }

    /**
     * Builds whole configuration.
     * 
     * @throws ConfigurationException when the build fails.
     */
    public void configure() throws ConfigurationException 
    {
        try 
        {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(getConfigurationFileName()),"UTF8"));
            InputSource source = new InputSource(reader);
            parser.parse(source, this);
        } catch (Exception exception)
        {
            throw new ConfigurationException(exception);
        }
    }
    
    /**
     * Returns the name of the configuration file.
     * 
     * @return the name of the configuration file.
     */
    String getConfigurationFileName()
    {
        return Names.CONFIGURATION_FILE_NAME;
    }
}