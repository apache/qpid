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

from data import MessageRA, MessageMAR, MessageMAU

try:
  from dispatch import *
except ImportError:
  from ..stubs import *

class MobileAddressEngine(object):
  """
  This module is responsible for maintaining an up-to-date list of mobile addresses in the domain.
  It runs the Mobile-Address protocol and generates an un-optimized routing table for mobile addresses.
  Note that this routing table maps from the mobile address to the remote router where that address
  is directly bound.
  """
  def __init__(self, container):
    self.container = container
    self.id = self.container.id
    self.area = self.container.area
    self.mobile_addr_max_age = self.container.config.mobile_addr_max_age
    self.mobile_seq = 0
    self.local_keys = []
    self.added_keys = []
    self.deleted_keys = []
    self.remote_lists = {}      # map router_id => (sequence, list of keys)
    self.remote_last_seen = {}  # map router_id => time of last seen advertizement/update
    self.remote_changed = False
    self.needed_mars = {}


  def tick(self, now):
    self._expire_remotes(now)
    self._send_mars()

    ##
    ## If local keys have changed, collect the changes and send a MAU with the diffs
    ## Note: it is important that the differential-MAU be sent before a RA is sent
    ##
    if len(self.added_keys) > 0 or len(self.deleted_keys) > 0:
      self.mobile_seq += 1
      self.container.send('_topo.%s.all' % self.area,
                          MessageMAU(None, self.id, self.area, self.mobile_seq, self.added_keys, self.deleted_keys))
      self.local_keys.extend(self.added_keys)
      for key in self.deleted_keys:
        self.local_keys.remove(key)
      self.added_keys = []
      self.deleted_keys = []
      self.container.mobile_sequence_changed(self.mobile_seq)

    ##
    ## If remotes have changed, start the process of updating local bindings
    ##
    if self.remote_changed:
      self.remote_changed = False
      self._update_remote_keys()


  def add_local_address(self, key):
    """
    """
    if self.local_keys.count(key) == 0:
      if self.added_keys.count(key) == 0:
        self.added_keys.append(key)
    else:
      if self.deleted_keys.count(key) > 0:
        self.deleted_keys.remove(key)


  def del_local_address(self, key):
    """
    """
    if self.local_keys.count(key) > 0:
      if self.deleted_keys.count(key) == 0:
        self.deleted_keys.append(key)
    else:
      if self.added_keys.count(key) > 0:
        self.added_keys.remove(key)


  def handle_ra(self, msg, now):
    if msg.id == self.id:
      return

    if msg.mobile_seq == 0:
      return

    if msg.id in self.remote_lists:
      _seq, _list = self.remote_lists[msg.id]
      self.remote_last_seen[msg.id] = now
      if _seq < msg.mobile_seq:
        self.needed_mars[(msg.id, msg.area, _seq)] = None
    else:
      self.needed_mars[(msg.id, msg.area, 0)] = None


  def handle_mau(self, msg, now):
    ##
    ## If the MAU is differential, we can only use it if its sequence is exactly one greater
    ## than our stored sequence.  If not, we will ignore the content and schedule a MAR.
    ##
    ## If the MAU is absolute, we can use it in all cases.
    ##
    if msg.id == self.id:
      return

    if msg.exist_list:
      ##
      ## Absolute MAU
      ##
      if msg.id in self.remote_lists:
        _seq, _list = self.remote_lists[msg.id]
        if _seq >= msg.mobile_seq:  # ignore duplicates
          return
      self.remote_lists[msg.id] = (msg.mobile_seq, msg.exist_list)
      self.remote_last_seen[msg.id] = now
      self.remote_changed = True
    else:
      ##
      ## Differential MAU
      ##
      if msg.id in self.remote_lists:
        _seq, _list = self.remote_lists[msg.id]
        if _seq == msg.mobile_seq:  # ignore duplicates
          return
        self.remote_last_seen[msg.id] = now
        if _seq + 1 == msg.mobile_seq:
          ##
          ## This is one greater than our stored value, incorporate the deltas
          ##
          if msg.add_list and msg.add_list.__class__ == list:
            _list.extend(msg.add_list)
          if msg.del_list and msg.del_list.__class__ == list:
            for key in msg.del_list:
              _list.remove(key)
          self.remote_lists[msg.id] = (msg.mobile_seq, _list)
          self.remote_changed = True
        else:
          self.needed_mars[(msg.id, msg.area, _seq)] = None
      else:
        self.needed_mars[(msg.id, msg.area, 0)] = None


  def handle_mar(self, msg, now):
    if msg.id == self.id:
      return
    if msg.have_seq < self.mobile_seq:
      self.container.send('_topo.%s.%s' % (msg.area, msg.id),
                          MessageMAU(None, self.id, self.area, self.mobile_seq, None, None, self.local_keys))


  def _update_remote_keys(self):
    keys = {}
    for _id,(seq,key_list) in self.remote_lists.items():
      keys[_id] = key_list
    self.container.mobile_keys_changed(keys)


  def _expire_remotes(self, now):
    for _id, t in self.remote_last_seen.items():
      if now - t > self.mobile_addr_max_age:
        self.remote_lists.pop(_id)
        self.remote_last_seen.pop(_id)
        self.remote_changed = True


  def _send_mars(self):
    for _id, _area, _seq in self.needed_mars.keys():
      self.container.send('_topo.%s.%s' % (_area, _id), MessageMAR(None, self.id, self.area, _seq))
    self.needed_mars = {}

