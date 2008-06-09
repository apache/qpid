// This header file is just for doxygen documentation purposes.

/** \mainpage Qpid C++ Developer Kit.
 *
 * The <a href=http://incubator.apache.org/qpid>Qpid project</a> provides implementations of the <a href="http://amqp.org/">AMQP messaging specification</a> in several programming language.
 * 
 * Qpidc provides APIs and libraries to implement AMQP clients in
 * C++. Qpidc clients can interact with any compliant AMQP message
 * broker. The Qpid project also provides an AMQP broker daemon called
 * qpidd that you can use with your qpidc clients.
 *
 * See the \ref clientapi "client API reference" to get started.
 *
 */


/**
 * \defgroup clientapi Application API for an AMQP client.
 *
 * A typical client takes the following steps:
 *  - Connect to the broker using qpid::client::Connection::open()
 *  - Create a qpid::client::Session object.
 *  
 * Once a session is created the client can work with the broker:
 *  - Create and bind queues using the qpid::client::Session commands.
 *  - Send messages using qpid::client::Session::messageTransfer.
 *  - Subscribe to queues using qpid::client::SubscriptionManager
 */
