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
  "dojo/text!../../../grid/showColumnDefDialog.html",
  "dojo/domReady!"
], function(declare, event, array, lang, parser, dom, query, registry, Button, CheckBox, Dialog, template ){


return declare("qpid.common.grid.ColumnDefDialog", null, {

  grid: null,
  containerNode: null,
  _columns: [],
  _dialog: null,

  constructor: function(args){
      var grid = this.grid = args.grid;
      var that = this;
      this.containerNode = dom.create("div", {innerHTML: template});
      parser.parse(this.containerNode).then(function(instances)
      {
          that._postParse();
      });
  },
  _postParse: function()
  {
      var submitButton = registry.byNode(query(".displayButton", this.containerNode)[0]);
      this.closeButton = registry.byNode(query(".cancelButton", this.containerNode)[0]);
      var columnsContainer = query(".columnList", this.containerNode)[0];

      this._buildColumnWidgets(columnsContainer);

      this._dialog = new Dialog({
        "refNode": this.grid.domNode,
        "title": "Grid Columns",
        "content": this.containerNode
      });

      var self = this;
      submitButton.on("click", function(e){self._onColumnsSelect(e); });
      this.closeButton.on("click", function(e){self._dialog.hide(); });

      this._dialog.startup();
    },

    destroy: function(){
      this._dialog.destroyRecursive();
      this._dialog = null;
      this.grid = null;
      this.containerNode = null;
      this._columns = null;
    },

    showDialog: function(){
      this._initColumnWidgets();
      this._dialog.show();
    },

    _initColumnWidgets: function()
    {
      var cells = this.grid.layout.cells;
      for(var i in cells)
      {
        var cell = cells[i];
        this._columns[cell.name].checked = !cell.hidden;
      }
    },

    _onColumnsSelect: function(evt){
      event.stop(evt);
      var grid = this.grid;
      grid.beginUpdate();
      var cells = grid.layout.cells;
      try
      {
        for(var i in cells)
        {
          var cell = cells[i];
          var widget = this._columns[cell.name];
          grid.layout.setColumnVisibility(i, widget.checked);
        }
      }
      finally
      {
        grid.endUpdate();
        this._dialog.hide();
      }
    },

    _buildColumnWidgets: function(columnsContainer)
    {
      var cells = this.grid.layout.cells;
      for(var i in cells)
      {
        var cell = cells[i];
        var widget = new dijit.form.CheckBox({
          required: false,
          checked: !cell.hidden,
          label: cell.name,
          name: this.grid.id + "_cchb_ " + i
        });

        this._columns[cell.name] = widget;

        var div = dom.create("div");
        div.appendChild(widget.domNode);
        div.appendChild(dom.create("span", {innerHTML: cell.name}));

        columnsContainer.appendChild(div);
      }
    }

  });

});
