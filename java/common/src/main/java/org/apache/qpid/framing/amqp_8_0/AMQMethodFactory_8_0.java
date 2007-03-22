package org.apache.qpid.framing.amqp_8_0;

import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;

import org.apache.mina.common.ByteBuffer;


public class AMQMethodFactory_8_0 implements AMQMethodFactory
{
    private static final AMQShortString CLIENT_INITIATED_CONNECTION_CLOSE =
            new AMQShortString("Client initiated connection close");

    public ConnectionCloseBody createConnectionClose()
    {
        return new ConnectionCloseBodyImpl(AMQConstant.REPLY_SUCCESS.getCode(),
                                           CLIENT_INITIATED_CONNECTION_CLOSE,
                                           0,
                                           0);
    }

    public AccessRequestBody createAccessRequest(boolean active, boolean exclusive, boolean passive, boolean read, AMQShortString realm, boolean write)
    {
        return new AccessRequestBodyImpl(realm,exclusive,passive,active,write,read);
    }

    public TxSelectBody createTxSelect()
    {
        return new TxSelectBodyImpl();
    }

    public TxCommitBody createTxCommit()
    {
        return new TxCommitBodyImpl();
    }

    public TxRollbackBody createTxRollback()
    {
        return new TxRollbackBodyImpl();
    }

    public ChannelOpenBody createChannelOpen()
    {
        return new ChannelOpenBodyImpl((AMQShortString)null);
    }

    public ChannelCloseBody createChannelClose(int replyCode, AMQShortString replyText)
    {
        return new ChannelCloseBodyImpl(replyCode, replyText, 0, 0);
    }

    public ExchangeDeclareBody createExchangeDeclare(AMQShortString name, AMQShortString type, int ticket)
    {
        return new ExchangeDeclareBodyImpl(ticket,name,type,false,false,false,false,false,null);  
    }

    public ExchangeBoundBody createExchangeBound(AMQShortString exchangeName, AMQShortString queueName, AMQShortString routingKey)
    {
        return new ExchangeBoundBodyImpl(exchangeName,routingKey,queueName);
    }

    public QueueDeclareBody createQueueDeclare(AMQShortString name, FieldTable arguments, boolean autoDelete, boolean durable, boolean exclusive, boolean passive, int ticket)
    {
        return new QueueDeclareBodyImpl(ticket,name,passive,durable,exclusive,autoDelete,false,arguments);
    }

    public QueueBindBody createQueueBind(AMQShortString queueName, AMQShortString exchangeName, AMQShortString routingKey, FieldTable arguments, int ticket)
    {
        return new QueueBindBodyImpl(ticket,queueName,exchangeName,routingKey,false,arguments);
    }

    public QueueDeleteBody createQueueDelete(AMQShortString queueName, boolean ifEmpty, boolean ifUnused, int ticket)
    {
        return new QueueDeleteBodyImpl(ticket,queueName,ifUnused,ifEmpty,false);
    }

    public ChannelFlowBody createChannelFlow(boolean active)
    {
        return new ChannelFlowBodyImpl(active);
    }


    // In different versions of the protocol we change the class used for message transfer
    // abstract this out so the appropriate methods are created
    public AMQMethodBody createRecover(boolean requeue)
    {
        return new BasicRecoverBodyImpl(requeue);
    }

    public AMQMethodBody createConsumer(AMQShortString tag, AMQShortString queueName, FieldTable arguments, boolean noAck, boolean exclusive, boolean noLocal, int ticket)
    {
        return new BasicConsumeBodyImpl(ticket,queueName,tag,noLocal,noAck,exclusive,false,arguments);
    }

    public AMQMethodBody createConsumerCancel(AMQShortString consumerTag)
    {
        return new BasicCancelBodyImpl(consumerTag, false);
    }

    public AMQMethodBody createAcknowledge(long deliveryTag, boolean multiple)
    {
        return new BasicAckBodyImpl(deliveryTag,multiple);
    }

    public AMQMethodBody createRejectBody(long deliveryTag, boolean requeue)
    {
        return new BasicRejectBodyImpl(deliveryTag, requeue);
    }

    public AMQMethodBody createMessageQos(int prefetchCount, int prefetchSize)
    {
        return new BasicQosBodyImpl(prefetchSize, prefetchCount, false);
    }



}
