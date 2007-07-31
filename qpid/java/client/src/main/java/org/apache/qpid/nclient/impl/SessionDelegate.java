package org.apache.qpid.nclient.impl;

import org.apache.qpidity.CommonSessionDelegate;
import org.apache.qpidity.Delegate;
import org.apache.qpidity.ExchangeDeclare;
import org.apache.qpidity.ExchangeDelete;
import org.apache.qpidity.ExchangeQuery;
import org.apache.qpidity.ExchangeQueryOk;
import org.apache.qpidity.QueueBind;
import org.apache.qpidity.QueueDeclare;
import org.apache.qpidity.QueueDeclareOk;
import org.apache.qpidity.QueueDelete;
import org.apache.qpidity.QueueDeleteOk;
import org.apache.qpidity.QueuePurge;
import org.apache.qpidity.QueuePurgeOk;
import org.apache.qpidity.QueueUnbind;
import org.apache.qpidity.Session;


public class SessionDelegate extends CommonSessionDelegate
{

	/**
	 * --------------------------------------------
	 * Exchange related functionality
	 * --------------------------------------------
	 */
    public void exchangeDeclare(Session session, ExchangeDeclare struct) {}
  
    public void exchangeDelete(Session session, ExchangeDelete struct) {}
    
    public void exchangeQuery(Session session, ExchangeQuery struct) {}
    
    public void exchangeQueryOk(Session session, ExchangeQueryOk struct) {}

	/**
	 * --------------------------------------------
	 * Queue related functionality
	 * --------------------------------------------
	 */    
    public void queueDeclare(Session session, QueueDeclare struct) {}
    
    public void queueDeclareOk(Session session, QueueDeclareOk struct) {}
    
    public void queueBind(Session session, QueueBind struct) {}
    
    public void queueUnbind(Session session, QueueUnbind struct) {}
    
    public void queuePurge(Session session, QueuePurge struct) {}
    
    public void queuePurgeOk(Session session, QueuePurgeOk struct) {}
    
    public void queueDelete(Session session, QueueDelete struct) {}
    
    public void queueDeleteOk(Session session, QueueDeleteOk struct) {}
}
