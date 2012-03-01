#!/usr/bin/env python

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

"""%prog [options] broker...
Check for brokers that lag behind other brokers in a cluster."""

import os, os.path, sys, socket, time, re
from qpid.messaging import *
from optparse import OptionParser
from threading import Thread

class Browser(Thread):
    def __init__(self, broker, queue, timeout):
        Thread.__init__(self)
        self.broker = broker
        self.queue = queue
        self.timeout = timeout
        self.error = None
        self.time = None

    def run(self):
        try:
            self.connection = Connection(self.broker)
            self.connection.open()
            self.session = self.connection.session()
            self.receiver = self.session.receiver("%s;{mode:browse}"%self.queue)
            self.msg = self.receiver.fetch(timeout=self.timeout)
            self.time = time.time()
            if (self.msg.content != self.queue):
                raise Exception("Wrong message content, expected '%s' found '%s'"%
                                (self.queue, self.msg.content))
        except Empty:
            self.error = "No message on queue %s"%self.queue
        except Exception, e:
            self.error = "Error: %s"%e

def main(argv):
    op = OptionParser(usage=__doc__)
    op.add_option("--timeout", type="float", default=None, metavar="TIMEOUT",
                   help="Give up after TIMEOUT milliseconds, default never timeout")
    (opts, args) = op.parse_args(argv)
    if (len(args) <= 1): op.error("No brokers were specified")
    brokers = args[1:]

    # Put a message on a uniquely named queue.
    queue = "%s:%s:%s"%(os.path.basename(args[0]), socket.gethostname(), os.getpid())
    connection = Connection(brokers[0])
    connection.open()
    session = connection.session()
    sender = session.sender(
        "%s;{create:always,delete:always,node:{durable:False}}"%queue)
    sender.send(Message(content=queue))
    start = time.time()
    # Browse for the message on each broker
    if opts.timeout: opts.timeout
    threads = [Browser(b, queue, opts.timeout) for b in brokers]
    for t in threads: t.start()
    delays=[]

    for t in threads:
        t.join()
        if t.error:
            delay=t.error
        else:
            delay = t.time-start
            delays.append([delay, t.broker])
        print "%s: %s"%(t.broker,delay)
    if delays:
        delays.sort()
        print "lag: %s (%s-%s)"%(delays[-1][0] - delays[0][0], delays[-1][1], delays[0][1])
    # Clean up
    sender.close()
    session.close()
    connection.close()

if __name__ == "__main__": sys.exit(main(sys.argv))
