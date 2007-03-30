package org.apache.qpid.nclient.amqp;

import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageOffsetBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageQosBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageRejectBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPMessage
{

	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */

	public abstract void transfer(MessageTransferBody messageTransferBody, AMQPCallBack cb) throws AMQPException;

	public abstract void consume(MessageConsumeBody messageConsumeBody, AMQPCallBack cb) throws AMQPException;

	public abstract void cancel(MessageCancelBody messageCancelBody, AMQPCallBack cb) throws AMQPException;

	public abstract void get(MessageGetBody messageGetBody, AMQPCallBack cb) throws AMQPException;

	public abstract void recover(MessageRecoverBody messageRecoverBody, AMQPCallBack cb) throws AMQPException;

	public abstract void open(MessageOpenBody messageOpenBody, AMQPCallBack cb) throws AMQPException;

	public abstract void close(MessageCloseBody messageCloseBody, AMQPCallBack cb) throws AMQPException;

	public abstract void append(MessageAppendBody messageAppendBody, AMQPCallBack cb) throws AMQPException;

	public abstract void checkpoint(MessageCheckpointBody messageCheckpointBody, AMQPCallBack cb) throws AMQPException;

	public abstract void resume(MessageResumeBody messageResumeBody, AMQPCallBack cb) throws AMQPException;

	public abstract void qos(MessageQosBody messageQosBody, AMQPCallBack cb) throws AMQPException;

	/**
	 * The correlationId from the request.
	 * For example if a message.transfer is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public abstract void ok(MessageOkBody messageOkBody, long correlationId) throws AMQPException;

	/**
	 * The correlationId from the request.
	 * For example if a message.transfer is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public abstract void reject(MessageRejectBody messageRejectBody, long correlationId) throws AMQPException;

	/**
	 * The correlationId from the request.
	 * For example if a message.resume is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public abstract void offset(MessageOffsetBody messageOffsetBody, long correlationId) throws AMQPException;

}