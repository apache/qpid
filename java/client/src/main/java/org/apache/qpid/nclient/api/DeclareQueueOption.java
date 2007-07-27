package org.apache.qpid.nclient.api;

/**
 * Enumeration of the options available when declaring a queue
 *
 */
public enum DeclareQueueOption
{
    AUTO_DELETE,
    DURABLE,
    EXCLUSIVE,
    NOWAIT,
    PASSIVE;
}
