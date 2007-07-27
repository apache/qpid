package org.apache.qpid.nclient.api;

/**
 * Available options for deleting a queue.
 */
public enum DeleteQueueOption
{
    IF_EMPTY,
    IF_UNUSED,
    NO_WAIT;
}
