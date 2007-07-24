package org.apache.qpid.server.exchange.synapse;

import org.apache.axis2.addressing.EndpointReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.utils.EndpointDefinition;
import org.apache.synapse.statistics.StatisticsCollector;

public class QpidSynapseEnvironment implements SynapseEnvironment
{

	private static final Log log = LogFactory.getLog(QpidSynapseEnvironment.class);

	private SynapseConfiguration synapseConfig;

	private StatisticsCollector statisticsCollector;

	private SynapseExchange qpidExchange;
	
	public QpidSynapseEnvironment(SynapseConfiguration synapseConfig, SynapseExchange qpidExchange)
	{
		this.synapseConfig = synapseConfig;
		this.qpidExchange = qpidExchange;
	}

	public MessageContext createMessageContext()
	{
		org.apache.axis2.context.MessageContext axis2MC = new org.apache.axis2.context.MessageContext();
		MessageContext mc = new Axis2MessageContext(axis2MC, synapseConfig, this);
		return mc;
	}

	public StatisticsCollector getStatisticsCollector()
	{
		return statisticsCollector;
	}

	public void injectMessage(MessageContext synCtx)
	{

		synCtx.getMainSequence().mediate(synCtx);
	}

	public void send(EndpointDefinition endpoint, MessageContext smc)
	{
		if(endpoint != null)
		{
			smc.setTo(new EndpointReference(endpoint.getAddress()));
			AMQMessage newMessage = MessageContextCreatorForQpid.getAMQMessage(smc);
			try
			{	
				qpidExchange.getExchangeRegistry().routeContent(newMessage);
			}
			catch(Exception e)
			{
				throw new SynapseException("Faulty endpoint",e);
			}
		}	
		
	}

	public void setStatisticsCollector(StatisticsCollector statisticsCollector)
	{
		this.statisticsCollector = statisticsCollector;
	}

}
