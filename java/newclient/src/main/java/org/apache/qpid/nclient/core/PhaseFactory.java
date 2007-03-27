package org.apache.qpid.nclient.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.nclient.config.ClientConfiguration;

public class PhaseFactory
{
    /**
     * This method will create the pipe and return a reference
     * to the top of the pipeline.
     * 
     * The application can then use this (top most) phase and all
     * calls will propogated down the pipe.
     * 
     * Simillar calls orginating at the bottom of the pipeline
     * will be propogated to the top.
     * 
     * @param ctx
     * @return
     * @throws AMQPException
     */
    public static Phase createPhasePipe(PhaseContext ctx) throws AMQPException
    {
	Map<Integer,Phase> phaseMap = new HashMap<Integer,Phase>();
	List<String> list = ClientConfiguration.get().getList(QpidConstants.PHASE_PIPE + "." + QpidConstants.PHASE);
	for(String s:list)
	{
	    try
	    {
		Phase temp = (Phase)Class.forName(ClientConfiguration.get().getString(s)).newInstance();
		phaseMap.put(ClientConfiguration.get().getInt(s + "." + QpidConstants.INDEX),temp) ;
	    }
	    catch(Exception e)
	    {
		throw new AMQPException("Error loading phase " + ClientConfiguration.get().getString(s),e);
	    }    
	}
	
	Phase current = null;
	Phase prev = null;
	Phase next = null;
	//Lets build the phase pipe.
	for (int i=0; i<phaseMap.size();i++)
	{
	   current = phaseMap.get(i);	   
	   if (1+1 < phaseMap.size())
	   {
	       next = phaseMap.get(i+1);
	   }
	   current.init(ctx, next, prev);
	   prev = current;
	   next = null;
	}
	
	return current;
    }
}