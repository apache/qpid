package org.apache.qpid.client;

import org.apache.qpid.AMQException;

import javax.jms.Topic;

public interface AMQTopicSubscriber
{

    void addBindingKey(Topic topic, String bindingKey) throws AMQException;
}
