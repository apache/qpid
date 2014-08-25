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
        "dijit/form/Button",
        "dijit/form/ValidationTextBox",
        "dijit/form/CheckBox",
        "dijit/form/NumberSpinner",
        "dojo/domReady!"],
    function (xhr, dom, parser, query, construct, connect, win, event, json, registry, util, properties, updater) {

        function ManagementHttp(containerNode, pluginObject, controller) {
            var node = construct.create("div", null, containerNode, "last");
            var that = this;
            this.name = pluginObject.name;
            xhr.get({
                      url: "plugin/showManagementHttp.html",
                      sync: true,
                      load:  function(data) {
                          node.innerHTML = data;
                          parser.parse(node);

                          that.managementHttpUpdater= new ManagementHttpUpdater(node, pluginObject, controller);
                          that.managementHttpUpdater.update(true);
                          updater.add( that.managementHttpUpdater);

                          var editButton = query(".editPluginButton", node)[0];
                          connect.connect(registry.byNode(editButton), "onClick", function(evt){ that.edit(); });
                      }});
        }

        ManagementHttp.prototype.close = function() {
            updater.remove( this.managementHttpUpdater );
        };

        ManagementHttp.prototype.edit = function() {
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
                    name: "httpBasicAuthenticationEnabled",
                    createWidget: function(plugin) {
                        return new dijit.form.CheckBox({
                          required: false,
                          checked: plugin.httpBasicAuthenticationEnabled,
                          label: "HTTP Basic Authentication Enabled:",
                          name: "httpBasicAuthenticationEnabled"});
                }
              }, {
                    name: "httpsBasicAuthenticationEnabled",
                    createWidget: function(plugin) {
                        return new dijit.form.CheckBox({
                          required: false,
                          checked: plugin.httpsBasicAuthenticationEnabled,
                          label: "HTTPS Basic Authentication Enabled:",
                          name: "httpsBasicAuthenticationEnabled"});
                    }
              }, {
                    name: "httpSaslAuthenticationEnabled",
                    createWidget: function(plugin) {
                        return new dijit.form.CheckBox({
                          required: false,
                          checked: plugin.httpSaslAuthenticationEnabled,
                          label: "HTTP SASL Authentication Enabled:",
                          name: "httpSaslAuthenticationEnabled"});
                    }
              }, {
                    name: "httpsSaslAuthenticationEnabled",
                    createWidget: function(plugin) {
                        return new dijit.form.CheckBox({
                          required: false,
                          checked: plugin.httpsSaslAuthenticationEnabled,
                          label: "HTTPS SASL Authentication Enabled:",
                          name: "httpsSaslAuthenticationEnabled"});
                    }
              }, {
                    name: "sessionTimeout",
                    createWidget: function(plugin) {
                        return new dijit.form.NumberSpinner({
                          invalidMessage: "Invalid value",
                          required: false,
                          value: plugin.sessionTimeout,
                          smallDelta: 1,
                          constraints: {min:1,places:0, pattern: "#####"},
                          label: "Session timeout (s):",
                          name: "sessionTimeout"
                        });
                    }
              }, {
              name: "compressResponses",
              createWidget: function(plugin) {
                  return new dijit.form.CheckBox({
                      required: false,
                      checked: plugin.compressResponses,
                      label: "Compress responses:",
                      name: "compressResponses"});
              }
          }
          ];
          var data = this.managementHttpUpdater.pluginData;
          util.showSetAttributesDialog(
              widgetFactories,
              data,
              "api/latest/plugin/" + encodeURIComponent(data.name),
              "Edit plugin - " + data.name,
              "Plugin",
              "MANAGEMENT-HTTP");
        };

        function ManagementHttpUpdater(node, pluginObject, controller)
        {
            this.controller = controller;
            this.query = "api/latest/plugin/"+encodeURIComponent(pluginObject.name);
            this.name = pluginObject.name;
            this.httpBasicAuthenticationEnabled = query(".httpBasicAuthenticationEnabled", node)[0];
            this.httpsBasicAuthenticationEnabled = query(".httpsBasicAuthenticationEnabled", node)[0];
            this.sessionTimeout = query(".sessionTimeout", node)[0];
            this.httpsSaslAuthenticationEnabled = query(".httpsSaslAuthenticationEnabled", node)[0];
            this.httpSaslAuthenticationEnabled = query(".httpSaslAuthenticationEnabled", node)[0];
            this.compressResponses = query(".compressResponses", node)[0];

        }

        ManagementHttpUpdater.prototype.update = function(syncRequest)
        {
            var that = this;

            function showBoolean(val)
            {
              return "<input type='checkbox' disabled='disabled' "+(val ? "checked='checked'": "")+" />" ;
            }

            xhr.get({url: this.query, sync: syncRequest ? syncRequest : properties.useSyncGet, handleAs: "json"})
                .then(function(data) {
                    that.pluginData = data[0];
                    that.httpBasicAuthenticationEnabled.innerHTML = showBoolean(that.pluginData.httpBasicAuthenticationEnabled);
                    that.httpsBasicAuthenticationEnabled.innerHTML = showBoolean(that.pluginData.httpsBasicAuthenticationEnabled);
                    that.httpsSaslAuthenticationEnabled.innerHTML = showBoolean(that.pluginData.httpsSaslAuthenticationEnabled);
                    that.httpSaslAuthenticationEnabled.innerHTML = showBoolean(that.pluginData.httpSaslAuthenticationEnabled);
                    that.compressResponses.innerHTML = showBoolean(that.pluginData.compressResponses);
                    that.sessionTimeout.innerHTML = that.pluginData.sessionTimeout;
                });

        };

        return ManagementHttp;
    });
