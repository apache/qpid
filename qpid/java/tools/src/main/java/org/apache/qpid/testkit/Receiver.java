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
package org.apache.qpid.testkit;


import java.util.ArrayList;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;

/**
 * A generic receiver which consumes messages
 * from a given address in a broker (host/port) 
 * until told to stop by killing it.
 * 
 * It participates in a feedback loop to ensure the producer
 * doesn't fill up the queue. If it receives an "End" msg
 * it sends a reply to the replyTo address in that msg.
 * 
 * It doesn't check for correctness or measure anything
 * leaving those concerns to another entity. 
 * However it prints a timestamp every x secs(-Dreport_frequency)
 * as checkpoint to figure out how far the test has progressed if
 * a failure occurred.
 * 
 * It also takes in an optional Error handler to
 * pass out any error in addition to writing them to std err. 
 * 
 * This is intended more as building block to create
 * more complex test cases. However there is a main method
 * provided to use this standalone.
 *
 * The following options are available and configurable 
 * via jvm args.
 * 
 * sync_rcv - Whether to consume sync (instead of using a listener).
 * report_frequency - how often a timestamp is printed
 * durable
 * transacted
 * tx_size - size of transaction batch in # msgs. * 
 * check_for_dups - check for duplicate messages and out of order messages.
 * jms_durable_sub - create a durable subscription instead of a regular subscription.
 */
public class Receiver extends Client implements MessageListener
{
	long msg_count = 0;
	int sequence = 0;
	boolean syncRcv = Boolean.getBoolean("sync_rcv");
	boolean jmsDurableSub = Boolean.getBoolean("jms_durable_sub");
	boolean checkForDups = Boolean.getBoolean("check_for_dups");
	MessageConsumer consumer;
    List<Integer> duplicateMessages = new ArrayList<Integer>();
    
    public Receiver(Connection con,String addr) throws Exception
    {
    	super(con);
    	setSsn(con.createSession(isTransacted(), getAck_mode()));
    	consumer = getSsn().createConsumer(new AMQAnyDestination(addr));
    	if (!syncRcv)
    	{
    		consumer.setMessageListener(this);
    	}
    	
    	System.out.println("Receiving messages from : " + addr);
    }

    public void onMessage(Message msg)
    {    	
    	handleMessage(msg);
    }
    
    public void run() throws Exception
    {
        long sleepTime = getReportFrequency();
    	while(true)
    	{
    		if(syncRcv)
    		{   
    		    long t = sleepTime;
    		    while (t > 0)
    		    {
    		        long start = System.currentTimeMillis();
    		        Message msg = consumer.receive(t);
    		        t = t - (System.currentTimeMillis() - start);
    		        handleMessage(msg);
    		    }
    		}
    		Thread.sleep(sleepTime);
            System.out.println(getDf().format(System.currentTimeMillis())
                    + " - messages received : " + msg_count);
    	}
    }
    
    private void handleMessage(Message m)
    {
        if (m == null)  { return; }
        
    	try
        {   
            if (m instanceof TextMessage && ((TextMessage) m).getText().equals("End"))
            {
                MessageProducer temp = getSsn().createProducer(m.getJMSReplyTo());
                Message controlMsg = getSsn().createTextMessage();
                temp.send(controlMsg);
                if (isTransacted())
                {
                    getSsn().commit();
                }
                temp.close();
            }
            else
            {   
            	
            	int seq = m.getIntProperty("sequence");   
            	if (checkForDups)
            	{
            		if (seq == 0)
	                {
            			sequence = 0; // wrap around for each iteration
            			System.out.println("Received " + duplicateMessages.size() + " duplicate messages during the iteration");
            			duplicateMessages.clear();
	                }
            		
	                if (seq < sequence)
	                {                    
	                    duplicateMessages.add(seq);
	                }
	                else if (seq == sequence)
	                {
	                	sequence++;
	                	msg_count ++;
	                }
	                else
	                {  
	                	// Multiple publishers are not allowed in this test case.
	                	// So out of order messages are not allowed.
	                	throw new Exception(": Received an out of order message (expected="
	                			+ sequence  + ",received=" + seq + ")" ); 
	                }
            	}
            	else
            	{
            	    msg_count ++;
            	}
            	
                // Please note that this test case doesn't expect duplicates
                // When testing for transactions.
            	if (isTransacted() && msg_count % getTxSize() == 0)
            	{
            		getSsn().commit();
            	}
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        	handleError("Exception receiving messages",e);
        }   	
    }

    // Receiver host port address
    public static void main(String[] args) throws Exception
    {
    	String host = "127.0.0.1";
    	int port = 5672;
    	String addr = "message_queue";
    	
    	if (args.length > 0)
    	{
    		host = args[0];
    	}
    	if (args.length > 1)
    	{
    		port = Integer.parseInt(args[1]);
    	}    	
    	if (args.length > 2)
        {
            addr = args[2];    
        }
        
    	AMQConnection con = new AMQConnection(
				"amqp://username:password@topicClientid/test?brokerlist='tcp://"
						+ host + ":" + port + "'");
        
        Receiver rcv = new Receiver(con,addr);
        rcv.run();
    }

}
