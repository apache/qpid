package org.apache.qpid.server.exchange.synapse;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;

import org.apache.axiom.attachments.ByteArrayDataSource;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.impl.llom.soap11.SOAP11Factory;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;

public class TestClassMediator implements Mediator
{

	public int getTraceState()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public String getType()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public boolean mediate(MessageContext mc)
	{
		SOAPFactory soapFactory = new SOAP11Factory();
		OMElement binaryNode = mc.getEnvelope().getBody().getFirstChildWithName(new QName("payload"));
		byte[] source = binaryNode.getText().getBytes();
		
		byte[] b = new byte[source.length];
	    int j = 0;
		for(int i=source.length-1; i>0; i--)
		{
			b[j] = source[i];
			j++;
		}
		
		mc.getEnvelope().getBody().getFirstChildWithName(new QName("payload")).detach();
		
		DataHandler dataHandler = new DataHandler(
                new ByteArrayDataSource(b));
        OMText textData = soapFactory.createOMText(dataHandler, true);
        OMElement wrapper = soapFactory.createOMElement(new QName("payload"), null);
        wrapper.addChild(textData);
        mc.setDoingMTOM(true);
        
        mc.getEnvelope().getBody().addChild(wrapper);
		return true;
	}

	public void setTraceState(int arg0)
	{
		// TODO Auto-generated method stub

	}

}
