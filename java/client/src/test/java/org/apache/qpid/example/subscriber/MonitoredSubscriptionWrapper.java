package org.apache.qpid.example.subscriber;

import org.apache.log4j.BasicConfigurator;
import org.apache.qpid.example.shared.Statics;

import java.util.Properties;

/**
 * Allows you to simply start a monitored subscriber
 * Author: Marnie McCormack
 * Date: 08-Aug-2006
 * Time: 12:05:52
 * Copyright JPMorgan Chase 2006
 */
public class MonitoredSubscriptionWrapper {

    private static MonitoredSubscriber _subscriber;

    public static void main(String args[])
    {
        //switch on logging
        BasicConfigurator.configure();

        _subscriber = new MonitoredSubscriber();

        //using system props but can replace with app appropriate config here
        Properties props = System.getProperties();

        //note that for failover should set -Dhost=host1:port1;host2:port2
        //Client will then failover in order i.e. connect to first host and failover to second and so on
        _subscriber.subscribe(props.getProperty(Statics.HOST_PROPERTY),
                                props.getProperty(Statics.USER_PROPERTY), props.getProperty(Statics.PWD_PROPERTY),
                                props.getProperty(Statics.VIRTUAL_PATH_PROPERTY), props.getProperty(Statics.QUEUE_NAME));
    }

    //Stop subscribing now ...
    public static void stop()
    {
        _subscriber.stop();
    }
}
