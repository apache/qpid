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

try:
  from dispatch import *
except ImportError:
  from stubs import *

ENTRY_OLD     = 1
ENTRY_CURRENT = 2
ENTRY_NEW     = 3

class AdapterEngine(object):
  """
  This module is responsible for managing the Adapter's key bindings (list of address-subject:next-hop).
  Key binding lists are kept in disjoint key-classes that can come from different parts of the router
  (i.e. topological keys for inter-router communication and mobile keys for end users).

  For each key-class, a mirror copy of what the adapter has is kept internally.  This allows changes to the
  routing tables to be efficiently communicated to the adapter in the form of table deltas.
  """
  def __init__(self, container):
    self.container = container
    self.id = self.container.id
    self.area = self.container.area
    self.key_classes = {}  # map [key_class] => (addr-key, next-hop)


  def tick(self, now):
    """
    There is no periodic processing needed for this module.
    """
    pass


  def remote_routes_changed(self, key_class, new_table):
    old_table = []
    if key_class in self.key_classes:
      old_table = self.key_classes[key_class]

    # flag all of the old entries
    old_flags = {}
    for a,b in old_table:
      old_flags[(a,b)] = ENTRY_OLD

    # flag the new entries
    new_flags = {}
    for a,b in new_table:
      new_flags[(a,b)] = ENTRY_NEW

    # calculate the differences from old to new
    for a,b in new_table:
      if old_table.count((a,b)) > 0:
        old_flags[(a,b)] = ENTRY_CURRENT
        new_flags[(a,b)] = ENTRY_CURRENT

    # make to_add and to_delete lists
    to_add    = []
    to_delete = []
    for (a,b),f in old_flags.items():
      if f == ENTRY_OLD:
        to_delete.append((a,b))
    for (a,b),f in new_flags.items():
      if f == ENTRY_NEW:
        to_add.append((a,b))

    # set the routing table to the new contents
    self.key_classes[key_class] = new_table

    # update the adapter's routing tables
    # Note: Do deletions before adds to avoid overlapping routes that may cause
    #       messages to be duplicated.  It's better to have gaps in the routing
    #       tables momentarily because unroutable messages are stored for retry.
    for a,b in to_delete:
      self.container.router_adapter.remote_unbind(a, b)
    for a,b in to_add:
      self.container.router_adapter.remote_bind(a, b)

    self.container.log(LOG_INFO, "New Routing Table (class=%s):" % key_class)
    for a,b in new_table:
      self.container.log(LOG_INFO, "  %s => %s" % (a, b))


