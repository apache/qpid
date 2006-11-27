package org.apache.qpid.client;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

public class TopicPublisherAdapter implements TopicPublisher {

	private MessageProducer delegate;
	private Topic topic;
	
	public TopicPublisherAdapter(MessageProducer msgProducer, Topic topic){
		delegate = msgProducer;
		this.topic = topic;
	}
	
	public Topic getTopic() throws JMSException {
		return topic;
	}

	public void publish(Message msg) throws JMSException {
		delegate.send(msg);
	}

	public void publish(Topic topic, Message msg) throws JMSException {
		delegate.send(topic,msg);
	}

	public void publish(Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void publish(Topic topic, Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		delegate.send(topic,msg, deliveryMode,priority,timeToLive);
	}

	public void close() throws JMSException {
		delegate.close();
	}

	public int getDeliveryMode() throws JMSException {
		return delegate.getDeliveryMode();
	}

	public Destination getDestination() throws JMSException {
		return delegate.getDestination();
	}

	public boolean getDisableMessageID() throws JMSException {
		return delegate.getDisableMessageID();
	}

	public boolean getDisableMessageTimestamp() throws JMSException {
		return delegate.getDisableMessageTimestamp();
	}

	public int getPriority() throws JMSException {
		return delegate.getPriority();
	}

	public long getTimeToLive() throws JMSException {
		return delegate.getTimeToLive();
	}

	public void send(Message msg) throws JMSException {
		delegate.send(msg);
	}

	public void send(Destination dest, Message msg) throws JMSException {
		delegate.send(dest,msg);
	}

	public void send(Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void send(Destination dest, Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException {
		delegate.send(dest,msg, deliveryMode,priority,timeToLive);
	}

	public void setDeliveryMode(int deliveryMode) throws JMSException {
		delegate.setDeliveryMode(deliveryMode);
	}

	public void setDisableMessageID(boolean disableMessageID) throws JMSException {
		delegate.setDisableMessageID(disableMessageID);
	}

	public void setDisableMessageTimestamp(boolean disableMessageTimestamp) throws JMSException {
		delegate.setDisableMessageTimestamp(disableMessageTimestamp);
	}

	public void setPriority(int priority) throws JMSException {
		delegate.setPriority(priority);
	}

	public void setTimeToLive(long timeToLive) throws JMSException {
		delegate.setTimeToLive(timeToLive);
	}

}
