

using System;
using System.Collections.Generic;
using client.client;
using org.apache.qpid.transport;

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

        private Dictionary<String, MessageListener> _listeners = new Dictionary<String, MessageListener>();

        public ClientSession(byte[] name) : base(name)
        {
        }

        public void attachMessageListener(MessageListener listener, string Destination)
        {
            _listeners.Add(Destination, listener);
        }

        public Dictionary<String, MessageListener> MessageListeners
        {
            get { return _listeners; }
        }
    }
}