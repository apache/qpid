using System;
using System.Collections.Generic;
using org.apache.qpid.transport;

namespace org.apache.qpid.client
{
    public interface IClientSession : ISession
    {
        void AttachMessageListener(IMessageListener listener, string Destination);
        Dictionary<String, IMessageListener> MessageListeners { get; }
        void MessageTransfer(String destination, string routingkey, IMessage message);
        void MessageTransfer(String destination, IMessage message);
        void QueueDeclare(String queue);
        void QueueDeclare(String queue, params Option[] options);
        void ExchangeBind(String queue, String exchange, String bindingKey);
        void MessageSubscribe(String queue);
    }
}
