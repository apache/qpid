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

class PathEngine(object):
    """
    This module is responsible for computing the next-hop for every router/area in the domain
    based on the collection of link states that have been gathered.
    """
    def __init__(self, container):
        self.container = container
        self.id = self.container.id
        self.area = self.container.area
        self.recalculate = False
        self.collection = None


    def tick(self, now_unused):
        if self.recalculate:
            self.recalculate = False
            self._calculate_routes()


    def ls_collection_changed(self, collection):
        self.recalculate = True
        self.collection = collection


    def _calculate_tree_from_root(self, root):
        ##
        ## Make a copy of the current collection of link-states that contains
        ## a fake link-state for nodes that are known-peers but are not in the
        ## collection currently.  This is needed to establish routes to those nodes
        ## so we can trade link-state information with them.
        ##
        link_states = {}
        for _id, ls in self.collection.items():
            link_states[_id] = ls.peers
            for p in ls.peers:
                if p not in link_states:
                    link_states[p] = [_id]

        ##
        ## Setup Dijkstra's Algorithm
        ##
        cost = {}
        prev = {}
        for _id in link_states:
            cost[_id] = None  # infinite
            prev[_id] = None  # undefined
        cost[root] = 0   # no cost to the root node
        unresolved = NodeSet(cost)

        ##
        ## Process unresolved nodes until lowest cost paths to all reachable nodes have been found.
        ##
        while not unresolved.empty():
            u = unresolved.lowest_cost()
            if cost[u] == None:
                # There are no more reachable nodes in unresolved
                break
            for v in link_states[u]:
                if unresolved.contains(v):
                    alt = cost[u] + 1   # TODO - Use link cost instead of 1
                    if cost[v] == None or alt < cost[v]:
                        cost[v] = alt
                        prev[v] = u
                        unresolved.set_cost(v, alt)

        ##
        ## Remove unreachable nodes from the map.  Note that this will also remove the
        ## root node (has no previous node) from the map.
        ##
        for u, val in prev.items():
            if not val:
                prev.pop(u)

        ##
        ## Return previous-node map.  This is a map of all reachable, remote nodes to
        ## their predecessor node.
        ##
        return prev


    def _calculate_valid_origins(self, nodeset):
        ##
        ## Calculate the tree from each origin, determine the set of origins-per-dest
        ## for which the path from origin to dest passes through us.  This is the set
        ## of valid origins for forwarding to the destination.
        ##
        valid_origin = {}         # Map of destination => List of Valid Origins
        for node in nodeset:
            if node != self.id:
                valid_origin[node] = []

        for root in valid_origin.keys():
            prev  = self._calculate_tree_from_root(root)
            nodes = prev.keys()
            while len(nodes) > 0:
                u = nodes[0]
                path = [u]
                nodes.remove(u)
                v = prev[u]
                while v != root:
                    if v in nodes:
                        if v != self.id:
                            path.append(v)
                        nodes.remove(v)
                    if v == self.id:
                        valid_origin[root].extend(path)
                    u = v
                    v = prev[u]
        return valid_origin


    def _calculate_routes(self):
        ##
        ## Generate the shortest-path tree with the local node as root
        ##
        prev  = self._calculate_tree_from_root(self.id)
        nodes = prev.keys()

        ##
        ## Distill the path tree into a map of next hops for each node
        ##
        next_hops = {}
        while len(nodes) > 0:
            u = nodes[0]          # pick any destination
            path = [u]
            nodes.remove(u)
            v = prev[u]
            while v != self.id:   # build a list of nodes in the path back to the root
                if v in nodes:
                    path.append(v)
                    nodes.remove(v)
                u = v
                v = prev[u]
            for w in path:        # mark each node in the path as reachable via the next hop
                next_hops[w] = u

        self.container.next_hops_changed(next_hops)

        ##
        ## Calculate the valid origins for remote routers
        ##
        valid_origin = self._calculate_valid_origins(prev.keys())
        self.container.valid_origins_changed(valid_origin)



class NodeSet(object):
    """
    This data structure is an ordered list of node IDs, sorted in increasing order by their cost.
    Equal cost nodes are secondarily sorted by their ID in order to provide deterministic and
    repeatable ordering.
    """
    def __init__(self, cost_map):
        self.nodes = []
        for _id, cost in cost_map.items():
            ##
            ## Assume that nodes are either unreachable (cost = None) or local (cost = 0)
            ## during this initialization.
            ##
            if cost == 0:
                self.nodes.insert(0, (_id, cost))
            else:
                ##
                ## There is no need to sort unreachable nodes by ID
                ##
                self.nodes.append((_id, cost))


    def __repr__(self):
        return self.nodes.__repr__()


    def empty(self):
        return len(self.nodes) == 0


    def contains(self, _id):
        for a, b in self.nodes:
            if a == _id:
                return True
        return False


    def lowest_cost(self):
        """
        Remove and return the lowest cost node ID.
        """
        _id, cost = self.nodes.pop(0)
        return _id


    def set_cost(self, _id, new_cost):
        """
        Set the cost for an ID in the NodeSet and re-insert the ID so that the list
        remains sorted in increasing cost order.
        """
        index = 0
        for i, c in self.nodes:
            if i == _id:
                break
            index += 1
        self.nodes.pop(index)

        index = 0
        for i, c in self.nodes:
            if c == None or new_cost < c or (new_cost == c and _id < i):
                break
            index += 1

        self.nodes.insert(index, (_id, new_cost))

