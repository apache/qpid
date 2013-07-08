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

from time import time
from uuid import uuid4

from configuration import Configuration
from data import *
from neighbor import NeighborEngine
from link import LinkStateEngine
from path import PathEngine
from mobile import MobileAddressEngine
from routing import RoutingTableEngine
from binding import BindingEngine
from adapter import AdapterEngine

##
## Import the Dispatch adapters from the environment.  If they are not found
## (i.e. we are in a test bench, etc.), load the stub versions.
##
try:
  from dispatch import *
except ImportError:
  from stubs import *


class RouterEngine:
  """
  """

  def __init__(self, router_adapter, router_id=None, area='area', config_override={}):
    """
    Initialize an instance of a router for a domain.
    """
    ##
    ## Record important information about this router instance
    ##
    self.domain         = "domain"
    self.router_adapter = router_adapter
    self.log_adapter    = LogAdapter("dispatch.router")
    self.io_adapter     = IoAdapter(self, "qdxrouter")

    if router_id:
      self.id = router_id
    else:
      self.id = str(uuid4())
    self.area = area
    self.log(LOG_INFO, "Router Engine Instantiated: area=%s id=%s" % (self.area, self.id))

    ##
    ## Setup configuration
    ##
    self.config = Configuration(config_override)
    self.log(LOG_INFO, "Config: %r" % self.config)

    ##
    ## Launch the sub-module engines
    ##
    self.neighbor_engine       = NeighborEngine(self)
    self.link_state_engine     = LinkStateEngine(self)
    self.path_engine           = PathEngine(self)
    self.mobile_address_engine = MobileAddressEngine(self)
    self.routing_table_engine  = RoutingTableEngine(self)
    self.binding_engine        = BindingEngine(self)
    self.adapter_engine        = AdapterEngine(self)



  ##========================================================================================
  ## Adapter Entry Points - invoked from the adapter
  ##========================================================================================
  def getId(self):
    """
    Return the router's ID
    """
    return self.id


  def addLocalAddress(self, key):
    """
    """
    try:
      if key.find('_topo') == 0 or key.find('_local') == 0:
        return
      self.mobile_address_engine.add_local_address(key)
    except Exception, e:
      self.log(LOG_ERROR, "Exception in new-address processing: exception=%r" % e)

  def delLocalAddress(self, key):
    """
    """
    try:
      if key.find('_topo') == 0 or key.find('_local') == 0:
        return
      self.mobile_address_engine.del_local_address(key)
    except Exception, e:
      self.log(LOG_ERROR, "Exception in del-address processing: exception=%r" % e)


  def handleTimerTick(self):
    """
    """
    try:
      now = time()
      self.neighbor_engine.tick(now)
      self.link_state_engine.tick(now)
      self.path_engine.tick(now)
      self.mobile_address_engine.tick(now)
      self.routing_table_engine.tick(now)
      self.binding_engine.tick(now)
      self.adapter_engine.tick(now)
    except Exception, e:
      self.log(LOG_ERROR, "Exception in timer processing: exception=%r" % e)


  def handleControlMessage(self, opcode, body):
    """
    """
    try:
      now = time()
      if   opcode == 'HELLO':
        msg = MessageHELLO(body)
        self.log(LOG_TRACE, "RCVD: %r" % msg)
        self.neighbor_engine.handle_hello(msg, now)

      elif opcode == 'RA':
        msg = MessageRA(body)
        self.log(LOG_TRACE, "RCVD: %r" % msg)
        self.link_state_engine.handle_ra(msg, now)
        self.mobile_address_engine.handle_ra(msg, now)

      elif opcode == 'LSU':
        msg = MessageLSU(body)
        self.log(LOG_TRACE, "RCVD: %r" % msg)
        self.link_state_engine.handle_lsu(msg, now)

      elif opcode == 'LSR':
        msg = MessageLSR(body)
        self.log(LOG_TRACE, "RCVD: %r" % msg)
        self.link_state_engine.handle_lsr(msg, now)

      elif opcode == 'MAU':
        msg = MessageMAU(body)
        self.log(LOG_TRACE, "RCVD: %r" % msg)
        self.mobile_address_engine.handle_mau(msg, now)

      elif opcode == 'MAR':
        msg = MessageMAR(body)
        self.log(LOG_TRACE, "RCVD: %r" % msg)
        self.mobile_address_engine.handle_mar(msg, now)

    except Exception, e:
      self.log(LOG_ERROR, "Exception in message processing: opcode=%s body=%r exception=%r" % (opcode, body, e))


  def receive(self, message_properties, body):
    """
    This is the IoAdapter message-receive handler
    """
    try:
      self.handleControlMessage(message_properties['opcode'], body)
    except Exception, e:
      self.log(LOG_ERROR, "Exception in raw message processing: properties=%r body=%r exception=%r" %
               (message_properties, body, e))

  def getRouterData(self, kind):
    """
    """
    if kind == 'help':
      return { 'help'           : "Get list of supported values for kind",
               'link-state'     : "This router's link state",
               'link-state-set' : "The set of link states from known routers",
               'next-hops'      : "Next hops to each known router",
               'topo-table'     : "Topological routing table",
               'mobile-table'   : "Mobile key routing table"
             }
    if kind == 'link-state'     : return self.neighbor_engine.link_state.to_dict()
    if kind == 'next-hops'      : return self.routing_table_engine.next_hops
    if kind == 'topo-table'     : return {'table': self.adapter_engine.key_classes['topological']}
    if kind == 'mobile-table'   : return {'table': self.adapter_engine.key_classes['mobile-key']}
    if kind == 'link-state-set' :
      copy = {}
      for _id,_ls in self.link_state_engine.collection.items():
        copy[_id] = _ls.to_dict()
      return copy

    return {'notice':'Use kind="help" to get a list of possibilities'}


  ##========================================================================================
  ## Adapter Calls - outbound calls to Dispatch
  ##========================================================================================
  def log(self, level, text):
    """
    Emit a log message to the host's event log
    """
    self.log_adapter.log(level, text)


  def send(self, dest, msg):
    """
    Send a control message to another router.
    """
    app_props = {'opcode' : msg.get_opcode() }
    self.io_adapter.send(dest, app_props, msg.to_dict())
    self.log(LOG_TRACE, "SENT: %r dest=%s" % (msg, dest))


  ##========================================================================================
  ## Interconnect between the Sub-Modules
  ##========================================================================================
  def local_link_state_changed(self, link_state):
    self.log(LOG_DEBUG, "Event: local_link_state_changed: %r" % link_state)
    self.link_state_engine.new_local_link_state(link_state)

  def ls_collection_changed(self, collection):
    self.log(LOG_DEBUG, "Event: ls_collection_changed: %r" % collection)
    self.path_engine.ls_collection_changed(collection)

  def next_hops_changed(self, next_hop_table):
    self.log(LOG_DEBUG, "Event: next_hops_changed: %r" % next_hop_table)
    self.routing_table_engine.next_hops_changed(next_hop_table)
    self.binding_engine.next_hops_changed()

  def mobile_sequence_changed(self, mobile_seq):
    self.log(LOG_DEBUG, "Event: mobile_sequence_changed: %d" % mobile_seq)
    self.link_state_engine.set_mobile_sequence(mobile_seq)

  def mobile_keys_changed(self, keys):
    self.log(LOG_DEBUG, "Event: mobile_keys_changed: %r" % keys)
    self.binding_engine.mobile_keys_changed(keys)

  def get_next_hops(self):
    return self.routing_table_engine.get_next_hops()

  def remote_routes_changed(self, key_class, routes):
    self.log(LOG_DEBUG, "Event: remote_routes_changed: class=%s routes=%r" % (key_class, routes))
    self.adapter_engine.remote_routes_changed(key_class, routes)

