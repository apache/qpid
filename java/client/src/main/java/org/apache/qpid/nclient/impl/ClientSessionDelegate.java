package org.apache.qpid.nclient.impl;

import org.apache.qpid.nclient.MessagePartListener;
import org.apache.qpidity.CommonSessionDelegate;
import org.apache.qpidity.ExchangeQueryOk;
import org.apache.qpidity.Header;
import org.apache.qpidity.MessageTransfer;
import org.apache.qpidity.Option;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.Session;


public class ClientSessionDelegate extends CommonSessionDelegate
{
	
	/*@Override public void messageTransfer(Session context, MessageTransfer struct)
    {
        MessagePartListener l = context.messagListeners.get(struct.getDestination());
        l.messageTransfer(struct.getDestination(),new Option[0]);
    }*/

    // ---------------------------------------------------------------
    //  Non generated methods - but would like if they are also generated.
    //  These methods should be called from Body and Header Handlers.
    //  If these methods are generated as part of the delegate then
    //  I can call these methods from the BodyHandler and HeaderHandler
    //  in a generic way

    //  I have used destination to indicate my intent of receiving
    //  some form of correlation to know which consumer this data belongs to.
    //  It can be anything as long as I can make the right correlation
    // ----------------------------------------------------------------
   /* public void data(Session context,String destination,byte[] src) throws QpidException
    {
        MessagePartListener l = context.messagListeners.get(destination);
        l.data(src);
    }

    public void endData(Session context,String destination) throws QpidException
    {
        MessagePartListener l = context.messagListeners.get(destination);
        l.endData();
    }

    public void messageHeaders(Session context,String destination,Header... headers) throws QpidException
    {
        MessagePartListener l = context.messagListeners.get(destination);
        l.endData();
    }*/


    // --------------------------------------------
    //   Exchange related functionality
    // --------------------------------------------
    public void exchangeQueryOk(Session session, ExchangeQueryOk struct) {}

}
