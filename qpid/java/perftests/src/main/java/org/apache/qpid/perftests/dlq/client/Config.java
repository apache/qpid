package org.apache.qpid.perftests.dlq.client;

public interface Config
{
    String BROKER = "broker";
    String MAX_REDELIVERY = "maxRedelivery";
    String MAX_PREFETCH = "maxPrefetch";
    String SESSION = "session";
    String QUEUE = "queue";
    String PERSISTENT = "persistent";
    String COUNT = "count";
    String SIZE = "size";
    String MESSAGE_IDS = "messageIds";
    String THREADS = "threads";
    String MAX_RECORDS = "maxRecords";
    String LISTENER = "listener";
    String REJECT = "reject";
    String REJECT_COUNT = "rejectCount";
    String REPEAT = "repeat";
    
    String SESSION_TRANSACTED = "SESSION_TRANSACTED";
    String AUTO_ACKNOWLEDGE = "AUTO_ACKNOWLEDGE";
    String CLIENT_ACKNOWLEDGE = "CLIENT_ACKNOWLEDGE";
    String DUPS_OK_ACKNOWLEDGE = "DUPS_OK_ACKNOWLEDGE";
    
    String[] SESSION_VALUES = new String[] {
        SESSION_TRANSACTED, AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
    };
}
