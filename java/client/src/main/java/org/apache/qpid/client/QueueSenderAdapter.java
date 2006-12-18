package org.apache.qpid.client;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSender;

public class QueueSenderAdapter implements QueueSender {

	private MessageProducer delegate;
	private Queue queue;
	private boolean closed = false;
	
	public QueueSenderAdapter(MessageProducer msgProducer, Queue queue){
		delegate = msgProducer;
		this.queue = queue;
	}
	
	public Queue getQueue() throws JMSException {
		checkPreConditions();
		return queue;
	}

	public void send(Message msg) throws JMSException {
		checkPreConditions();
		delegate.send(msg);
	}

	public void send(Queue queue, Message msg) throws JMSException {
		checkPreConditions();
		delegate.send(queue, msg);
	}

	public void publish(Message msg, int deliveryMode, int priority, long timeToLive)
	throws JMSException {
		checkPreConditions();
		delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void send(Queue queue,Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		checkPreConditions();
		delegate.send(queue,msg, deliveryMode,priority,timeToLive);
	}
	
	public void close() throws JMSException {
		delegate.close();
		closed = true;
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
		checkPreConditions();
		delegate.send(dest,msg);
	}

	public void send(Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		checkPreConditions();
		delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void send(Destination dest, Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException {
		checkPreConditions();
		delegate.send(dest,msg, deliveryMode,priority,timeToLive);
	}

	public void setDeliveryMode(int deliveryMode) throws JMSException {
		checkPreConditions();
		delegate.setDeliveryMode(deliveryMode);
	}

	public void setDisableMessageID(boolean disableMessageID) throws JMSException {
		checkPreConditions();
		delegate.setDisableMessageID(disableMessageID);
	}

	public void setDisableMessageTimestamp(boolean disableMessageTimestamp) throws JMSException {
		checkPreConditions();
		delegate.setDisableMessageTimestamp(disableMessageTimestamp);
	}

	public void setPriority(int priority) throws JMSException {
		checkPreConditions();
		delegate.setPriority(priority);
	}

	public void setTimeToLive(long timeToLive) throws JMSException {
		checkPreConditions();
		delegate.setTimeToLive(timeToLive);
	}
	
	private void checkPreConditions() throws IllegalStateException, IllegalStateException {
		if (closed){
			throw new javax.jms.IllegalStateException("Publisher is closed");
		}
		
		if(queue == null){
			throw new UnsupportedOperationException("Queue is null");
		}
		
		AMQSession session = ((BasicMessageProducer)delegate).getSession();
		
		if(session == null || session.isClosed()){
			throw new UnsupportedOperationException("Invalid Session");
		}
	}
}
