package org.apache.qpid.nclient.api;

/**
 * Enumeration of the options available when declaring an exchange
 */
public enum DeclareExchangeOption
{
    AUTO_DELETE,
    DURABLE,
    INTERNAL,
    NOWAIT,
    PASSIVE;
}
