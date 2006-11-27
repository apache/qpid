package org.apache.qpid.client;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSender;

public class QueueSenderAdapter implements QueueSender {

	private MessageProducer delegate;
	private Queue queue;
	
	public QueueSenderAdapter(MessageProducer msgProducer, Queue queue){
		delegate = msgProducer;
		this.queue = queue;
	}
	
	public Queue getQueue() throws JMSException {
		return queue;
	}

	public void send(Message msg) throws JMSException {
		delegate.send(msg);
	}

	public void send(Queue queue, Message msg) throws JMSException {
		delegate.send(queue, msg);
	}

	public void publish(Message msg, int deliveryMode, int priority, long timeToLive)
	throws JMSException {
		
		delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void send(Queue queue,Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		
		delegate.send(queue,msg, deliveryMode,priority,timeToLive);
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
