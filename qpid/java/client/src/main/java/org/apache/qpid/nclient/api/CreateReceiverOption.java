package org.apache.qpid.nclient.api;

/**
 * Enumeration of the options available when creating a receiver
 */
public enum CreateReceiverOption
{
    NO_LOCAL,
    EXCLUSIVE,
    NO_ACQUIRE,    
    CONFIRME;
}
