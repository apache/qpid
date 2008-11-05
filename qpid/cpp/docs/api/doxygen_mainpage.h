// This header file is just for doxygen documentation purposes.

/** \mainpage Qpid C++ API Reference
 *
 * <h2>Messaging Client API classes</h2>
 * <ul>
 * <li><p>\ref clientapi</p></li>
 * </ul>
 * 
 * <h2>Code for common tasks</h2>
 *
 * <ul><li><p>Includes and Namespaces</p>
 * <pre> \#include <qpid/client/Connection.h>
 * \#include <qpid/client/Session.h>
 * \#include <qpid/client/Message.h>
 * \#include <qpid/client/SubscriptionManager.h>
 * 
 *
 * using namespace qpid::client; 
 * using namespace qpid::framing;</pre></li>
 * 
 * <li><p>Opening and closing connections and sessions</p>
 * <pre> Connection connection;
 * try {
 *    connection.open(host, port);
 *    Session session =  connection.newSession();
 *    ...
 *    connection.close();
 *    return 0;
 * } catch(const std::exception& error) {
 *    std::cout << error.what() << std::endl;
 * }
 * return 1;</pre>
 *
 *
 * <li><p>Declaring and binding queues:</p>
 *
 * <pre> session.queueDeclare(arg::queue="message_queue");
 * session.exchangeBind(arg::exchange="amq.direct", arg::queue="message_queue", arg::bindingKey="routing_key");</pre></li>
 *
 * <li><p>Sending a message:</p>
 *
 * <pre> message.getDeliveryProperties().setRoutingKey("routing_key"); 
 * message.setData("Hi, Mom!");
 * session.messageTransfer(arg::content=message,  arg::destination="amq.direct");</pre></li>
 *
 * <li><p>Sending a message (asynchronous):</p>
 *
 * <pre> ##include <qpid/client/AsyncSession.h>
 * async(session).messageTransfer(arg::content=message,  arg::destination="amq.direct");
 *  ...
 * session.sync();</pre></li>
 * 
 *
 * <li><p>Replying to a message:</p>
 * <pre> Message request, response; 
 * ...
 * if (request.getMessageProperties().hasReplyTo()) {
 *    string routingKey = request.getMessageProperties().getReplyTo().getRoutingKey();
 *    string exchange = request.getMessageProperties().getReplyTo().getExchange();
 *    response.getDeliveryProperties().setRoutingKey(routingKey);
 *    messageTransfer(arg::content=response, arg::destination=exchange);
 * } 
 * </pre></li>
 *
 * <li><p>A message listener:</p>
 *
 * <pre> class Listener : public MessageListener{
 *  private:
 *    SubscriptionManager& subscriptions;
 *  public:
 *    Listener(SubscriptionManager& subscriptions);
 *    virtual void received(Message& message);
 * };
 *
 * void Listener::received(Message& message) {
 *    std::cout << "Message: " << message.getData() << std::endl;
 *    if (endCondition(message)) {
 *       subscriptions.cancel(message.getDestination());
 *    }
 * }</pre></li>
 *
 * <li><p>Using a message listener with a subscription manager:</p>
 *
 * <pre> SubscriptionManager subscriptions(session);
 *
 * Listener listener(subscriptions);
 * subscriptions.subscribe(listener, "message_queue");
 * subscriptions.run();</pre></li>
 *
 * <li><p>Using a LocalQueue with a subscription manager</p>
 *
 * <pre> SubscriptionManager subscriptions(session);
 *   
 * LocalQueue local_queue;
 * subscriptions.subscribe(local_queue, string("message_queue"));
 *  
 * Message message;
 * for (int i=0; i<10; i++) {
 *    local_queue.get(message, 10000);
 *    std::cout << message.getData() << std::endl;
 * }</pre></li><ul>
 *
 *
 */

/**
 * \defgroup clientapi Qpid C++ Client API
 *
 */
