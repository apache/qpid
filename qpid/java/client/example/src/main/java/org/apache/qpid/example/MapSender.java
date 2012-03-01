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

package org.apache.qpid.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MapMessage;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;


public class MapSender {

    public static void main(String[] args) throws Exception 
    {
        Connection connection = 
            new AMQConnection("amqp://guest:guest@test/?brokerlist='tcp://localhost:5672'");
        
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination queue = new AMQAnyDestination("ADDR:message_queue; {create: always}");
        MessageProducer producer = session.createProducer(queue);

        MapMessage m = session.createMapMessage();
        m.setIntProperty("Id", 987654321);
        m.setStringProperty("name", "Widget");
        m.setDoubleProperty("price", 0.99);
        
        List<String> colors = new ArrayList<String>();
        colors.add("red");
        colors.add("green");
        colors.add("white");        
        m.setObject("colours", colors);
        
        Map<String,Double> dimensions = new HashMap<String,Double>();
        dimensions.put("length",10.2);
        dimensions.put("width",5.1);
        dimensions.put("depth",2.0);
        m.setObject("dimensions",dimensions);
        
        List<List<Integer>> parts = new ArrayList<List<Integer>>();
        parts.add(Arrays.asList(new Integer[] {1,2,5}));
        parts.add(Arrays.asList(new Integer[] {8,2,5}));
        m.setObject("parts", parts);
        
        Map<String,Object> specs = new HashMap<String,Object>();
        specs.put("colours", colors);
        specs.put("dimensions", dimensions);
        specs.put("parts", parts);
        m.setObject("specs",specs);
        
        producer.send(m);
        connection.close();
    }

}