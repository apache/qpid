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

define([
  "dojo/_base/declare",
  "dojo/_base/event",
  "dojo/_base/array",
  "dojo/_base/lang",
  "dojo/parser",
  "dojo/dom-construct",
  "dojo/query",
  "dijit/registry",
  "dijit/form/Button",
  "dijit/form/CheckBox",
  "dojox/grid/enhanced/plugins/Dialog",
  "dojo/text!../../../grid/showRowNumberLimitDialog.html",
  "dojo/domReady!"
], function(declare, event, array, lang, parser, dom, query, registry, Button, CheckBox, Dialog, template ){


return declare("qpid.management.logs.RowNumberLimitDialog", null, {

  grid: null,
  dialog: null,

  constructor: function(domNode, limitChangedCallback)
  {
      var that = this;
      this.containerNode = dom.create("div", {innerHTML: template});
      parser.parse(this.containerNode).then(function(instances)
      {
        that._postParse(domNode, limitChangedCallback);
      });
  },
  _postParse: function(domNode, limitChangedCallback)
  {
      this.rowNumberLimit = registry.byNode(query(".rowNumberLimit", this.containerNode)[0])
      this.submitButton = registry.byNode(query(".submitButton", this.containerNode)[0]);
      this.closeButton = registry.byNode(query(".cancelButton", this.containerNode)[0]);

      this.dialog = new Dialog({
        "refNode": domNode,
        "title": "Grid Rows Number",
        "content": this.containerNode
      });

      var self = this;
      this.submitButton.on("click", function(e){
          if (self.rowNumberLimit.value > 0)
          {
              try
              {
                  limitChangedCallback(self.rowNumberLimit.value);
              }
              catch(e)
              {
                  console.error(e);
              }
              finally
              {
                  self.dialog.hide();
              }
          }
      });

      this.closeButton.on("click", function(e){self.dialog.hide(); });
      this.dialog.startup();
    },

    destroy: function(){
      this.submitButton.destroy();
      this.closeButton.destroy();
      this.dialog.destroy();
      this.dialog = null;
    },

    showDialog: function(currentLimit){
      this.rowNumberLimit.set("value", currentLimit);
      this.dialog.show();
    }

  });

});
