package org.apache.qpid.nclient.qpidapi;

/**
 * Enumeration of the options available when creating a receiver
 *
 * Created by Arnaud Simon
 * Date: 20-Jul-2007
 * Time: 09:43:31
 */
public enum CreateReceiverOption
{
    NO_LOCAL,
    EXCLUSIVE,
    NO_ACQUIRE,    
    CONFIRME;
}
