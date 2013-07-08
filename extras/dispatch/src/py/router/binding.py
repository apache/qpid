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


class BindingEngine(object):
  """
  This module is responsible for responding to two different events:
    1) The learning of new remote mobile addresses
    2) The change of topology (i.e. different next-hops for remote routers)
  When these occur, this module converts the mobile routing table (address => router)
  to a next-hop routing table (address => next-hop), compresses the keys in case there
  are wild-card overlaps, and notifies outbound of changes in the "mobile-key" address class.
  """
  def __init__(self, container):
    self.container = container
    self.id = self.container.id
    self.area = self.container.area
    self.current_keys = {}


  def tick(self, now):
    pass


  def mobile_keys_changed(self, keys):
    self.current_keys = keys
    next_hop_keys = self._convert_ids_to_next_hops(keys)
    routing_table = self._compress_keys(next_hop_keys)
    self.container.remote_routes_changed('mobile-key', routing_table)


  def next_hops_changed(self):
    next_hop_keys = self._convert_ids_to_next_hops(self.current_keys)
    routing_table = self._compress_keys(next_hop_keys)
    self.container.remote_routes_changed('mobile-key', routing_table)


  def _convert_ids_to_next_hops(self, keys):
    next_hops = self.container.get_next_hops()
    new_keys = {}
    for _id, value in keys.items():
      if _id in next_hops:
        next_hop = next_hops[_id]
        if next_hop not in new_keys:
          new_keys[next_hop] = []
        new_keys[next_hop].extend(value)
    return new_keys
      

  def _compress_keys(self, keys):
    trees = {}
    for _id, key_list in keys.items():
      trees[_id] = TopicElementList()
      for key in key_list:
        trees[_id].add_key(key)
    routing_table = []
    for _id, tree in trees.items():
      tree_keys = tree.get_list()
      for tk in tree_keys:
        routing_table.append((tk, _id))
    return routing_table


class TopicElementList(object):
  """
  """
  def __init__(self):
    self.elements = {}  # map text => (terminal, sub-list)

  def __repr__(self):
    return "%r" % self.elements

  def add_key(self, key):
    self.add_tokens(key.split('.'))

  def add_tokens(self, tokens):
    first = tokens.pop(0)
    terminal = len(tokens) == 0

    if terminal and first == '#':
      ## Optimization #1A (A.B.C.D followed by A.B.#)
      self.elements = {'#':(True, TopicElementList())}
      return

    if '#' in self.elements:
      _t,_el = self.elements['#']
      if _t:
        ## Optimization #1B (A.B.# followed by A.B.C.D)
        return

    if first not in self.elements:
      self.elements[first] = (terminal, TopicElementList())
    else:
      _t,_el = self.elements[first]
      if terminal and not _t:
        self.elements[first] = (terminal, _el)

    if not terminal:
      _t,_el = self.elements[first]
      _el.add_tokens(tokens)

  def get_list(self):
    keys = []
    for token, (_t,_el) in self.elements.items():
      if _t: keys.append(token)
      _el.build_list(token, keys)
    return keys

  def build_list(self, prefix, keys):
    for token, (_t,_el) in self.elements.items():
      if _t: keys.append("%s.%s" % (prefix, token))
      _el.build_list("%s.%s" % (prefix, token), keys)
    


