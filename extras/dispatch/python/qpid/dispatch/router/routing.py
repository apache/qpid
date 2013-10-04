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

class RoutingTableEngine(object):
    """
    This module is responsible for converting the set of next hops to remote routers to a routing
    table in the "topological" address class.
    """
    def __init__(self, container, node_tracker):
        self.container = container
        self.node_tracker = node_tracker
        self.id = self.container.id
        self.area = self.container.area
        self.next_hops = {}


    def tick(self, now):
        pass


    def next_hops_changed(self, next_hops):
        # Convert next_hops into routing table
        self.next_hops = next_hops
        for _id, next_hop in next_hops.items():
            mb_id = self.node_tracker.maskbit_for_node(_id)
            mb_nh = self.node_tracker.maskbit_for_node(next_hop)
            self.container.router_adapter.set_next_hop(mb_id, mb_nh)


    def valid_origins_changes(self, valid_origins):
        for _id, vo in valid_origins.items():
            mb_id = self.node_tracker.maskbit_for_node(_id)
            mb_vo = []
            for o in vo:
                mb_vo.append(self.node_tracker.maskbit_for_node(o))
            self.container.router_adapted.set_valid_origins(mb_id, mb_vo)


    def get_next_hops(self):
        return self.next_hops

