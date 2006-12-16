package org.apache.qpid.client;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSender;

public class QueueSenderAdapter implements QueueSender {

	private MessageProducer _delegate;
	private Queue _queue;
	private boolean closed = false;
	
	public QueueSenderAdapter(MessageProducer msgProducer, Queue queue){
		_delegate = msgProducer;
		_queue = queue;
	}
	
	public Queue getQueue() throws JMSException {
		checkPreConditions();
		return _queue;
	}

	public void send(Message msg) throws JMSException {
		checkPreConditions();
		_delegate.send(msg);
	}

	public void send(Queue queue, Message msg) throws JMSException {
		checkPreConditions(queue);
		_delegate.send(queue, msg);
	}

	public void publish(Message msg, int deliveryMode, int priority, long timeToLive)
	throws JMSException {
		checkPreConditions();
		_delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void send(Queue queue,Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		checkPreConditions(queue);
		_delegate.send(queue,msg, deliveryMode,priority,timeToLive);
	}
	
	public void close() throws JMSException {
		_delegate.close();
		closed = true;
	}

	public int getDeliveryMode() throws JMSException {
		checkPreConditions();
		return _delegate.getDeliveryMode();
	}

	public Destination getDestination() throws JMSException {
		checkPreConditions();
		return _delegate.getDestination();
	}

	public boolean getDisableMessageID() throws JMSException {
		checkPreConditions();
		return _delegate.getDisableMessageID();
	}

	public boolean getDisableMessageTimestamp() throws JMSException {
		checkPreConditions();
		return _delegate.getDisableMessageTimestamp();
	}

	public int getPriority() throws JMSException {
		checkPreConditions();
		return _delegate.getPriority();
	}

	public long getTimeToLive() throws JMSException {
		checkPreConditions();
		return _delegate.getTimeToLive();
	}

	public void send(Destination dest, Message msg) throws JMSException {
		checkPreConditions((Queue)dest);
		_delegate.send(dest,msg);
	}

	public void send(Message msg, int deliveryMode, int priority, long timeToLive)
			throws JMSException {
		checkPreConditions();
		_delegate.send(msg, deliveryMode,priority,timeToLive);
	}

	public void send(Destination dest, Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException {
		checkPreConditions((Queue)dest);
		_delegate.send(dest,msg, deliveryMode,priority,timeToLive);
	}

	public void setDeliveryMode(int deliveryMode) throws JMSException {
		checkPreConditions();
		_delegate.setDeliveryMode(deliveryMode);
	}

	public void setDisableMessageID(boolean disableMessageID) throws JMSException {
		checkPreConditions();
		_delegate.setDisableMessageID(disableMessageID);
	}

	public void setDisableMessageTimestamp(boolean disableMessageTimestamp) throws JMSException {
		checkPreConditions();
		_delegate.setDisableMessageTimestamp(disableMessageTimestamp);
	}

	public void setPriority(int priority) throws JMSException {
		checkPreConditions();
		_delegate.setPriority(priority);
	}

	public void setTimeToLive(long timeToLive) throws JMSException {
		checkPreConditions();
		_delegate.setTimeToLive(timeToLive);
	}

    private void checkPreConditions() throws IllegalStateException, IllegalStateException
    {
        checkPreConditions(_queue);
    }

    private void checkPreConditions(Queue queue) throws IllegalStateException, IllegalStateException {
		if (closed){
			throw new javax.jms.IllegalStateException("Publisher is closed");
		}
		
		if(queue == null){
			throw new UnsupportedOperationException("Queue is null");
		}
		
		AMQSession session = ((BasicMessageProducer) _delegate).getSession();
		
		if(session == null || session.isClosed()){
			throw new javax.jms.IllegalStateException("Invalid Session");
		}
	}
}
