package org.apache.qpid.example.subscriber;

import org.apache.log4j.Logger;
import org.apache.qpid.example.shared.Statics;

import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.JMSException;
import javax.jms.Queue;

/**
 * Subclass of Subscriber which consumes a heartbeat message
 * Author: Marnie McCormack
 * Date: 12-Sep-2006
 * Time: 09:41:07
 * Copyright JPMorgan Chase 2006
 */

public class MonitoredSubscriber extends Subscriber
{
    private static final Logger _logger = Logger.getLogger(MonitoredSubscriber.class);

    private static MessageConsumer _monitorConsumer;

    public static class MonitorMessageListener implements MessageListener
    {
        private String _name;

        public MonitorMessageListener(String name)
        {
            _name = name;

        }

        /*
        * Listens for heartbeat messages and acknowledges them
        */
        public void onMessage(javax.jms.Message message)
        {
            _logger.info(_name + " monitor got message '" + message + "'");

            try
            {
               _logger.debug("Monitor acknowledging recieved message");

                //Now acknowledge the message to clear it from our queue
                message.acknowledge();
            }
            catch(JMSException j)
            {
                _logger.error("Monitor caught JMSException trying to acknowledge message receipt");
                j.printStackTrace();
            }
            catch(Exception e)
            {
                _logger.error("Monitor caught unexpected exception trying to handle message");
                e.printStackTrace();
            }
        }
    }

    /*
    * Subscribes to Queue and attaches additional monitor listener
    * @param hostdetails - for broker connection in host1:port1;host2:port2 format
    * @param username - for connection to the broker
    * @password - for connection to the broker
    * @virtualpath
    */
    public void subscribeAndMonitor(String hostdetails, String username, String password,
                                      String virtualPath, String queueName)
    {
        Queue queue;

        try
        {
            //Create monitor comsumer for failover purposes
            if (queueName==null||queueName.length()==0)
            {
                queue = getSession(_connection).createQueue(Statics.QUEUE_NAME);
            }
            else
            {
                queue = getSession(_connection).createQueue(queueName);
            }

            _monitorConsumer = getSession(_connection).createConsumer(queue);

            //give the monitor message listener a name of it's own
            _monitorConsumer.setMessageListener(new MonitoredSubscriber.MonitorMessageListener("MonitorListener " + System.currentTimeMillis()));

            MonitoredSubscriber._logger.info("Starting monitored subscription ...");

            MonitoredSubscriber._connection.start();

            //and now start ordinary consumption too
            subscribe(hostdetails,username,password,virtualPath,queueName);
        }
        catch (Throwable t)
        {
            _logger.error("Fatal error: " + t);
            t.printStackTrace();
        }
    }

    //stop consuming
    public void stopMonitor()
    {
        try
        {
            _monitorConsumer.close();
            _monitorConsumer = null;
            stop();
        }
        catch(JMSException j)
        {
            _logger.error("JMSException trying to Subscriber.stop: " + j.getStackTrace());
        }
    }

}
