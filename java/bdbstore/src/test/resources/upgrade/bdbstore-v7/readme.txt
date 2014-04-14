The bdbstore v7 data was obtained by running 0.26 and:

* creating an exchange 'myexch' of type direct
* creating queues 'queue1' and 'queue2'
* binding 'queue1' to 'myexch' and 'amq.direct' using binding key 'queue1'
* binding 'queue2' to amq.fanout only using binding key 'queue2'