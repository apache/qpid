/*
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
 */
define(["dijit/registry", "qpid/common/util", "dojo/domReady!"],
   function (registry, util)
   {
       return {
           show: function(data)
           {
              util.buildEditUI(data.containerNode, "virtualhost/sizemonitoring/edit.html", "editVirtualHost.", null, null,
              function()
              {
                  registry.byId("editVirtualHost.storeUnderfullSize").set("regExpGen", util.numericOrContextVarRegexp);
                  registry.byId("editVirtualHost.storeOverfullSize").set("regExpGen", util.numericOrContextVarRegexp);
              });
           }
       };
   }
);
