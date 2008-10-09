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

import org.xml.sax.SAXException;

import junit.framework.TestCase;

/**
 * Test case for configurator.
 * 
 * @author Andrea Gazzarini
 *
 */
public class ConfiguratorTest extends TestCase
{
    
    /**
     * Tests the execution of the configure() method when a inexistent configuration file is used.
     * 
     * <br>precondition : the configuration file doesn't exist.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testConfigureKO_WithConfigFileNotFound() 
    {
        Configurator configurator = getConfiguratorWithDatafileInjected("pippo/pluto/paperino.xml");
        try {
            configurator.configure();
            fail("If there's no configuration file the configuration cannot be built.");
        } catch(ConfigurationException expected) {
            
        }
    }
    
    /**
     * Tests the execution of the configure() method when configuration file is null.
     * 
     * <br>precondition : the configuration file is null.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testConfigureKO_WithNullConfig() 
    {
        Configurator configurator = getConfiguratorWithDatafileInjected(null);
        try {
            configurator.configure();
            fail("If there's no configuration file the configuration cannot be built.");
        } catch(ConfigurationException expected) {
            
        }
    }    
    
    /**
     * Tests the changes of the configurator internal state while configuration file is parsed.
     * 
     * <br>precondition: N.A.
     * <br>postcondition: N.A.
     */
    public void testDirectorParsing() throws SAXException{
        Configurator configurator = new Configurator();
        
        assertSame(Configurator.DEFAULT_PARSER,configurator._currentParser);
        
        configurator.startElement(null, null, Tag.TYPE_MAPPINGS.toString(), null);
        assertSame(configurator._typeMappingParser,configurator._currentParser);
        
        configurator.startElement(null, null, Tag.ACCESS_MODE_MAPPINGS.toString(), null);
        assertSame(configurator._accessModeMappingParser,configurator._currentParser);

        configurator.startElement(null, null, Tag.BROKERS.toString(), null);
        assertSame(configurator._brokerConfigurationParser,configurator._currentParser);
        
        configurator.startElement(null, null, Tag.MANAGEMENT_QUEUE.toString(), null);
        assertSame(configurator._managementQueueHandlerParser,configurator._currentParser);
        
        configurator.startElement(null, null, Tag.METHOD_REPLY_QUEUE.toString(), null);
        assertSame(configurator._methodReplyQueueHandlerParser,configurator._currentParser);
    }    
    /**
     * Create a stub configurator which returns the given datafile path.
     * 
     * @param filename the configuration file to be injected.
     * @return a configurator which returns the given datafile path.
     */
    private Configurator getConfiguratorWithDatafileInjected(final String filename) {
      return new Configurator()
      {
          @Override
          String getConfigurationFileName ()
          {
              return filename;
          }
      };
    }
}