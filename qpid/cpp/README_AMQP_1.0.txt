This branch contains an initial integration of AMQP 1.0 support. This
is provided via the qpid 'proton-c' component, available from
https://svn.apache.org/repos/asf/qpid/proton/trunk/proton-c.

The (absolute) path to the location of that library should be
specified via the '--with-proton' option to configure.

At present only the messaging API implementation is integrated
(i.e. client only). I've used the python broker available from
https://github.com/rhs/amqp for my testing so far.

There is no automatic protocol version detection yet, so you need to
explicitly enable AMQP 1.0 using the 'use_amqp1.0' connection option.

E.g. start broker with:

./broker -a -u example-users -n example-nodes

then run:

./examples/messaging/spout --connection-options '{use_amqp1.0:True}' --content 'my-message' queue

then:

./examples/messaging/drain --connection-options '{use_amqp1.0:True}' queue

Note: at present the cluster tests don't run correctly from `make check` due to
inability to find the proton library.

== Status / TODO ==

* client only at present
* there is no support for transactions
* there is no automatic protocol version detection
* only message content is encoded/decoded (not properties/headers)
* only the node names in addresses are actually used at present
* driver-integration:
  - the code at present uses the proton driver for IO
  - each connection uses its own driver and starts a thread for that purpose; TODO: charing drivers between connections
  - there is no integration with the c++ 'transports' (ssl, rdma etc)
  - there is no real SASL integration (just simple PLAIN and ANONYMOUS supported by proton driver)
