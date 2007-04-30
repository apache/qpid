package org.apache.qpid.nclient.amqp.state;

import org.apache.qpid.nclient.core.AMQPException;

public class AMQPStateMachine 
{
	protected void checkIfValidStateTransition(AMQPState correctState,AMQPState currentState,AMQPState requiredState) throws IllegalStateTransitionException
    {
    	if (currentState != correctState)
    	{
    		throw new IllegalStateTransitionException(currentState,requiredState);
    	}
    }
	
	protected void checkIfValidStateTransition(AMQPState[] correctStates,AMQPState currentState,AMQPState requiredState) throws IllegalStateTransitionException
    {
		for(AMQPState correctState :correctStates)
		{
	    	if (currentState == correctState)
	    	{
	    		return;
	    	}
		}
		throw new IllegalStateTransitionException(currentState,requiredState);
    }	
}
