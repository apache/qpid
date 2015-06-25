#
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

"""
Add-on utilities for the L{qpid.messaging} API.
"""

from qpid.messaging import *
from logging import getLogger
from threading import Thread

log = getLogger("qpid.messaging.util")

def auto_fetch_reconnect_urls(conn):
  ssn = conn.session("auto-fetch-reconnect-urls")
  rcv = ssn.receiver("amq.failover")
  rcv.capacity = 10

  def main():
    while True:
      try:
        msg = rcv.fetch()
      except LinkClosed:
        return
      set_reconnect_urls(conn, msg)
      ssn.acknowledge(msg, sync=False)

  thread = Thread(name="auto-fetch-reconnect-urls", target=main)
  thread.setDaemon(True)
  thread.start()


def set_reconnect_urls(conn, msg):
  reconnect_urls = []
  urls = msg.properties["amq.failover"]
  for u in urls:
    # FIXME aconway 2012-06-12: Nasty hack parsing of the C++ broker's URL format.
    if u.startswith("amqp:"):
      for a in u[5:].split(","):
        parts = a.split(":")
        # Handle IPv6 addresses which have : in the host part.
        port = parts[-1]        # Last : separated field is port
        host = ":".join(parts[1:-1]) # First : separated field is protocol, host is the rest.
        reconnect_urls.append("%s:%s" % (host, port))
  conn.reconnect_urls = reconnect_urls
  log.warn("set reconnect_urls for conn %s: %s", conn, reconnect_urls)

__all__ = ["auto_fetch_reconnect_urls", "set_reconnect_urls"]
