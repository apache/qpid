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
        "dojo/_base/array",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/json",
        "dijit/registry",
        "dojo/text!virtualhostnode/bdb_ha/add.html",
        "dijit/form/ValidationTextBox",
        "dijit/form/RadioButton",
        "dojo/domReady!"],
  function (xhr, parser, array, dom, domConstruct, json, registry, template)
  {
      return {
          show: function(data)
          {
              var that=this;

              this.containerNode = domConstruct.create("div", {innerHTML: template}, data.containerNode);
              parser.parse(this.containerNode).then(function(instances)
              {
                  // lookup field
                  that.groupChoice = registry.byId("addVirtualHostNode.group");
                  that.virtualHostNodeBdbhaTypeFieldsContainer = dom.byId("addVirtualHostNode.bdbha.typeFields");

                  // add callback
                  that.groupChoice.on("change",
                                      function(type)
                                      {
                                        that._groupChoiceChanged(type,
                                                                 that.virtualHostNodeBdbhaTypeFieldsContainer,
                                                                 "qpid/management/virtualhostnode/bdb_ha/add/");
                                      });
              });
          },
          _groupChoiceChanged: function(type, typeFieldsContainer, urlStem)
          {
              var widgets = registry.findWidgets(typeFieldsContainer);
              array.forEach(widgets, function(item) { item.destroyRecursive();});
              domConstruct.empty(typeFieldsContainer);

              if (type)
              {
                  var that = this;
                  require([urlStem + type.toLowerCase() + "/add"],
                      function(TypeUI)
                      {
                          try
                          {
                              TypeUI.show({containerNode:typeFieldsContainer, parent: that});
                          }
                          catch(e)
                          {
                              console.warn(e);
                          }
                      }
                  );
              }
          }
      };
  }
);
