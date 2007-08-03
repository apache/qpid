package org.apache.qpid.nclient.impl;

import org.apache.qpidity.CommonSessionDelegate;
import org.apache.qpidity.ExchangeQueryOk;
import org.apache.qpidity.Session;


public class ClientSessionDelegate extends CommonSessionDelegate
{

    // --------------------------------------------
    //   Exchange related functionality
    // --------------------------------------------
    public void exchangeQueryOk(Session session, ExchangeQueryOk struct) {}

}
