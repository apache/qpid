

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace org.apache.qpid.client
{
    /// <summary> Implements a Qpid Sesion.</summary>
    public class ClientSession : Session
    {
        public static short TRANSFER_ACQUIRE_MODE_NO_ACQUIRE = 1;
        public static short TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE = 0;
        public static short TRANSFER_CONFIRM_MODE_REQUIRED = 0;
        public static short TRANSFER_CONFIRM_MODE_NOT_REQUIRED = 1;
        public static short MESSAGE_FLOW_MODE_CREDIT = 0;
        public static short MESSAGE_FLOW_MODE_WINDOW = 1;
        public static short MESSAGE_FLOW_UNIT_MESSAGE = 0;
        public static short MESSAGE_FLOW_UNIT_BYTE = 1;
        public static long MESSAGE_FLOW_MAX_BYTES = 0xFFFFFFFF;
        public static short MESSAGE_REJECT_CODE_GENERIC = 0;
        public static short MESSAGE_REJECT_CODE_IMMEDIATE_DELIVERY_FAILED = 1;
        public static short MESSAGE_ACQUIRE_ANY_AVAILABLE_MESSAGE = 0;
        public static short MESSAGE_ACQUIRE_MESSAGES_IF_ALL_ARE_AVAILABLE = 1;

        private Dictionary<String, IMessageListener> _listeners = new Dictionary<String, IMessageListener>();

        public ClientSession(byte[] name) : base(name)
        {
        }

        public void attachMessageListener(IMessageListener listener, string Destination)
        {
            _listeners.Add(Destination, listener);
        }

        public Dictionary<String, IMessageListener> MessageListeners
        {
            get { return _listeners; }
        }

        public void messageTransfer(String destination, string routingkey, IMessage message)
        {           
            message.DeliveryProperties.setRoutingKey(routingkey);
            messageTransfer(destination, message);
        }

        public void messageTransfer(String destination, IMessage message)
        {           
            byte[] body = new byte[message.Body.Position];
            message.Body.Seek(0, SeekOrigin.Begin);
            message.Body.Read(body, 0, body.Length);
            message.MessageProperties.setMessageId(UUID.randomUUID());
            messageTransfer(destination,
                            MessageAcceptMode.NONE,
                            MessageAcquireMode.PRE_ACQUIRED,
                            message.Header,
                            body);
        }

        public void queueDeclare(String queue)
        {
            queueDeclare(queue, null, null);
        }

        public void queueDeclare(String queue, params Option[] options) 
        {
            queueDeclare(queue, null, null, options);
        }

        public void exchangeBind(String queue, String exchange, String bindingKey)
        {
            exchangeBind(queue, exchange, bindingKey, null);
        }

         public void messageSubscribe(String queue)
         {
             messageSubscribe(queue, queue, MessageAcceptMode.EXPLICIT, MessageAcquireMode.PRE_ACQUIRED, null, 0, null);
             // issue credits     
             messageSetFlowMode(queue, MessageFlowMode.WINDOW);
             messageFlow(queue, MessageCreditUnit.BYTE, ClientSession.MESSAGE_FLOW_MAX_BYTES);
             messageFlow(queue, MessageCreditUnit.MESSAGE, 10000);                                
         }
             
    }
}