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

def auto_update_backups(conn):
  ssn = conn.session("auto-update-backups")
  rcv = ssn.receiver("amq.failover")
  rcv.capacity = 10

  def main():
    while True:
      msg = rcv.fetch()
      update_backups(conn, msg)
      ssn.acknowledge(msg, sync=False)

  thread = Thread(name="auto-update-backups", target=main)
  thread.setDaemon(True)
  thread.start()


def update_backups(conn, msg):
  backups = []
  urls = msg.properties["amq.failover"]
  for u in urls:
    if u.startswith("amqp:tcp:"):
      parts = u.split(":")
      host, port = parts[2:4]
      backups.append((host, port))
  conn.backups = backups
  log.warn("updated backups for conn %s: %s", conn, backups)

__all__ = ["auto_update_backups", "update_backups"]
