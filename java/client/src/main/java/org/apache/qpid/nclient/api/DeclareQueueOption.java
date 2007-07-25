package org.apache.qpid.nclient.api;

/**
 * Enumeration of the options available when declaring a queue
 *
 * Created by Arnaud Simon
 * Date: 23-Jul-2007
 * Time: 09:44:36
 */
public enum DeclareQueueOption
{
    AUTO_DELETE,
    DURABLE,
    EXCLUSIVE,
    NOWAIT,
    PASSIVE;
}
