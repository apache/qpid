package org.apache.qpid.jms;

import org.apache.qpid.AMQException;

import javax.jms.Topic;

public interface TopicSubscriber extends javax.jms.TopicSubscriber
{

    void addBindingKey(Topic topic, String bindingKey) throws AMQException;
}
