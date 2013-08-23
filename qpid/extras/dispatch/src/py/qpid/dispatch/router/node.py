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
  from ..stubs import *


class NodeTracker(object):
  """
  This module is responsible for tracking the set of router nodes that are known to this
  router.  It tracks whether they are neighbor or remote and whether they are reachable.
  """
  def __init__(self, container):
    self.container = container
    self.id    = self.container.id
    self.area  = self.container.area
    self.nodes = {}  # id => RemoteNode


  def tick(self, now):
    pass


  def new_neighbor(self, node_id):
    if node_id not in self.nodes:
      self.nodes[node_id] = RemoteNode(node_id)
    self.nodes[node_id].set_neighbor()
    self._notify(self.nodes[node_id])


  def lost_neighbor(self, node_id):
    node = self.nodes[node_id]
    node.clear_neighbor()
    self._notify(node)
    if node.to_delete():
      self.nodes.pop(node_id)


  def new_node(self, node_id):
    if node_id not in self.nodes:
      self.nodes[node_id] = RemoteNode(node_id)
    self.nodes[node_id].set_remote()
    self._notify(self.nodes[node_id])


  def lost_node(self, node_id):
    node = self.nodes[node_id]
    node.clear_remote()
    self._notify(node)
    if node.to_delete():
      self.nodes.pop(node_id)


  def _notify(self, node):
    if node.to_delete():
      self.container.adapter.node_updated("R%s" % node.id, 0, 0)
    else:
      is_neighbor = 0
      if node.neighbor:
        is_neighbor = 1
      self.container.adapter.node_updated("R%s" % node.id, 1, is_neighbor)


class RemoteNode(object):

  def __init__(self, node_id):
    self.id       = node_id
    self.neighbor = None
    self.remote   = None

  def set_neighbor(self):
    self.neighbor = True

  def set_remote(self):
    self.remote = True

  def clear_neighbor(self):
    self.neighbor = None

  def clear_remote(self):
    self.remote = None

  def to_delete(self):
    return self.neighbor or self.remote

