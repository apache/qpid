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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MapMessage;
import javax.jms.StreamMessage;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.MessageEOFException;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;

import org.apache.qpid.jms.ListMessage;

import java.util.Enumeration;
import java.util.Iterator;

public class ListReceiver {

    public static void main(String[] args) throws Exception
    {
	if (args.length != 1) {
		System.out.println("Usage: java org.apache.qpid.example.ListReceiver <-l | -m | -s>");
		System.out.println("where:");
		System.out.println("\t-l\tAccept ListMessage and print it");
		System.out.println("\t-m\tAccept ListMessage as a MapMessage");
		System.out.println("\t-s\tAccept ListMessage as a StreamMessage");
		return;
	}

        Connection connection =
            new AMQConnection("amqp://guest:guest@test/?brokerlist='tcp://localhost:5672'");

        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination queue = new AMQAnyDestination("ADDR:message_queue; {create: always}");
        MessageConsumer consumer = session.createConsumer(queue);

	if (args[0].equals("-l")) {
		System.out.println("Receiving as ListMessage");
        	ListMessage m = (ListMessage)consumer.receive();
	        System.out.println(m);
		System.out.println("==========================================");
		System.out.println("Printing list contents:");
		Iterator i = m.iterator();
		while(i.hasNext())
			System.out.println(i.next());
	}
	else if (args[0].equals("-m")) {
		System.out.println("Receiving as MapMessage");
        	MapMessage m = (MapMessage)consumer.receive();
	        System.out.println(m);
		System.out.println("==========================================");
		System.out.println("Printing map contents:");
		Enumeration keys = m.getMapNames();
		while(keys.hasMoreElements()) {
			String key = (String)keys.nextElement();
			System.out.println(key + " => " + m.getObject(key));
		}
	}
	else if (args[0].equals("-s")) {
		System.out.println("Receiving as StreamMessage");
        	StreamMessage m = (StreamMessage)consumer.receive();
	        System.out.println(m);
		System.out.println("==========================================");
		System.out.println("Printing stream contents:");
		try {
			while(true)
				System.out.println(m.readObject());
		}
		catch (MessageEOFException e) {
			// DONE
		}
	}

        connection.close();
    }
}
