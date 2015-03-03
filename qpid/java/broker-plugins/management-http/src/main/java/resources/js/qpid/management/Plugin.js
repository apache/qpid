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
define(["dojo/_base/xhr",
        "dojo/parser",
        "dojo/query",
        "dojo/_base/connect",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "dijit/registry",
        "dojo/_base/event",
        "dojox/html/entities",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, registry, event, entities) {

           function Plugin(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "plugin", name: name, parent: parent };
           }

           Plugin.prototype.getTitle = function() {
               return "Plugin: " + this.name ;
           };

           Plugin.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showPlugin.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode).then(function(instances)
                            {
                                that.pluginUpdater = new PluginUpdater(contentPane.containerNode, that.modelObj, that.controller);
                            });
                        }});
           };

           Plugin.prototype.close = function() {
               updater.remove( this.pluginUpdater );
           };

           function PluginUpdater(node, pluginObject, controller)
           {
               this.controller = controller;
               this.name = query(".name", node)[0];
               this.type = query(".type", node)[0];
               this.query = "api/latest/plugin/"+encodeURIComponent(pluginObject.name);

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.pluginData = data[0];

                             that.updateHeader();

                             require(["qpid/management/plugin/"+ that.pluginData.type.toLowerCase().replace('-','')],
                                 function(SpecificPlugin) {
                                 that.details = new SpecificPlugin(query(".pluginDetails", node)[0], pluginObject, controller);
                             });

                         });

           }

           PluginUpdater.prototype.updateHeader = function()
           {
               this.name.innerHTML = entities.encode(String(this.pluginData[ "name" ]));
               this.type.innerHTML = entities.encode(String(this.pluginData[ "type" ]));
           };

           return Plugin;
       });
