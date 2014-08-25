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
define(["dojo/_base/lang",
        "dojo/_base/xhr",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "qpid/common/util",
        "dojo/store/Memory",
        "dojox/validate/us",
        "dojox/validate/web",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/ComboBox",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dijit/layout/ContentPane",
        "dojox/layout/TableContainer",
        "dojo/domReady!"],
    function (lang, xhr, dom, construct, registry, parser, array, event, json, util) {

        var addAccessControlProvider = {};

        addAccessControlProvider.show = function(accessControlProvider) {
          var fields = [{
              name: "name",
              createWidget: function(accessControlProvider) {
                  return new dijit.form.ValidationTextBox({
                    required: true,
                    value: accessControlProvider.name,
                    disabled: accessControlProvider.name ? true : false,
                    label: "Name*:",
                    regexp: "^[\x20-\x2e\x30-\x7F]{1,255}$",
                    promptMessage: "Name of access control provider.",
                    placeHolder: "name",
                    name: "name"});
              }
          }, {
              name: "type",
              createWidget: function(accessControlProvider) {

                  var typeContainer = construct.create("div");

                  var typeListContainer = new dojox.layout.TableContainer({
                      cols: 1,
                      "labelWidth": "300",
                      customClass: "formLabel",
                      showLabels: true,
                      orientation: "horiz"
                  });

                  typeContainer.appendChild(typeListContainer.domNode);

                  var providers =  [];
                  var fieldSetContainers = {};
                  xhr.get({
                    url: "service/helper?action=ListAccessControlProviderAttributes",
                    handleAs: "json",
                    sync: true
                  }).then(
                  function(data) {
                       var providerIndex = 0;

                       for (var providerType in data) {
                           if (data.hasOwnProperty(providerType)) {
                               providers[providerIndex++] = {id: providerType, name: providerType};

                               var attributes = data[providerType].attributes;
                               var descriptions = data[providerType].descriptions;

                               var layout = new dojox.layout.TableContainer( {
                                   cols: 1,
                                   "labelWidth": "300",
                                   customClass: "formLabel",
                                   showLabels: true,
                                   orientation: "horiz"
                               });

                               for(var i=0; i < attributes.length; i++) {
                                   if ("type" == attributes[i])
                                   {
                                       continue;
                                   }
                                   var labelValue = attributes[i];
                                   if (descriptions && descriptions[attributes[i]])
                                   {
                                       labelValue = descriptions[attributes[i]];
                                   }
                                   var text = new dijit.form.TextBox({
                                       label: labelValue + ":",
                                       name: attributes[i]
                                   });
                                   layout.addChild(text);
                               }

                               typeContainer.appendChild(layout.domNode);
                               fieldSetContainers[providerType] = layout;
                           }
                       }
                });

                var providersStore = new dojo.store.Memory({ data: providers });

                var typeList = new dijit.form.FilteringSelect({
                  required: true,
                  value: accessControlProvider.type,
                  store: providersStore,
                  label: "Type*:",
                  name: "type"});

                typeListContainer.addChild(typeList);

                var onChangeHandler = function onChangeHandler(newValue){
                  for (var i in fieldSetContainers) {
                    var container = fieldSetContainers[i];
                    var descendants = container.getChildren();
                    for(var i in descendants){
                      var descendant = descendants[i];
                      var propName = descendant.name;
                      if (propName) {
                        descendant.set("disabled", true);
                      }
                    }
                    container.domNode.style.display = "none";
                  }
                  var container = fieldSetContainers[newValue];
                  if (container)
                  {
                    container.domNode.style.display = "block";
                    var descendants = container.getChildren();
                    for(var i in descendants){
                      var descendant = descendants[i];
                      var propName = descendant.name;
                      if (propName) {
                        descendant.set("disabled", false);
                      }
                    }
                  }
                };
                typeList.on("change", onChangeHandler);
                onChangeHandler(typeList.value);
                return new dijit.layout.ContentPane({content: typeContainer, style:{padding: 0}});
              }
              }];

          util.showSetAttributesDialog(
              fields,
              accessControlProvider ? accessControlProvider : {},
              "api/latest/accesscontrolprovider" + (name ? "/" + encodeURIComponent(name.name) : ""),
              accessControlProvider ? "Edit access control provider - " + accessControlProvider.name : "Add access control provider",
              "AccessControlProvider",
              accessControlProvider && accessControlProvider.type ? accessControlProvider.type : "AclFile",
              accessControlProvider ? false : true);
        };
        return addAccessControlProvider;
    });