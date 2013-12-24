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
        "dojo/string",
        "dojox/html/entities",
        "dojo/query",
        "dijit/registry",
        "dojox/grid/EnhancedGrid",
        "qpid/common/UpdatableStore",
        "qpid/common/formatter",
        "dojo/domReady!"],
    function (xhr, parser, json, entities, query, registry, EnhancedGrid, UpdatableStore, formatter) {

  var fields = ["storePath", "storeType", "desiredState"];

  var buttons = [];

  function findNode(nodeClass, containerNode)
  {
    return query("." + nodeClass, containerNode)[0];
  }

  function Standard(containerNode) {
    var that = this;
    xhr.get({url: "virtualhost/standard/show.html",
      sync: true,
      load:  function(template) {
        that._init(template, containerNode);
      }});
  }

  Standard.prototype.update=function(data)
  {
    for(var i = 0; i < fields.length; i++)
    {
      var name = fields[i];
      this[name].innerHTML = entities.encode(String(data[name]));
    }
  };

  Standard.prototype._init = function(template, containerNode)
  {
    containerNode.innerHTML = template;
    parser.parse(containerNode);
    this._initFields(fields, containerNode);
    for(var i = 0; i < buttons.length; i++)
    {
      var buttonName = buttons[i];
      var buttonNode = findNode(buttonName, containerNode);
      if (buttonNode)
      {
        var buttonWidget = registry.byNode(buttonNode);
        if (buttonWidget)
        {
          this[buttonName] = buttonWidget;
          var handler = this["_onClick_" + buttonName];
          if (handler)
          {
            buttonWidget.on("click", function(evt){
              handler(evt);
            });
          }
          else
          {
            //buttonWidget.set("disabled", true);
          }
        }
      }
    }
  }

  Standard.prototype._initFields = function(fields, containerNode)
  {
    for(var i = 0; i < fields.length; i++)
    {
      this[fields[i]] = findNode(fields[i], containerNode);
    }
  }

  return Standard;
});
