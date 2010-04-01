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

from logging import getLogger
from threading import Thread

log = getLogger("qpid.messaging.util")

def auto_fetch_reconnect_hosts(conn):
  ssn = conn.session("auto-fetch-reconnect-hosts")
  rcv = ssn.receiver("amq.failover")
  rcv.capacity = 10

  def main():
    while True:
      msg = rcv.fetch()
      set_reconnect_hosts(conn, msg)
      ssn.acknowledge(msg, sync=False)

  thread = Thread(name="auto-fetch-reconnect-hosts", target=main)
  thread.setDaemon(True)
  thread.start()


def set_reconnect_hosts(conn, msg):
  reconnect_hosts = []
  urls = msg.properties["amq.failover"]
  for u in urls:
    if u.startswith("amqp:tcp:"):
      parts = u.split(":")
      host, port = parts[2:4]
      reconnect_hosts.append((host, port))
  conn.reconnect_hosts = reconnect_hosts
  log.warn("set reconnect_hosts for conn %s: %s", conn, reconnect_hosts)

__all__ = ["auto_fetch_reconnect_hosts", "set_reconnect_hosts"]
