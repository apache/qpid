package org.apache.qpid.nclient.impl;

import org.apache.qpidity.CommonSessionDelegate;
import org.apache.qpidity.ExchangeQueryOk;
import org.apache.qpidity.QueueDeclareOk;
import org.apache.qpidity.QueueDeleteOk;
import org.apache.qpidity.QueuePurgeOk;
import org.apache.qpidity.Session;


public class ClientSessionDelegate extends CommonSessionDelegate
{

	
	 // --------------------------------------------
	 //   Exchange related functionality
	 // --------------------------------------------	 
    public void exchangeQueryOk(Session session, ExchangeQueryOk struct) {}

    
    
	// --------------------------------------------
	//    Queue related functionality
	// --------------------------------------------
    
    public void queueDeclareOk(Session session, QueueDeclareOk struct) {}
    
    public void queuePurgeOk(Session session, QueuePurgeOk struct) {}
    
    public void queueDeleteOk(Session session, QueueDeleteOk struct) {}
}
