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
  "dojo/_base/array",
  "dijit/Toolbar",
  "dojox/grid/enhanced/_Plugin",
  "dojox/grid/enhanced/plugins/Dialog",
  "dojox/grid/enhanced/plugins/filter/FilterLayer",
  "dojox/grid/enhanced/plugins/filter/FilterDefDialog",
  "dojox/grid/enhanced/plugins/filter/FilterStatusTip",
  "dojox/grid/enhanced/plugins/filter/ClearFilterConfirm",
  "dojox/grid/EnhancedGrid",
  "dojo/i18n!dojox/grid/enhanced/nls/Filter",
  "qpid/common/grid/EnhancedFilterTools"
], function(declare, lang, array, Toolbar, _Plugin,
    Dialog, FilterLayer, FilterDefDialog, FilterStatusTip, ClearFilterConfirm, EnhancedGrid, nls, EnhancedFilterTools){

  // override CriteriaBox#_getColumnOptions to show criteria for hidden columns with EnhancedFilter
  dojo.extend(dojox.grid.enhanced.plugins.filter.CriteriaBox, {
          _getColumnOptions: function(){
                  var colIdx = this.dlg.curColIdx >= 0 ? String(this.dlg.curColIdx) : "anycolumn";
                  var filterHidden = this.plugin.filterHidden;
                  return array.map(array.filter(this.plugin.grid.layout.cells, function(cell){
                          return !(cell.filterable === false || (!filterHidden && cell.hidden));
                  }), function(cell){
                          return {
                                  label: cell.name || cell.field,
                                  value: String(cell.index),
                                  selected: colIdx == String(cell.index)
                          };
                  });
          }
  });

  // Enhanced filter has extra functionality for refreshing, limiting rows, displaying/hiding columns in the grid
  var EnhancedFilter = declare("qpid.common.grid.EnhancedFilter", _Plugin, {
    // summary:
    //    Accept the same plugin parameters as dojox.grid.enhanced.plugins.Filter and the following:
    //
    //    filterHidden: boolean:
    //    Whether to display filtering criteria for hidden columns. Default to true.
    //
    //    defaulGridRowLimit: int:
    //    Default limit for numbers of items to cache in the gris dtore
    //
    //    disableFiltering: boolean:
    //    Whether to disable a filtering including filter button, clear filter button and filter summary.
    //
    //    toolbar: dijit.Toolbar:
    //    An instance of toolbar to add the enhanced filter widgets.


    // name: String
    //    plugin name
    name: "enhancedFilter",

    // filterHidden: Boolean
    //    whether to filter hidden columns
    filterHidden: true,

    constructor: function(grid, args){
      // summary:
      //    See constructor of dojox.grid.enhanced._Plugin.
      this.grid = grid;
      this.nls = nls;

      args = this.args = lang.isObject(args) ? args : {};
      if(typeof args.ruleCount != 'number' || args.ruleCount < 0){
        args.ruleCount = 0;
      }
      var rc = this.ruleCountToConfirmClearFilter = args.ruleCountToConfirmClearFilter;
      if(rc === undefined){
        this.ruleCountToConfirmClearFilter = 5;
      }

      if (args.filterHidden){
          this.filterHidden = args.filterHidden;
      }
      this.defaulGridRowLimit = args.defaulGridRowLimit;
      this.disableFiltering = args.disableFiltering;

      //Install UI components
      var obj = { "plugin": this };

      this.filterBar = ( args.toolbar && args.toolbar instanceof dijit.Toolbar) ? args.toolbar: new Toolbar();

      if (!this.disableFiltering)
      {
          //Install filter layer
          this._wrapStore();

          this.clearFilterDialog = new Dialog({
            refNode: this.grid.domNode,
            title: this.nls["clearFilterDialogTitle"],
            content: new ClearFilterConfirm(obj)
          });

          this.filterDefDialog = new FilterDefDialog(obj);

          nls["statusTipTitleNoFilter"] = "Filter is not set";
          nls["statusTipMsg"] = "Click on 'Set Filter' button to specify filtering conditions";
          this.filterStatusTip = new FilterStatusTip(obj);

          var self = this;
          var toggleClearFilterBtn = function (arg){ self.enhancedFilterTools.toggleClearFilterBtn(arg); };

          this.filterBar.toggleClearFilterBtn = toggleClearFilterBtn;

          this.grid.isFilterBarShown = function (){return true};

          this.connect(this.grid.layer("filter"), "onFilterDefined", function(filter){
            toggleClearFilterBtn(true);
          });

          //Expose the layer event to grid.
          grid.onFilterDefined = function(){};
          this.connect(grid.layer("filter"), "onFilterDefined", function(filter){
              grid.onFilterDefined(grid.getFilter(), grid.getFilterRelation());
          });
      }

      // add extra buttons into toolbar
      this.enhancedFilterTools = new EnhancedFilterTools({
          grid: grid,
          toolbar: this.filterBar,
          filterStatusTip: this.filterStatusTip,
          clearFilterDialog: this.clearFilterDialog,
          filterDefDialog: this.filterDefDialog,
          defaulGridRowLimit: this.defaulGridRowLimit,
          disableFiltering: this.disableFiltering,
          nls: nls
        });

      this.filterBar.placeAt(this.grid.viewsHeaderNode, "before");
      this.filterBar.startup();

    },

    destroy: function(){
      this.inherited(arguments);
      try
      {
        if (this.grid)
        {
          this.grid.unwrap("filter");
          this.grid = null;
        }
        if (this.filterBar)
        {
          this.filterBar.destroyRecursive();
          this.filterBar = null;
        }
        if (this.enhancedFilterTools)
        {
          this.enhancedFilterTools.destroy();
          this.enhancedFilterTools = null;
        }
        if (this.clearFilterDialog)
        {
          this.clearFilterDialog.destroyRecursive();
          this.clearFilterDialog = null;
        }
        if (this.filterStatusTip)
        {
          this.filterStatusTip.destroy();
          this.filterStatusTip = null;
        }
        if (this.filterDefDialog)
        {
          this.filterDefDialog.destroy();
          this.filterDefDialog = null;
        }
        this.args = null;

      }catch(e){
        console.warn("Filter.destroy() error:",e);
      }
    },

    _wrapStore: function(){
      var g = this.grid;
      var args = this.args;
      var filterLayer = args.isServerSide ? new FilterLayer.ServerSideFilterLayer(args) :
        new FilterLayer.ClientSideFilterLayer({
          cacheSize: args.filterCacheSize,
          fetchAll: args.fetchAllOnFirstFilter,
          getter: this._clientFilterGetter
        });
      FilterLayer.wrap(g, "_storeLayerFetch", filterLayer);

      this.connect(g, "_onDelete", lang.hitch(filterLayer, "invalidate"));
    },

    onSetStore: function(store){
      this.filterDefDialog.clearFilter(true);
    },

    _clientFilterGetter: function(/* data item */ datarow,/* cell */cell, /* int */rowIndex){
      return cell.get(rowIndex, datarow);
    }

  });

  EnhancedGrid.registerPlugin(EnhancedFilter);

  return EnhancedFilter;

});
