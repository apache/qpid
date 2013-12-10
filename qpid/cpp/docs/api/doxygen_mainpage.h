/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */

// This header file is just for doxygen documentation purposes.

/** \mainpage Qpid C++ API Reference
 *
 * <h2>Messaging Client API classes</h2>
 * <ul>
 * <li><p>\ref messaging</p></li>
 * <li><p>\ref qmfapi</p></li>
 * </ul>
 * 
 * <h2>Code for common tasks</h2>
 *
 * 
 * <h3>Includes and Namespaces</h3>
 * 
 * <pre>
 * #include <qpid/messaging/Connection.h>
 * #include <qpid/messaging/Message.h>
 * #include <qpid/messaging/Receiver.h>
 * #include <qpid/messaging/Sender.h>
 * #include <qpid/messaging/Session.h>
 * 
 * #include <iostream>
 * 
 * using namespace qpid::messaging;
 * </pre>
 * 
 * <h3>Opening Sessions and Connections</h3>
 * 
 * <pre>
 * int main(int argc, char** argv) {
 *     std::string broker = argc > 1 ? argv[1] : "localhost:5672";
 *     std::string address = argc > 2 ? argv[2] : "amq.topic";
 *     Connection connection(broker);
 *     try {
 *         connection.open();
 *         Session session = connection.createSession();
 * 
 * 	// ### Your Code Here ###
 *         
 *         connection.close();
 *         return 0;
 *     } catch(const std::exception& error) {
 *         std::cerr << error.what() << std::endl;
 *         connection.close();
 *         return 1;   
 *     }
 * }
 * </pre>
 * 
 * <h3>Creating and Sending a Message</h3>
 * 
 * <pre>
 * Sender sender = session.createSender(address);
 * sender.send(Message("Hello world!"));
 * </pre>
 * 
 * <h3>Setting Message Content</h3>
 * 
 * <pre>
 * Message message;
 * message.setContent("Hello world!");
 * 
 * // In some applications, you should also set the content type,
 * // which is a MIME type
 * message.setContentType("text/plain");
 * </pre>
 * 
 * <h3>Receiving a Message</h3>
 * 
 * <pre>
 * Receiver receiver = session.createReceiver(address);
 * Message message = receiver.fetch(Duration::SECOND * 1); // timeout is optional
 * session.acknowledge(); // acknowledge message receipt
 * std::cout << message.getContent() << std::endl;
 * </pre>
 * 
 * <h3>Receiving Messages from Multiple Sources</h3>
 * 
 * To receive messages from multiple sources, create a receiver for each
 * source, and use session.nextReceiver().fetch() to fetch messages.
 * session.nextReceiver() is guaranteed to return the receiver
 * responsible for the first available message on the session.
 * 
 * <pre>
 * Receiver receiver1 = session.createReceiver(address1);
 * Receiver receiver2 = session.createReceiver(address2);
 * 
 * Message message =  session.nextReceiver().fetch();
 * session.acknowledge(); // acknowledge message receipt
 * std::cout << message.getContent() << std::endl;
 * </pre>
 * 
 * <h3>Replying to a message:</h3>
 * 
 * <pre>
 * // Server creates a service queue and waits for messages
 * // If it gets a request, it sends a response to the reply to address
 *
 * Receiver receiver = session.createReceiver("service_queue; {create: always}");
 * Message request = receiver.fetch();
 * const Address&amp; address = request.getReplyTo(); // Get "reply-to" from request ...
 * if (address) {
 *   Sender sender = session.createSender(address); // ... send response to "reply-to"
 *   Message response("pong!");
 *   sender.send(response);
 *   session.acknowledge();
 * }
 *
 *
 * // Client creates a private response queue - the # gets converted
 * // to a unique string for the response queue name. Client uses the
 * // name of this queue as its reply-to.
 *
 * Sender sender = session.createSender("service_queue");
 * Address responseQueue("#response-queue; {create:always, delete:always}");
 * Receiver receiver = session.createReceiver(responseQueue);
 *
 * Message request;
 * request.setReplyTo(responseQueue);
 * request.setContent("ping");
 * sender.send(request);
 * Message response = receiver.fetch();
 * std::cout << request.getContent() << " -> " << response.getContent() << std::endl;
 * </pre>
 * 
 * 
 * <h3>Getting and Setting Standard Message Properties</h3>
 * 
 * This shows some of the most commonly used message properties, it is
 * not complete.
 * 
 * <pre>
 * Message message("Hello world!");
 * message.setContentType("text/plain");
 * message.setSubject("greeting");
 * message.setReplyTo("response-queue");
 * message.setTtl(100); // milliseconds
 * message.setDurable(1);
 * 
 * std::cout << "Content: " << message.getContent() << std::endl
 *           << "Content Type: " << message.getContentType()
 *           << "Subject: " << message.getSubject()
 * 	  << "ReplyTo: " << message.getReplyTo()
 * 	  << "Time To Live (in milliseconds) " << message.getTtl()
 * 	  << "Durability: " << message.getDurable();
 * </pre>
 * 
 * <h3>Getting and Setting Application-Defined Message Properties</h3>
 * 
 * <pre>
 * std::string name = "weekday";
 * std::string value = "Thursday";
 * message.getProperties()[name] = value;
 * 
 * std:string s = message.getProperties()["weekday"];
 * </pre>
 * 
 * <h3>Transparent Failover</h3>
 * 
 * If a connection opened using the reconnect option, it will
 * transparently reconnect if the connection is lost.
 * 
 * <pre>
 * Connection connection(broker);
 * connection.setOption("reconnect", true);
 * try {
 *     connection.open();
 *     ....
 * </pre>
 * 
 *
 * <h3>Maps</h3>
 * 
 * Maps provide a simple way to exchange binary data portably, across
 * languages and platforms. Maps can contain simple types, lists, or
 * maps.
 * 
 * 
 * <pre>
 * // Sender
 * 
 * Variant::Map content;
 * content["id"] = 987654321;
 * content["name"] = "Widget";
 * content["probability"] = 0.43;
 * Variant::List colours;
 * colours.push_back(Variant("red"));
 * colours.push_back(Variant("green"));
 * colours.push_back(Variant("white"));
 * content["colours"] = colours;
 * content["uuid"] = Uuid(true);
 * 
 * Message message;
 * encode(content, message);
 * 	
 * sender.send(message);
 * </pre>
 * 
 * <pre>
 * // Receiver
 * 
 * Variant::Map content;
 * decode(receiver.fetch(), content);
 * </pre>
 *
 * <h3>Guaranteed Delivery</h3>
 * 
 * If a queue is durable, the queue survives a messaging broker crash, as
 * well as any durable messages that have been placed on the queue. These
 * messages will be delivered when the messaging broker is
 * restarted. Delivery is not guaranteed unless both the message and the
 * queue are durable.
 * 
 * <pre>
 * Sender sender = session.createSender("durable-queue");
 * 
 * Message message("Hello world!");
 * message.setDurable(1);
 * 
 * sender.send(Message("Hello world!"));
 * </pre>
 * 
 * 
 * <h3>Transactions</h3>
 * 
 * Transactions cover enqueues and dequeues.
 * 
 * When sending messages, a transaction tracks enqueues without actually
 * delivering the messages, a commit places messages on their queues, and
 * a rollback discards the enqueues.
 * 
 * When receiving messages, a transaction tracks dequeues without
 * actually removing acknowledged messages, a commit removes all
 * acknowledged messages, and a rollback discards acknowledgements. A
 * rollback does not release the message, it must be explicitly released
 * to return it to the queue.
 * 
 * <pre>
 * Connection connection(broker);
 * Session session =  connection.createTransactionalSession();
 * ...
 * if (looksOk)
 *    session.commit();
 * else 
 *    session.rollback();
 * </pre>
 *
 * <h3>Exceptions</h3>
 *
 * All exceptions for the messaging API have MessagingException as
 * their base class.

 * A common class of exception are those related to processing
 * addresses used to create senders and/or receivers. These all have
 * AddressError as their base class.
 *
 * Where there is a syntax error in the address itself, a
 * MalformedAddress will be thrown. Where the address is valid, but
 * there is an error in interpreting (i.e. resolving) it, a
 * ResolutionError - or a sub-class of it - will be thrown. If the
 * address has assertions enabled for a given context and the asserted
 * node properties are not in fact correct then AssertionFailed will
 * be thrown. If the node is not found, NotFound will be thrown.
 *
 * The loss of the underlying connection (e.g. the TCP connection)
 * results in TransportFailure being thrown. If automatic reconnect is
 * enabled, this will be caught be the library which will then try to
 * reconnect. If reconnection - as configured by the connection
 * options - fails, then TransportFailure will be thrown. This can
 * occur on any call to the messaging API.
 *
 * Sending a message may also result in an exception
 * (e.g. TargetCapacityExceeded if a queue to which the message is
 * delivered cannot enqueue it due to lack of capacity). For
 * asynchronous send the exception may not be thrown on the send
 * invocation that actually triggers it, but on a subsequent method
 * call on the API.
 *
 * Certain exceptions may render the session invalid; once these
 * occur, subsequent calls on the session will throw the same class of
 * exception. This is not an intrinsic property of the class of
 * exception, but is a result of the current mapping of the API to the
 * underlying AMQP 0-10 protocol. You can test whether the session is
 * valid at any time using the hasError() and/or checkError() methods
 * on Session.
 *
 * <h3>Logging</h3>
 * 
 * The Qpidd broker and C++ clients can both use environment variables to
 * enable logging. Use QPID_LOG_ENABLE to set the level of logging you
 * are interested in (trace, debug, info, notice, warning, error, or
 * critical):
 * 
 * <pre>
 * export QPID_LOG_ENABLE="warning+"
 * </pre>
 * 
 * Use QPID_LOG_OUTPUT to determine where logging output should be
 * sent. This is either a file name or the special values stderr, stdout,
 * or syslog:
 * 
 * <pre>
 * export QPID_LOG_TO_FILE="/tmp/myclient.out"
 * </pre>
 * 
 *
 */

/**
 * \defgroup messaging Qpid C++ Client API
 * \defgroup qmfapi Qpid Management Framework C++ API
 *
 */



