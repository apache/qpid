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
  "dojo/_base/lang",
  "dojo/_base/html",
  "dojo/query",
  "dojo/dom-construct",
  "dojo/string",
  "dojo/on",
  "dijit/_WidgetBase"
], function(declare, lang, html, query, domConstruct, string, on, _WidgetBase){

return declare("qpid.common.grid.FilterSummary", [_WidgetBase], {

    domNode: null,
    itemName: null,
    filterStatusTip: null,
    grid: null,
    _handle_statusTooltip: null,
    _timeout_statusTooltip: 300,
    _nls: null,

    constructor: function(params)
    {
      this.inherited(arguments);
      this.itemName = params.itemsName;
      this.initialize(params.filterStatusTip, params.grid);
      this._nls = params.nls;
    },

    buildRendering: function(){
      this.inherited(arguments);
      var itemsName = this.itemName || this._nls["defaultItemsName"];
      var message = string.substitute(this._nls["filterBarMsgNoFilterTemplate"], [0, itemsName ]);
      this.domNode = domConstruct.create("span", {innerHTML: message, "class": "dijit dijitReset dijitInline dijitButtonInline", role: "presentation" });
    },

    postCreate: function(){
      this.inherited(arguments);
      on(this.domNode, "mouseenter", lang.hitch(this, this._onMouseEnter));
      on(this.domNode, "mouseleave", lang.hitch(this, this._onMouseLeave));
      on(this.domNode, "mousemove", lang.hitch(this, this._onMouseMove));
    },

    destroy: function()
    {
      this.inherited(arguments);
      this.itemName = null;
      this.filterStatusTip = null;
      this.grid = null;
      this._handle_statusTooltip = null;
      this._filteredClass = null;
      this._nls = null;
    },

    initialize: function(filterStatusTip, grid)
    {
      this.filterStatusTip = filterStatusTip;
      this.grid = grid;
      if (this.grid)
      {
        var filterLayer = grid.layer("filter");
        this.connect(filterLayer, "onFiltered", this.onFiltered);
      }
    },

    onFiltered: function(filteredSize, originSize)
    {
      try
      {
        var itemsName = this.itemName || this._nls["defaultItemsName"],
          msg = "", g = this.grid,
          filterLayer = g.layer("filter");
        if(filterLayer.filterDef()){
          msg = string.substitute(this._nls["filterBarMsgHasFilterTemplate"], [filteredSize, originSize, itemsName]);
        }else{
          msg = string.substitute(this._nls["filterBarMsgNoFilterTemplate"], [originSize, itemsName]);
        }
        this.domNode.innerHTML = msg;
      }
      catch(e)
      {
        // swallow and log exception
        // otherwise grid rendering is screwed
        console.error(e);
      }
    },

    _getColumnIdx: function(coordX){
      var headers = query("[role='columnheader']", this.grid.viewsHeaderNode);
      var idx = -1;
      for(var i = headers.length - 1; i >= 0; --i){
        var coord = html.position(headers[i]);
        if(coordX >= coord.x && coordX < coord.x + coord.w){
          idx = i;
          break;
        }
      }
      if(idx >= 0 && this.grid.layout.cells[idx].filterable !== false){
        return idx;
      }else{
        return -1;
      }
    },

    _setStatusTipTimeout: function(){
        this._clearStatusTipTimeout();
        this._handle_statusTooltip = setTimeout(lang.hitch(this,this._showStatusTooltip),this._timeout_statusTooltip);
    },

    _clearStatusTipTimeout: function(){
      if (this._handle_statusTooltip){
        clearTimeout(this._handle_statusTooltip);
      }
      this._handle_statusTooltip = null;
    },

    _showStatusTooltip: function(){
        this._handle_statusTooltip = null;
        if(this.filterStatusTip){
            this.filterStatusTip.showDialog(this._tippos.x, this._tippos.y, this._getColumnIdx(this._tippos.x));
        }
    },

    _updateTipPosition: function(evt){
        this._tippos = {
            x: evt.pageX,
            y: evt.pageY
        };
     },

     _onMouseEnter: function(e){
         this._updateTipPosition(e);
         if(this.filterStatusTip){
           this._setStatusTipTimeout();
         }
     },

     _onMouseMove: function(e){
       if(this.filterStatusTip){
         this._setStatusTipTimeout();
         if(this._handle_statusTooltip){
             this._updateTipPosition(e);
         }
       }
    },

    _onMouseLeave: function(e){
        this._clearStatusTipTimeout();
    }
  });

});