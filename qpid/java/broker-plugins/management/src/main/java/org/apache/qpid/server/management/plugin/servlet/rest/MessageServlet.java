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
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.server.subscription.Subscription;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class MessageServlet extends AbstractServlet
{
    public MessageServlet(Broker broker)
    {
        super(broker);
    }

    @Override
    protected void onGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
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
        Queue queue = null;

        for(VirtualHost vh : getBroker().getVirtualHosts())
        {
            if(vh.getName().equals(vhostName))
            {
                vhost = vh;
                break;
            }
        }

        for(Queue q : vhost.getQueues())
        {
            if(q.getName().equals(queueName))
            {
                queue = q;
                break;
            }
        }

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
        response.setHeader("Content-Range", messages.isEmpty()
                                                    ? "0-0/0"
                                                    : messages.get(0).get("id").toString()
                                                      + "-"
                                                      + messages.get(messages.size()-1).get("id").toString()
                                                      + "/" + queue.getStatistics().getStatistic(Queue.QUEUE_DEPTH_MESSAGES));
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        final PrintWriter writer = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, messages);


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
                _messages.add(convertToObject(entry, _position));
            }
            return _last != -1 && _position > _last;
        }

        public List<Map<String, Object>> getMessages()
        {
            return _messages;
        }
    }

    private Map<String, Object> convertToObject(QueueEntry entry, int position)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("id", position);
        object.put("size", entry.getSize());
        object.put("deliveryCount", entry.getDeliveryCount());
        object.put("state",entry.isAvailable()
                                   ? "Available"
                                   : entry.isAcquired()
                                             ? "Acquired"
                                             : "");
        final Subscription deliveredSubscription = entry.getDeliveredSubscription();
        object.put("deliveredTo", deliveredSubscription == null ? null : deliveredSubscription.getSubscriptionID());
        final AMQMessageHeader messageHeader = entry.getMessageHeader();
        if(messageHeader != null)
        {
            object.put("messageId", messageHeader.getMessageId());
            object.put("expirationTime", messageHeader.getExpiration());
        }
        ServerMessage message = entry.getMessage();
        if(message != null)
        {
            object.put("arrivalTime",message.getArrivalTime());
            object.put("persistent", message.isPersistent());
        }
        return object;
    }
}
