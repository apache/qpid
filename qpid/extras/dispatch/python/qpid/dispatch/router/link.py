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

from data import MessageRA, MessageLSU, MessageLSR
from time import time

try:
    from dispatch import *
except ImportError:
    from ..stubs import *

class LinkStateEngine(object):
    """
    This module is responsible for running the Link State protocol and maintaining the set
    of link states that are gathered from the domain.  It notifies outbound when changes to
    the link-state-collection are detected.
    """
    def __init__(self, container):
        self.container = container
        self.id = self.container.id
        self.area = self.container.area
        self.ra_interval = self.container.config.ra_interval
        self.remote_ls_max_age = self.container.config.remote_ls_max_age
        self.last_ra_time = 0
        self.collection = {}
        self.collection_changed = False
        self.mobile_seq = 0
        self.needed_lsrs = {}


    def tick(self, now):
        self._expire_ls(now)
        self._send_lsrs()

        if now - self.last_ra_time >= self.ra_interval:
            self.last_ra_time = now
            self._send_ra()

        if self.collection_changed:
            self.collection_changed = False
            self.container.log(LOG_INFO, "New Link-State Collection:")
            for a,b in self.collection.items():
                self.container.log(LOG_INFO, "  %s => %r" % (a, b.peers))
            self.container.ls_collection_changed(self.collection)


    def handle_ra(self, msg, now):
        if msg.id == self.id:
            return
        if msg.id in self.collection:
            ls = self.collection[msg.id]
            ls.last_seen = now
            if ls.ls_seq < msg.ls_seq:
                self.needed_lsrs[(msg.area, msg.id)] = None
        else:
            self.needed_lsrs[(msg.area, msg.id)] = None


    def handle_lsu(self, msg, now):
        if msg.id == self.id:
            return
        if msg.id in self.collection:
            ls = self.collection[msg.id]
            if ls.ls_seq < msg.ls_seq:
                ls = msg.ls
                self.collection[msg.id] = ls
                self.collection_changed = True
            ls.last_seen = now
        else:
            ls = msg.ls
            self.collection[msg.id] = ls
            self.collection_changed = True
            ls.last_seen = now
            self.container.new_node(msg.id)
            self.container.log(LOG_INFO, "Learned link-state from new router: %s" % msg.id)
        # Schedule LSRs for any routers referenced in this LS that we don't know about
        for _id in msg.ls.peers:
            if _id not in self.collection:
                self.container.new_node(_id)
                self.needed_lsrs[(msg.area, _id)] = None


    def handle_lsr(self, msg, now):
        if msg.id == self.id:
            return
        if self.id not in self.collection:
            return
        my_ls = self.collection[self.id]
        self.container.send('amqp:/_topo/%s/%s/qdxrouter' % (msg.area, msg.id), MessageLSU(None, self.id, self.area, my_ls.ls_seq, my_ls))


    def new_local_link_state(self, link_state):
        self.collection[self.id] = link_state
        self.collection_changed = True
        self._send_ra()


    def set_mobile_sequence(self, seq):
        self.mobile_seq = seq


    def get_collection(self):
        return self.collection


    def _expire_ls(self, now):
        to_delete = []
        for key, ls in self.collection.items():
            if key != self.id and now - ls.last_seen > self.remote_ls_max_age:
                to_delete.append(key)
        for key in to_delete:
            ls = self.collection.pop(key)
            self.collection_changed = True
            self.container.lost_node(key)
            self.container.log(LOG_INFO, "Expired link-state from router: %s" % key)


    def _send_lsrs(self):
        for (_area, _id) in self.needed_lsrs.keys():
            self.container.send('amqp:/_topo/%s/%s/qdxrouter' % (_area, _id), MessageLSR(None, self.id, self.area))
        self.needed_lsrs = {}


    def _send_ra(self):
        ls_seq = 0
        if self.id in self.collection:
            ls_seq = self.collection[self.id].ls_seq
        self.container.send('amqp:/_topo/%s/all/qdxrouter' % self.area, MessageRA(None, self.id, self.area, ls_seq, self.mobile_seq))
