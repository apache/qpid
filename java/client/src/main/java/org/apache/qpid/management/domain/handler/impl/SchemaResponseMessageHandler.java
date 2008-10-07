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
package org.apache.qpid.management.domain.handler.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.qpid.management.Names;
import org.apache.qpid.management.domain.handler.base.BaseMessageHandler;
import org.apache.qpid.management.domain.model.type.Binary;
import org.apache.qpid.transport.codec.ManagementDecoder;

/**
 * Schema Response message handler.
 * This handler is responsible to process 'S'(opcode) messages sent by the management broker containing the full
 * schema details for a class.
 * 
 * @author Andrea Gazzarini
 */
public class SchemaResponseMessageHandler extends BaseMessageHandler
{  
    /**
     * Processes an incoming  schema response.
     * This will be used for building the corresponding class definition.
     * 
     *  @param decoder the decoder used for parsing the incoming stream.
     *  @param sequenceNumber the sequence number of the incoming message.
     */
    public void process (ManagementDecoder decoder, int sequenceNumber)
    {        
        try 
        {
            int classKind = decoder.readUint8();
            if (classKind != 1) {
                return;
            }
            String packageName = decoder.readStr8();
            String className = decoder.readStr8();
            
            Binary schemaHash = new Binary(decoder.readBin128());
            
            int howManyProperties = decoder.readUint16();
            int howManyStatistics = decoder.readUint16();
            int howManyMethods = decoder.readUint16();
            int howManyEvents = 0;
                                    
            // FIXME : Divide between schema error and raw data conversion error!!!!
            _domainModel.addSchema(
                    packageName, 
                    className, 
                    schemaHash,
                    getProperties(decoder, howManyProperties), 
                    getStatistics(decoder, howManyStatistics), 
                    getMethods(decoder, howManyMethods), 
                    getEvents(decoder, howManyEvents));
        } catch(Exception exception) 
        {
            _logger.error(exception,"<QMAN-100007> : Q-Man was unable to process the schema response message.");
        }
    }
    
    /**
     * Reads from the incoming message stream the properties definitions.
     * 
     * @param decoder the decoder used for decode incoming data.
     * @param howManyProperties the number of properties to read. 
     * @return a list of maps. Each map contains a property definition.
     */
    List<Map<String, Object>> getProperties(ManagementDecoder decoder,int howManyProperties) 
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(howManyProperties);        
        for (int i = 0; i < howManyProperties; i++ )
        {
            result.add(decoder.readMap());
        }
        return result;
    }
    
    /**
     * Reads the statistics definitions from the incoming message stream.
     * 
     * @param decoder the decoder used for decode incoming data.
     * @param howManyProperties the number of statistics to read. 
     * @return a list of maps. Each map contains a statistic definition.
     */
    List<Map<String, Object>> getStatistics(ManagementDecoder decoder,int howManyStatistics) 
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(howManyStatistics);        
        for (int i = 0; i < howManyStatistics; i++ )
        {
            result.add(decoder.readMap());
        }
        return result;
    }

    /**
     * Reads the methods definitions from the incoming message stream.
     * 
     * @param decoder the decoder used for decode incoming data.
     * @param howManyMethods the number of methods to read. 
     * @return a list method definitions.
     */
    List<MethodOrEventDataTransferObject> getMethods(ManagementDecoder decoder, int howManyMethods)
    {
        List<MethodOrEventDataTransferObject> result = new ArrayList<MethodOrEventDataTransferObject>(howManyMethods);
        for (int i  = 0; i < howManyMethods; i++) 
        {   
            Map<String,Object> method = decoder.readMap();
            int howManyArguments = (Integer) method.get(Names.ARG_COUNT_PARAM_NAME);

            List<Map<String,Object>> arguments = new ArrayList<Map<String,Object>>(howManyArguments);
            for (int argIndex = 0; argIndex < howManyArguments; argIndex++){
                arguments.add(decoder.readMap());
            }
            result.add(new MethodOrEventDataTransferObject(method,arguments));
        }        
        return result;
    }
    
    /**
     * Reads the events definitions from the incoming message stream.
     * 
     * @param decoder the decoder used for decode incoming data.
     * @param howManyEvents the number of events to read. 
     * @return a list event definitions.
     */
    List<MethodOrEventDataTransferObject> getEvents(ManagementDecoder decoder, int howManyEvents)
    {
        List<MethodOrEventDataTransferObject> result = new ArrayList<MethodOrEventDataTransferObject>(howManyEvents);
        for (int i  = 0; i < howManyEvents; i++) 
        {   
            Map<String,Object> method = decoder.readMap();
            int howManyArguments = (Integer) method.get(Names.ARG_COUNT_PARAM_NAME);

            List<Map<String,Object>> arguments = new ArrayList<Map<String,Object>>(howManyArguments);
            for (int argIndex = 0; argIndex < howManyArguments; argIndex++){
                arguments.add(decoder.readMap());
            }
            result.add(new MethodOrEventDataTransferObject(method,arguments));
        }        
        return result;
    }
 }
