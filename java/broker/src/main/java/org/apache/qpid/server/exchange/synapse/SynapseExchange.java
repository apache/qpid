package org.apache.qpid.server.exchange.synapse;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.AbstractExchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.synapse.Constants;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.SynapseConfigurationBuilder;
import org.apache.synapse.core.SynapseEnvironment;

public class SynapseExchange extends AbstractExchange
{

	public final static AMQShortString TYPE = new AMQShortString("synapse"); 
	
	private SynapseEnvironment synEnv;
	
	private ExchangeRegistry exchangeRegistry;

	public SynapseExchange()
	{
		super();
	}

	@Override
	public void initialise(VirtualHost host, AMQShortString name, boolean durable, int ticket, boolean autoDelete, ExchangeRegistry exchangeRegistry) throws AMQException
	{
		super.initialise(host, name, durable, ticket, autoDelete, exchangeRegistry);
		
		String config = System.getProperty(Constants.SYNAPSE_XML);
		SynapseConfiguration synapseConfiguration = SynapseConfigurationBuilder.getConfiguration(config);
		synEnv = new QpidSynapseEnvironment(synapseConfiguration,this);
		MessageContextCreatorForQpid.setSynConfig(synapseConfiguration);
		MessageContextCreatorForQpid.setSynEnv(synEnv);
		this.exchangeRegistry = exchangeRegistry;
	}

	@Override
	protected ExchangeMBean createMBean() throws AMQException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public void deregisterQueue(AMQShortString routingKey, AMQQueue queue) throws AMQException
	{
		throw new UnsupportedOperationException("This exchange does not take bindings");
	}

	public AMQShortString getType()
	{
		return TYPE;
	}

	public boolean hasBindings() throws AMQException
	{		
		return false;
	}

	public boolean isBound(AMQShortString routingKey, AMQQueue queue) throws AMQException
	{
		throw new UnsupportedOperationException("This exchange does not take bindings");
	}

	public boolean isBound(AMQShortString routingKey) throws AMQException
	{
		throw new UnsupportedOperationException("This exchange does not take bindings");
	}

	public boolean isBound(AMQQueue queue) throws AMQException
	{
		throw new UnsupportedOperationException("This exchange does not take bindings");
	}

	public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
	{
		throw new UnsupportedOperationException("This exchange does not take bindings");
	}
	
	public void route(AMQMessage message) throws AMQException
	{
		try
		{
			MessageContext mc = MessageContextCreatorForQpid.getSynapseMessageContext(message);
			synEnv.injectMessage(mc);			
		}
		catch(Exception e)
		{
			throw new AMQException("Error occurred while trying to mediate message through Synapse",e);
		}
	}
	
	public ExchangeRegistry getExchangeRegistry()
	{
		return exchangeRegistry;
	}

}
