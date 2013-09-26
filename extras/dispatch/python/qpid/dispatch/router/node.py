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

    This module is also responsible for assigning a unique mask bit value to each router.
    The mask bit is used in the main router to represent sets of valid destinations for addresses.
    """
    def __init__(self, container, max_routers):
        self.container    = container
        self.max_routers  = max_routers
        self.nodes        = {}  # id => RemoteNode
        self.maskbits     = []
        self.next_maskbit = 0
        for i in range(max_routers):
            self.maskbits.append(None)


    def tick(self, now):
        pass


    def new_neighbor(self, node_id, link_maskbit):
        """
        A node, designated by node_id, has been discovered as a neighbor over a link with
        a maskbit of link_maskbit.
        """
        if node_id not in self.nodes:
            self.nodes[node_id] = RemoteNode(node_id, self._allocate_maskbit())
        self.nodes[node_id].set_neighbor(link_maskbit)
        self._notify(self.nodes[node_id])


    def lost_neighbor(self, node_id):
        """
        We have lost contact with a neighboring node node_id.
        """
        node = self.nodes[node_id]
        node.clear_neighbor()
        self._notify(node)
        if node.to_delete():
            self._free_maskbit(node.maskbit)
            self.nodes.pop(node_id)


    def new_node(self, node_id):
        """
        A node, designated by node_id, has been discovered through the an advertisement from a
        remote peer.
        """
        if node_id not in self.nodes:
            self.nodes[node_id] = RemoteNode(node_id, self._allocate_maskbit())
        self.nodes[node_id].set_remote()
        self._notify(self.nodes[node_id])


    def lost_node(self, node_id):
        """
        A remote node, node_id, has not been heard from for too long and is being deemed lost.
        """
        node = self.nodes[node_id]
        node.clear_remote()
        self._notify(node)
        if node.to_delete():
            self._free_maskbit(node.maskbit)
            self.nodes.pop(node_id)


    def _allocate_maskbit(self):
        if self.next_maskbit == None:
            raise Exception("Exceeded Maximum Router Count")
        result = self.next_maskbit
        self.next_maskbit = None
        self.maskbits[result] = True
        for n in range(result + 1, self.max_routers):
            if self.maskbits[n] == None:
                self.next_maskbit = n
                break
        return result


    def _free_maskbit(self, i):
        self.maskbits[i] = None
        if self.next_maskbit == None or i < self.next_maskbit:
            self.next_maskbit = i


    def _notify(self, node):
        if node.to_delete():
            self.container.node_updated("R%s" % node.id, 0, 0, 0, 0)
        else:
            is_neighbor = 0
            if node.neighbor:
                is_neighbor = 1
            self.container.node_updated("R%s" % node.id, 1, is_neighbor, node.link_maskbit, node.maskbit)


class RemoteNode(object):

    def __init__(self, node_id, maskbit):
        self.id           = node_id
        self.neighbor     = None
        self.link_maskbit = None
        self.maskbit      = maskbit
        self.remote       = None

    def set_neighbor(self, link_maskbit):
        self.neighbor     = True
        self.link_maskbit = link_maskbit

    def set_remote(self):
        self.remote = True

    def clear_neighbor(self):
        self.neighbor     = None
        self.link_maskbit = None

    def clear_remote(self):
        self.remote = None

    def to_delete(self):
        return self.neighbor == None and self.remote == None

