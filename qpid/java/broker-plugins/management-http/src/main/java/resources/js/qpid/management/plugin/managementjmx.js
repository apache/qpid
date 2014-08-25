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
        "dojo/dom",
        "dojo/parser",
        "dojo/query",
        "dojo/dom-construct",
        "dojo/_base/connect",
        "dojo/_base/window",
        "dojo/_base/event",
        "dojo/_base/json",
        "dijit/registry",
        "qpid/common/util",
        "qpid/common/properties",
        "qpid/common/updater",
        "dojo/domReady!"],
    function (xhr, dom, parser, query, construct, connect, win, event, json, registry, util, properties, updater) {

        function ManagementJmx(containerNode, pluginObject, controller) {
            var node = construct.create("div", null, containerNode, "last");
            var that = this;
            this.name = pluginObject.name;
            xhr.get({
                      url: "plugin/showManagementJmx.html",
                      sync: true,
                      load:  function(data) {
                          node.innerHTML = data;
                          parser.parse(node);

                          that.managementJmxUpdater= new ManagementJmxUpdater(node, pluginObject, controller);
                          that.managementJmxUpdater.update(true);
                          updater.add( that.managementJmxUpdater);

                          var editButton = query(".editPluginButton", node)[0];
                          connect.connect(registry.byNode(editButton), "onClick", function(evt){ that.edit(); });
                      }});
        }

        ManagementJmx.prototype.close = function() {
            updater.remove( this.managementJmxUpdater );
        };

        ManagementJmx.prototype.edit = function() {
          var widgetFactories = [{
                  name: "name",
                  createWidget: function(plugin) {
                      return new dijit.form.ValidationTextBox({
                        required: true,
                        value: plugin.name,
                        disabled: true,
                        label: "Name:",
                        regexp: "^[\x20-\x2e\x30-\x7F]{1,255}$",
                        name: "name"});
                  }
              }, {
                    name: "usePlatformMBeanServer",
                    createWidget: function(plugin) {
                        return new dijit.form.CheckBox({
                          required: false,
                          checked: plugin.usePlatformMBeanServer,
                          label: "Use Platform MBean Server:",
                          name: "usePlatformMBeanServer"});
                }
              }
          ];
          var data = this.managementJmxUpdater.pluginData;
          util.showSetAttributesDialog(
              widgetFactories,
              data,
              "api/latest/plugin/" + encodeURIComponent(data.name),
              "Edit plugin - " + data.name,
              "Plugin",
              "MANAGEMENT-JMX");
        };

        function ManagementJmxUpdater(node, pluginObject, controller)
        {
            this.controller = controller;
            this.query = "api/latest/plugin/"+encodeURIComponent(pluginObject.name);
            this.name = pluginObject.name;
            this.usePlatformMBeanServer = query(".usePlatformMBeanServer", node)[0];
        }

        ManagementJmxUpdater.prototype.update = function(syncRequest)
        {
            var that = this;

            function showBoolean(val)
            {
              return "<input type='checkbox' disabled='disabled' "+(val ? "checked='checked'": "")+" />" ;
            }

            xhr.get({url: this.query, sync: syncRequest ? syncRequest : properties.useSyncGet, handleAs: "json"})
                .then(function(data) {
                    that.pluginData = data[0];
                    that.usePlatformMBeanServer.innerHTML = showBoolean(that.pluginData.usePlatformMBeanServer);
                });

        };

        return ManagementJmx;
    });
