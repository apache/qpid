package org.apache.qpid.example.subscriber;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;

import javax.jms.*;

import org.apache.qpid.example.shared.Statics;
import org.apache.qpid.example.shared.ConnectionException;

import java.net.InetAddress;

/**
 * Subscriber which consumes messages from a queue
 * Author: Marnie McCormack
 * Date: 12-Sep-2006
 * Time: 09:41:07
 * Copyright JPMorgan Chase 2006
 */

public class Subscriber
{
    private static final Logger _logger = Logger.getLogger(Subscriber.class);

    protected static Connection _connection;

    protected static MessageConsumer _consumer;

    protected static Session _session;



    /*
    * Listener class that handles messages
    */
    public static class AMSMessageListener implements MessageListener
    {
        private String _name;

        public AMSMessageListener(String name)
        {
            _name = name;

        }

        /*
        * Listens for message callbacks, handles and then acknowledges them
        * @param message - the message received
        */
        public void onMessage(javax.jms.Message message)
        {
            _logger.info(_name + " got message '" + message + "'");

            try
            {
                //@TODO handle your message appropriately for your application here ?

                _logger.debug("Acknowledging recieved message");

                //Now acknowledge the message to clear it from our queue
                message.acknowledge();
            }
            catch(JMSException j)
            {
                _logger.error("JMSException trying to acknowledge message receipt");
                j.printStackTrace();
            }
            catch(Exception e)
            {
                _logger.error("Unexpected exception trying to handle message");
                e.printStackTrace();
            }
        }
    }

    /*
    * Subscribes to AMS Queue and attaches listener
    * @param hostdetails - for broker connection in host1:port1;host2:port2 format
    * @param username - for connection to the broker
    * @password - for connection to the broker
    * @virtualpath
    */
    public void subscribe(String hostdetails, String username, String password,
                          String virtualPath, String queue)
    {
        Queue q;

        _logger.info("Starting subscription ...");


        try
        {
            //To enable failover simply specify more than one host:port combination for hostdetails
            //Format is host1:port1;host2:port2
            _connection = getConnectionWithFailover(hostdetails,username,password,virtualPath);

            //Default to a queue with a default name if queue is null - replace with your own name from config etc
            if (queue==null || queue.length()==0)
            {
                q = getSession(_connection).createQueue(Statics.QUEUE_NAME);
            }
            else
            {
                q = getSession(_connection).createQueue(queue);
            }

            //Create a consumer with a destination of our queue which will use defaults for prefetch etc
            _consumer = getSession(_connection).createConsumer(q);

            //give the message listener a name of it's own
            _consumer.setMessageListener(new AMSMessageListener("MessageListener " + System.currentTimeMillis()));

            _connection.start();
        }
        catch (Throwable t)
        {
            _logger.error("Fatal error: " + t);
            t.printStackTrace();
        }

        _logger.info("Waiting for messages ...");

        //wait for messages and sleep to survive failover
        try
        {
            while(true)
            {
                Thread.sleep(Long.MAX_VALUE);
            }
        }
        catch (Exception e)
        {
            _logger.warn("Exception while Subscriber sleeping",e);
        }
    }

    /*
    * stop consuming and close connection
    */
    public void stop()
    {
        try
        {
            _consumer.close();
            _consumer = null;
            _connection.stop();
            _connection.close();
        }
        catch(JMSException j)
        {
            _logger.error("JMSException trying to Subscriber.stop: " + j.getStackTrace());
        }
    }

    /*
    * Get a connection for our broker with failover by providing an array of hostdetails
    * @param hostdetails - a delimited string of host1:port1;host2:port2 style connection details
    * @param username - for connection to the broker
    * @password - for connection to the broker
    * @virtualpath
    */
    protected Connection getConnectionWithFailover(String hostdetails, String username, String password,
                                                   String virtualPath) throws ConnectionException
    {
        if (_connection == null)
        {
            try
            {
                _connection = new AMQConnection(hostdetails,username,password,InetAddress.getLocalHost().getHostName(),virtualPath);

                //To use a url to get your connection create a string in this format and then get a connection with it
                //String myurl = "amqp://guest:guest@/temp?brokerlist='tcp://localhost:5672',failover='roundrobin'";
                //_connection =  new AMQConnectionFactory(url).createConnection();

                return _connection;
            }
            catch (Exception e)
            {
                throw new ConnectionException(e.toString());
            }
        }
        else
        {
            return _connection;
        }
    }

    /*
    * Creates a non-transacted session for consuming messages
    * Using client acknowledge mode means messages removed from queue only once ack'd
    * @param connection - to the broker
    */
    protected Session getSession(Connection connection) throws JMSException
    {
        if (_session == null)
        {
            _session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            return _session;
        }
        else
        {
            return _session;
        }
    }

}




