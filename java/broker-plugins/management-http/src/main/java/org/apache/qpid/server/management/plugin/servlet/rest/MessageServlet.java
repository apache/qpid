/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.subscription.Subscription;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class MessageServlet extends AbstractServlet
{
    private static final Logger LOGGER = Logger.getLogger(MessageServlet.class);

    public MessageServlet()
    {
        super();
    }

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {

        if(request.getPathInfo() != null && request.getPathInfo().length()>0 && request.getPathInfo().substring(1).split("/").length > 2)
        {
            getMessageContent(request, response);
        }
        else
        {
            getMessageList(request, response);
        }

    }

    private void getMessageContent(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        Queue queue = getQueueFromRequest(request);
        String path[] = request.getPathInfo().substring(1).split("/");
        MessageFinder messageFinder = new MessageFinder(Long.parseLong(path[2]));
        queue.visit(messageFinder);

        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        final PrintWriter writer = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, messageFinder.getMessageObject());
    }

    private void getMessageList(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        Queue queue = getQueueFromRequest(request);

        int first = -1;
        int last = -1;
        String range = request.getHeader("Range");
        if(range != null)
        {
            String[] boundaries = range.split("=")[1].split("-");
            first = Integer.parseInt(boundaries[0]);
            last = Integer.parseInt(boundaries[1]);
        }
        final MessageCollector messageCollector = new MessageCollector(first, last);
        queue.visit(messageCollector);

        response.setContentType("application/json");
        final List<Map<String, Object>> messages = messageCollector.getMessages();
        int queueSize = ((Number) queue.getStatistics().getStatistic(Queue.QUEUE_DEPTH_MESSAGES)).intValue();
        String min = messages.isEmpty() ? "0" : messages.get(0).get("position").toString();
        String max = messages.isEmpty() ? "0" : messages.get(messages.size()-1).get("position").toString();
        response.setHeader("Content-Range", (min + "-" + max + "/" + queueSize));
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        final PrintWriter writer = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, messages);
    }

    private Queue getQueueFromRequest(HttpServletRequest request)
    {
        List<String> names = new ArrayList<String>();
        // TODO - validation that there is a vhost and queue and only those in the path
        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            names.addAll(Arrays.asList(path.split("/")));
        }
        String vhostName = names.get(0);
        String queueName = names.get(1);

        VirtualHost vhost = null;

        for(VirtualHost vh : getBroker().getVirtualHosts())
        {
            if(vh.getName().equals(vhostName))
            {
                vhost = vh;
                break;
            }
        }

        return getQueueFromVirtualHost(queueName, vhost);
    }

    private Queue getQueueFromVirtualHost(String queueName, VirtualHost vhost)
    {
        Queue queue = null;

        for(Queue q : vhost.getQueues())
        {

            if(q.getName().equals(queueName))
            {
                queue = q;
                break;
            }
        }
        return queue;
    }

    private abstract static class QueueEntryTransaction implements VirtualHost.TransactionalOperation
    {
        private final Queue _sourceQueue;
        private final List _messageIds;

        protected QueueEntryTransaction(Queue sourceQueue, List messageIds)
        {
            _sourceQueue = sourceQueue;
            _messageIds = messageIds;
        }


        public void withinTransaction(final VirtualHost.Transaction txn)
        {

            _sourceQueue.visit(new QueueEntryVisitor()
            {

                public boolean visit(final QueueEntry entry)
                {
                    final ServerMessage message = entry.getMessage();
                    if(message != null)
                    {
                        final long messageId = message.getMessageNumber();
                        if (_messageIds.remove(messageId) || (messageId <= (long) Integer.MAX_VALUE
                                                              && _messageIds.remove(Integer.valueOf((int)messageId))))
                        {
                            updateEntry(entry, txn);
                        }
                    }
                    return _messageIds.isEmpty();
                }
            });
        }


        protected abstract void updateEntry(QueueEntry entry, VirtualHost.Transaction txn);
    }

    private static class MoveTransaction extends QueueEntryTransaction
    {
        private final Queue _destinationQueue;

        public MoveTransaction(Queue sourceQueue, List<Long> messageIds, Queue destinationQueue)
        {
            super(sourceQueue, messageIds);
            _destinationQueue = destinationQueue;
        }

        protected void updateEntry(QueueEntry entry, VirtualHost.Transaction txn)
        {
            txn.move(entry, _destinationQueue);
        }
    }

    private static class CopyTransaction extends QueueEntryTransaction
    {
        private final Queue _destinationQueue;

        public CopyTransaction(Queue sourceQueue, List<Long> messageIds, Queue destinationQueue)
        {
            super(sourceQueue, messageIds);
            _destinationQueue = destinationQueue;
        }

        protected void updateEntry(QueueEntry entry, VirtualHost.Transaction txn)
        {
            txn.copy(entry, _destinationQueue);
        }
    }

    private static class DeleteTransaction extends QueueEntryTransaction
    {
        public DeleteTransaction(Queue sourceQueue, List<Long> messageIds)
        {
            super(sourceQueue, messageIds);
        }

        protected void updateEntry(QueueEntry entry, VirtualHost.Transaction txn)
        {
            txn.dequeue(entry);
        }
    }



    private class MessageCollector implements QueueEntryVisitor
    {
        private final int _first;
        private final int _last;
        private int _position = -1;
        private final List<Map<String, Object>> _messages = new ArrayList<Map<String, Object>>();

        private MessageCollector(int first, int last)
        {
            _first = first;
            _last = last;
        }


        public boolean visit(QueueEntry entry)
        {

            _position++;
            if((_first == -1 || _position >= _first) && (_last == -1 || _position <= _last))
            {
                final Map<String, Object> messageObject = convertToObject(entry, false);
                messageObject.put("position", _position);
                _messages.add(messageObject);
            }
            return _last != -1 && _position > _last;
        }

        public List<Map<String, Object>> getMessages()
        {
            return _messages;
        }
    }


    private class MessageFinder implements QueueEntryVisitor
    {
        private final long _messageNumber;
        private Map<String, Object> _messageObject;

        private MessageFinder(long messageNumber)
        {
            _messageNumber = messageNumber;
        }


        public boolean visit(QueueEntry entry)
        {
            ServerMessage message = entry.getMessage();
            if(message != null)
            {
                if(_messageNumber == message.getMessageNumber())
                {
                    MessageReference reference = message.newReference();
                    _messageObject = convertToObject(entry, true);
                    reference.release();
                    return true;
                }
            }
            return false;
        }

        public Map<String, Object> getMessageObject()
        {
            return _messageObject;
        }
    }

    private Map<String, Object> convertToObject(QueueEntry entry, boolean includeContent)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("size", entry.getSize());
        object.put("deliveryCount", entry.getDeliveryCount());
        object.put("state",entry.isAvailable()
                                   ? "Available"
                                   : entry.isAcquired()
                                             ? "Acquired"
                                             : "");
        final Subscription deliveredSubscription = entry.getDeliveredSubscription();
        object.put("deliveredTo", deliveredSubscription == null ? null : deliveredSubscription.getSubscriptionID());
        ServerMessage message = entry.getMessage();

        if(message != null)
        {
            convertMessageProperties(object, message);
            if(includeContent)
            {
                convertMessageHeaders(object, message);
            }
        }

        return object;
    }

    private void convertMessageProperties(Map<String, Object> object, ServerMessage message)
    {
        object.put("id", message.getMessageNumber());
        object.put("arrivalTime",message.getArrivalTime());
        object.put("persistent", message.isPersistent());

        final AMQMessageHeader messageHeader = message.getMessageHeader();
        if(messageHeader != null)
        {
            addIfPresent(object, "messageId", messageHeader.getMessageId());
            addIfPresent(object, "expirationTime", messageHeader.getExpiration());
            addIfPresent(object, "applicationId", messageHeader.getAppId());
            addIfPresent(object, "correlationId", messageHeader.getCorrelationId());
            addIfPresent(object, "encoding", messageHeader.getEncoding());
            addIfPresent(object, "mimeType", messageHeader.getMimeType());
            addIfPresent(object, "priority", messageHeader.getPriority());
            addIfPresent(object, "replyTo", messageHeader.getReplyTo());
            addIfPresent(object, "timestamp", messageHeader.getTimestamp());
            addIfPresent(object, "type", messageHeader.getType());
            addIfPresent(object, "userId", messageHeader.getUserId());
        }

    }

    private void addIfPresent(Map<String, Object> object, String name, Object property)
    {
        if(property != null)
        {
            object.put(name, property);
        }
    }

    private void convertMessageHeaders(Map<String, Object> object, ServerMessage message)
    {
        final AMQMessageHeader messageHeader = message.getMessageHeader();
        if(messageHeader != null)
        {
            Map<String, Object> headers = new HashMap<String,Object>();
            for(String headerName : messageHeader.getHeaderNames())
            {
                headers.put(headerName, messageHeader.getHeader(headerName));
            }
            object.put("headers", headers);
        }
    }

    /*
     * POST moves or copies messages to the given queue from a queue specified in the posted JSON data
     */
    @Override
    protected void doPostWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {

        try
        {
            final Queue sourceQueue = getQueueFromRequest(request);

            ObjectMapper mapper = new ObjectMapper();

            @SuppressWarnings("unchecked")
            Map<String,Object> providedObject = mapper.readValue(request.getInputStream(), LinkedHashMap.class);

            String destQueueName = (String) providedObject.get("destinationQueue");
            Boolean move = (Boolean) providedObject.get("move");

            final VirtualHost vhost = sourceQueue.getParent(VirtualHost.class);

            boolean isMoveTransaction = move != null && Boolean.valueOf(move);

            // FIXME: added temporary authorization check until we introduce management layer
            // and review current ACL rules to have common rules for all management interfaces
            String methodName = isMoveTransaction? "moveMessages":"copyMessages";
            if (isQueueUpdateMethodAuthorized(methodName, vhost))
            {
                final Queue destinationQueue = getQueueFromVirtualHost(destQueueName, vhost);
                final List messageIds = new ArrayList((List) providedObject.get("messages"));
                QueueEntryTransaction txn =
                        isMoveTransaction
                                ? new MoveTransaction(sourceQueue, messageIds, destinationQueue)
                                : new CopyTransaction(sourceQueue, messageIds, destinationQueue);
                vhost.executeTransaction(txn);
                response.setStatus(HttpServletResponse.SC_OK);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Failure to perform message opertion", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }


    /*
     * DELETE removes messages from the queue
     */
    @Override
    protected void doDeleteWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response)
    {

        final Queue sourceQueue = getQueueFromRequest(request);

        final VirtualHost vhost = sourceQueue.getParent(VirtualHost.class);


        final List<Long> messageIds = new ArrayList<Long>();
        for(String idStr : request.getParameterValues("id"))
        {
            messageIds.add(Long.valueOf(idStr));
        }

        // FIXME: added temporary authorization check until we introduce management layer
        // and review current ACL rules to have common rules for all management interfaces
        if (isQueueUpdateMethodAuthorized("deleteMessages", vhost))
        {
            vhost.executeTransaction(new DeleteTransaction(sourceQueue, messageIds));
            response.setStatus(HttpServletResponse.SC_OK);
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

    }

    private boolean isQueueUpdateMethodAuthorized(String methodName, VirtualHost host)
    {
        SecurityManager securityManager = host.getSecurityManager();
        return securityManager.authoriseMethod(Operation.UPDATE, "VirtualHost.Queue", methodName);
    }

}
