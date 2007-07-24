package org.apache.qpid.nclient.qpidapi;

/**
 * Enumeration of the options available when declaring an exchange
 *
 * Created by Arnaud Simon
 * Date: 20-Jul-2007
 * Time: 09:44:52
 */
public enum DeclareExchangeOption
{
    AUTO_DELETE,
    DURABLE,
    INTERNAL,
    NOWAIT,
    PASSIVE;
}
