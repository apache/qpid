package org.apache.qpid.nclient.core;


public interface Phase 
{

    	/**
    	 * This method is used to initialize a phase 
    	 * 
    	 * @param ctx
    	 * @param nextInFlowPhase
    	 * @param nextOutFlowPhase
    	 */
	public void init(PhaseContext ctx,Phase nextInFlowPhase, Phase nextOutFlowPhase);

	/**
	 * 
	 * Implement logic related to physical opening
	 * of the pipe
	 */
	public void start()throws AMQPException;
	
	/**
	 * Implement cleanup in this method.
	 * This indicates the pipe is closing
	 */
	public void close()throws AMQPException;
	
	public void messageReceived(Object msg) throws AMQPException;
	
	public void messageSent(Object msg) throws AMQPException;
	
	public PhaseContext getPhaseContext();
	
	public Phase getNextOutFlowPhase();
	
	public Phase getNextInFlowPhase();
}
