package org.apache.qpid.server.exchange;

import org.apache.log4j.Logger;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;

import javax.management.openmbean.*;
import javax.management.JMException;
import javax.management.MBeanException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class FanoutExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(FanoutExchange.class);

    /**
     * Maps from queue name to queue instances
     */
    private final CopyOnWriteArraySet<AMQQueue> _queues = new CopyOnWriteArraySet<AMQQueue>();

    /**
     * MBean class implementing the management interfaces.
     */
    @MBeanDescription("Management Bean for Fanout Exchange")
    private final class FanoutExchangeMBean extends ExchangeMBean
    {
        // open mbean data types for representing exchange bindings
        private String[]   _bindingItemNames = {"Routing Key", "Queue Names"};
        private String[]   _bindingItemIndexNames = {_bindingItemNames[0]};
        private OpenType[] _bindingItemTypes = new OpenType[2];
        private CompositeType _bindingDataType = null;
        private TabularType _bindinglistDataType = null;
        private TabularDataSupport _bindingList = null;

        @MBeanConstructor("Creates an MBean for AMQ fanout exchange")
        public FanoutExchangeMBean()  throws JMException
        {
            super();
            _exchangeType = "fanout";
            init();
        }

        /**
         * initialises the OpenType objects.
         */
        private void init() throws OpenDataException
        {
            _bindingItemTypes[0] = SimpleType.STRING;
            _bindingItemTypes[1] = new ArrayType(1, SimpleType.STRING);
            _bindingDataType = new CompositeType("Exchange Binding", "Routing key and Queue names",
                                                 _bindingItemNames, _bindingItemNames, _bindingItemTypes);
            _bindinglistDataType = new TabularType("Exchange Bindings", "Exchange Bindings for " + getName(),
                                                 _bindingDataType, _bindingItemIndexNames);
        }

        public TabularData bindings() throws OpenDataException
        {

            _bindingList = new TabularDataSupport(_bindinglistDataType);

            for (AMQQueue queue : _queues)
            {
                String queueName = queue.getName().toString();



                Object[] bindingItemValues = {queueName, new String[] {queueName}};
                CompositeData bindingData = new CompositeDataSupport(_bindingDataType, _bindingItemNames, bindingItemValues);
                _bindingList.put(bindingData);
            }

            return _bindingList;
        }

        public void createNewBinding(String queueName, String binding) throws JMException
        {
            AMQQueue queue = getQueueRegistry().getQueue(new AMQShortString(queueName));
            if (queue == null)
            {
                throw new JMException("Queue \"" + queueName + "\" is not registered with the exchange.");
            }

            try
            {
                registerQueue(new AMQShortString(binding), queue, null);
                queue.bind(new AMQShortString(binding), FanoutExchange.this);
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex);
            }
        }

    }// End of MBean class


    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new FanoutExchange.FanoutExchangeMBean();
        }
        catch (JMException ex)
        {
            _logger.error("Exception occured in creating the direct exchange mbean", ex);
            throw new AMQException("Exception occured in creating the direct exchange mbean", ex);
        }
    }

    public AMQShortString getType()
    {
        return ExchangeDefaults.FANOUT_EXCHANGE_CLASS;
    }

    public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;

        if (_queues.contains(queue))
        {
            _logger.debug("Queue " + queue + " is already registered");
        }
        else
        {
            _queues.add(queue);
            _logger.debug("Binding queue " + queue + " with routing key " + routingKey + " to exchange " + this);
        }
    }

    public void deregisterQueue(AMQShortString routingKey, AMQQueue queue) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;

        if (!_queues.remove(queue))
        {
            throw new AMQException("Queue " + queue + " was not registered with exchange " + this.getName() +
                                   ". ");
        }
    }

    public void route(AMQMessage payload) throws AMQException
    {
        final BasicPublishBody publishBody = payload.getPublishBody();
        final AMQShortString routingKey = publishBody.routingKey;
        if (_queues == null || _queues.isEmpty())
        {
            String msg = "No queues bound to " + this;
            if (publishBody.mandatory)
            {
                throw new NoRouteException(msg, payload);
            }
            else
            {
                _logger.warn(msg);
            }
        }
        else
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Publishing message to queue " + _queues);
            }

            for (AMQQueue q : _queues)
            {
                payload.enqueue(q);
            }
        }
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue) throws AMQException
    {
        return _queues.contains(queue);
    }

    public boolean isBound(AMQShortString routingKey) throws AMQException
    {

        return _queues != null && !_queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue) throws AMQException
    {


        return _queues.contains(queue);
    }

    public boolean hasBindings() throws AMQException
    {
        return !_queues.isEmpty();
    }
}
