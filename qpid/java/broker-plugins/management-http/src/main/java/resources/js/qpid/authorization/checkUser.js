/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

define(["dojo/dom",
         "qpid/authorization/sasl",
         "dijit/registry",
         "dojox/html/entities",
         "dojo/domReady!"], function(dom, sasl, registry, entities){

var updateUI = function updateUI(data)
{
    if(data.user)
    {
      var userName = entities.encode(String(data.user));
      var controlButton = registry.byId("authenticatedUserControls");
      if (controlButton)
      {
        controlButton.set("label", userName);
      }
      dom.byId("authenticatedUser").innerHTML = userName;
      dom.byId("login").style.display = "inline";
    }
};

return {getUserAndUpdateUI: function(){sasl.getUser(updateUI);}}

});
