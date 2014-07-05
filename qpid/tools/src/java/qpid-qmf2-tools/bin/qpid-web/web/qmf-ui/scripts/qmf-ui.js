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

/**
 *
 * This program implements the QMF Console User Interface logic for qmf.html
 *
 * It has dependencies on the following:
 * qmf.css
 * itablet.css
 * iscroll.js
 * jquery.js (jquery-1.7.1.min.js)
 * itablet.js
 * excanvas.js (for IE < 9 only)
 * qpid.js
 *
 * author Fraser Adams
 */

//-------------------------------------------------------------------------------------------------------------------

// Create a new namespace for the qmfui "package".
var qmfui = {};
qmfui.TOUCH_ENABLED = 'ontouchstart' in window && !((/hp-tablet/gi).test(navigator.appVersion));
qmfui.END_EV   = (qmfui.TOUCH_ENABLED) ? "touchend"   : "mouseup";

//-------------------------------------------------------------------------------------------------------------------

/**
 * This class holds the history of various key statistics that may be held for some QMF
 * Management Objects so we may see how the state has changes over a particular time range.
 */
qmfui.Statistics = function(description) {
    this.description = description; // Array describing the contents of each stored statistic
    this.short  = new util.RingBuffer(60);  // Statistics for a 10 minute period (10*60/REFRESH_PERIOD)
    this.medium = new util.RingBuffer(60);  // Statistics for a 1 hour period (Entries are updated every minute)
    this.long   = new util.RingBuffer(144); // Statistics for a 1 day period (Entries are updated every 10 minutes)

    /**
     * Add an item to the end of each statistic buffer.
     * @param item an array containing the current statistics for each property that we want to hold for a
     * Management Object, the last item in the array is the Management Object's update timestamp.
     * As an example for the connection Management Object we would do:
     * stats.put([connection.msgsFromClient, connection.msgsToClient, connection._update_ts]);
     * This approach is a little ugly and not terribly OO, but it's pretty memory efficient, which is 
     * important as there could be lots of Management Objects on a heavily utilised broker.
     */
    this.put = function(item) {
        var TIMESTAMP = item.length - 1; // The timestamp is stored as the last item of each sample.
        var timestamp = item[TIMESTAMP];

        var lastItem = this.short.getLast();
        if (lastItem == null) {
            this.short.put(item); // Update the 10 minute period statistics.
        } else {
            var lastTimestamp = lastItem[TIMESTAMP];
            // 9000000000 is 9 seconds in nanoseconds. If the time delta is less than 9 seconds we hold off adding
            // the sample otherwise the ring buffer will end up holding less than the full 10 minutes worth.
            if ((timestamp - lastTimestamp) >= 9000000000) {
                this.short.put(item); // Update the 10 minute period statistics.
            }
        }

        var lastItem = this.medium.getLast();
        if (lastItem == null) {
            this.medium.put(item); // Update the 1 hour period statistics.
        } else {
            var lastTimestamp = lastItem[TIMESTAMP];
            // 59000000000 is 59 seconds in nanoseconds. We use 59 seconds rather than 60 seconds because the
            // update period has a modest +/i variance around 10 seconds.
            if ((timestamp - lastTimestamp) >= 59000000000) {
                this.medium.put(item); // Update the 1 hour period statistics.
            }
        }

        lastItem = this.long.getLast();
        if (lastItem == null) {
            this.long.put(item); // Update the 1 day period statistics.
        } else {
            var lastTimestamp = lastItem[TIMESTAMP];
            // 599000000000 is 599 seconds in nanoseconds (just short of 10 minutes). We use 599 seconds rather
            // than 600 seconds because the update period has a modest +/ variance around 10 seconds. 
            if ((timestamp - lastTimestamp) >= 599000000000) {
                this.long.put(item); // Update the 1 day period statistics.
            }
        }
    };

    /**
     * This method computes the most recent instantaneous rate for the property specified by the index.
     * For example for the Connection Management Object an index of 1 would represent msgsToClient as
     * described in the comments for put(). Note that the rate that is returned is the most recent
     * instantaneous rate, which means that it uses the samples held in the ring buffer used to hold
     * the ten minute window.
     * @param the index of the property that we wish to obtain the rate for.
     * @return the most recent intantaneous rate in items/s
     */
    this.getRate = function(index) {
        var size = this.short.size();
        if (size < 2) {
            return 0;
        }

        var s1 = this.short.get(size - 2);
        var t1 = s1[s1.length - 1];

        var s2 = this.short.get(size - 1);
        var t2 = s2[s2.length - 1];

        var delta = (t2 == t1) ? 0.0000001 : t2 - t1; // Shouldn't happen, but this tries to avoid divide by zero.
        var rate = ((s2[index] - s1[index]) * 1000000000)/delta
        return rate;
    };
};

//-------------------------------------------------------------------------------------------------------------------
//                      Helper Methods to provide a consistent way to render the UI lists.  
//-------------------------------------------------------------------------------------------------------------------

/**
 * This helper method renders the specified properties of the specifed JavaScript object to the specified html list.
 * @param list jQuery object representing the html list (ul) we wish to populate.
 * @param object the object whose properties we wish to render.
 * @param props an array of properties that we wish to render.
 * @param href optional string specifying URL fragment to e.g. "#graphs?connectionId=" + connectionId.
 */
qmfui.renderObject = function(list, object, props, href, useIndex) {
    iTablet.renderList(list, function(i) {
        var key = props[i];
        var value = object[key];
        if (value == null) { // Only show properties that are actually available.
            return false;
        } else {
            if (href) {
                var anchor = href + "&property=" + i;
                return "<li class='arrow'><a href='" + anchor + "'>" + key + "<p>" + value + "</p></a></li>";
            } else {
                return "<li><a href='#'>" + key + "<p>" + value + "</p></a></li>";
            }
        }
    }, props.length);
};

/**
 * This helper method renders the specified list of html list items (li) to the specified html list.
 * @param list jQuery object representing the html list (ul) we wish to populate.
 * @param array the array of html list items (li) that we wish to render.
 */
qmfui.renderArray = function(list, array) {
    iTablet.renderList(list, function(i) {
        return array[i];
    }, array.length);
};

//-------------------------------------------------------------------------------------------------------------------
//                                            Main Console Class                                             
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the main Console class.
 * This class contains the QMF Console and and caches the core QMF management objects. Caching the getObjects()     
 * results is obviously sensible and helps avoid the temptation to call getObjects() elsewhere which is rather
 * inefficient as it is invoked using AMQP request/response but morever in the case of this UI is is called via     
 * a REST proxy. Caching getObjects() calls also helps to abstract the asynchronous nature of JavaScript as the
 * cache methods can be called synchronously which "feels" a more natural way to get the data.
 *
 * This class is also responsible for initialising the rest of the pages when jQuery.ready() fires and updating
 * them when QMF object updates occur, in other words it might be considered "Main".
 *
 * This class handles the QMF Console Connection lifecycle management. It's worth pointing out that it's fairly
 * subtle and complex particularly due to the asynchronous nature of JavaScript. It's made even more complex
 * by the fact that there are two distinct ways used to decide when to get the QMF Management Objects. The default
 * way is where QMF Event delivery is enabled, in this case the onEvent() method is triggered periodically by
 * the underlying QMF Console's Event dispatcher and in this case the Event dispatcher takes care of reconnection
 * attempts. However if disableEvents is selected then the QMF Management Objects are retrieved via a timed
 * poll. In this case this method must correctly start the pollForData() but must also let it expire if the user
 * selects a new Connection that has QMF Event deliver enabled.
 */
qmfui.Console = new function() {
    /**
     * This is an array of QMF Console Connections that the user is interested in. It gets initiated with the
     * default connection (that is to say the connection that the REST API has been configured to use if no
     * explicit URL has been supplied). Using a Connection URL of "" makes the REST API use its default.
     * This property has been exposed as a "public" property of qmfui.Console because then it becomes possible
     * to "configure" the initial set of consoleConnections via a trivial config.js file containing:
     * qmfui.Console.consoleConnections = [{name: "default", url: ""},{name: "wildcard", url: "0.0.0.0:5672",  
     * connectionOptions: {sasl_mechs:"ANONYMOUS"}, disableEvents: true}];
     * i.e. a JSON array of Console Connection settings.
     */
    this.consoleConnections = [{name: "default", url: ""}];

    var _objects = {};   // A map used to cache the QMF Management Object lists returned by getObjects().
    var _disableEvents = false; // Set for Connections that have Events disabled and thus refresh via timed polling.
    var _polling = false; // Set when the timed polling is active so we can avoid starting it multiple times.
    var _receivedData = false; // This flag is used to tell the difference between a failure to connect and a disconnect.
    var _console = null; // The QMF Console used to retrieve information from the broker.
    var _connection = null;
    var _activeConsoleConnection = 0; // The index of the currently active QMF Console Connection.

    /**
     * Resets the messages that get rendered if the REST API Server or the Qpid broker fail.
     */
    var resetErrorMessages = function() {
        $("#restapi-disconnected").hide();
        $("#broker-disconnected").hide();
        $("#failed-to-connect").hide();
    };

    /**
     * Show Rest API Disconnected Message, hide the others.
     */
    var showRestAPIDisconnected = function() {
        resetErrorMessages();
        $("#restapi-disconnected").show();
    };

    /**
     * Show Broker Disconnected Message, hide the others.
     */
    var showBrokerDisconnected = function() {
        resetErrorMessages();
        $("#broker-disconnected").show();
    };

    /**
     * Show Failed to Connect Message, hide the others.
     */
    var showFailedToConnect = function() {
        resetErrorMessages();
        $("#failed-to-connect").show();
    };

    /**
     * QMF2 EventHandler that we register with the QMF2 Console.
     * @param workItem a QMF2 API WorkItem.
     */
    var onEvent = function(workItem) {
        if (workItem._type == "AGENT_DELETED") {
            var agent = workItem._params.agent;

            if (agent._product == "qpidd" && _connection != null) {
                if (_receivedData) {
                    showBrokerDisconnected();
                } else {
                    showFailedToConnect();
                }
            } else if (agent._product == "qpid.restapi") {
                showRestAPIDisconnected();
            }
        } else {
            _receivedData = true;
            resetErrorMessages();
            if (workItem._type == "EVENT_RECEIVED") {
                qmfui.Events.update(workItem);
            }
        }

        // onEvent() will be called periodically by the broker heartbeat events, so we use that fact to trigger
        // a call to getAllObjects() which will itself trigger a call to updateState() when its results return.
        getAllObjects();
    };

    /**
     * This method is called if startConsole() has been called with disableEvents. In this state
     * the onEvent() method won't be triggered by the QMF2 callback so we need to explicitly poll via a timer.
     */
    var pollForData = function() {
        if (_connection != null && _disableEvents) {
            _polling = true;
            getAllObjects();
            setTimeout(function() {
                pollForData();
            }, qmf.REFRESH_PERIOD);
        } else {
            _polling = false;
        }
    };

    /**
     * This method is called by the failure handler of getAllObjects(). If it is triggered it means there has
     * been some form of Server side disconnection. If this occurs set an error banner then attempt to re-open
     * the Qpid Connection. If the Connection reopens successfully updateState() will start getting called again.
     * This method returns immediately if _disableEvents is false because the underlying QMF Console has its own 
     * reconnection logic in its Event dispatcher, which we only need to replicate if that is disabled.
     * @param xhr the jQuery XHR object.
     */
    var timeout = function(xhr) {
        if (_disableEvents) {
            if (xhr.status == 0) {
                showRestAPIDisconnected();
            } else {
                if (_receivedData) {
                    showBrokerDisconnected();
                } else {
                    showFailedToConnect();
                }
                if (xhr.status == 404 && _connection != null && _connection.open) {
                    _connection.open();
                }
            }
        }
    };

    /**
     * This method is called when getAllObjects() completes, that is to say when all of the asynchronous responses
     * for the Deferred XHR objects returned by getObjects() have all returned successfully. This method triggers
     * the update() method on each of the user interface pages.
     */
    var updateState = function() {
        _receivedData = true;
        resetErrorMessages();

        qmfui.Broker.update();
        qmfui.Connections.update();
        qmfui.Exchanges.update();
        qmfui.Queues.update();
        //qmfui.Links.update();         // TODO
        //qmfui.RouteTopology.update(); // TODO

        // Update sub-pages after main pages as these may require state (such as statistics) set in the main pages.
        qmfui.SelectedConnection.update();
        qmfui.SelectedQueue.update();
        qmfui.QueueSubscriptions.update();
        qmfui.ConnectionSubscriptions.update();
        qmfui.SelectedExchange.update();
        qmfui.Bindings.update();
        qmfui.Graphs.update();
    };

    /**
     * This method retrieves the QmfConsoleData Objects from the real QMF Console via AJAX calls to the REST API.
     * Because the AJAX calls are all asynchronous we use jQuery.when(), which provides a way to execute callback   
     * functions based on one or more objects, usually Deferred objects that represent asynchronous events.
     * See http://api.jquery.com/jQuery.when/ and http://api.jquery.com/deferred.then/
     */
    var getAllObjects = function() {
        $.when(
            _console.getObjects("broker", function(data) {_objects.broker = data;}),
            _console.getObjects("queue", function(data) {_objects.queue = data;}),
            _console.getObjects("exchange", function(data) {_objects.exchange = data;}),
            _console.getObjects("binding", function(data) {_objects.binding = data;}),
            _console.getObjects("subscription", function(data) {_objects.subscription = data;}),
            _console.getObjects("connection", function(data) {_objects.connection = data;}),
//            _console.getObjects("link", function(data) {_objects.link = data;}),
//            _console.getObjects("bridge", function(data) {_objects.bridge = data;}),
            _console.getObjects("session", function(data) {_objects.session = data;})
        ).then(updateState, timeout);
    };

    /**
     * Handle the load event, triggered when the document has completely loaded.
     */
    var loadHandler = function() {
        qmfui.Console.startConsole(0); // Start the default QMF Console Connection.
    };

    /**
     * Handle the unload event, triggered when the document unloads or we navigate off the page. It's not 100%
     * reliable, which is a shame as it's the best way to clear up Server state. If it fails the Server will
     * eventually clear up unused Connections after a timeout period. TODO Opera seems to be especially bad at
     * firing the unloadHandler, not sure why this is, something to look into.
     * @param event the event that triggered the unloadHandler.
     */
    var unloadHandler = function(event) {
        // For mobile Safari (at least) we get pagehide events when navigating off the page, but also when closing via
        // home or locking the device. Fortunately this case has the persisted flag set as we don't want to close then.
        var persisted = (event.type == "pagehide") ? event.originalEvent.persisted : false;
        if (!persisted) {
            qmfui.Console.stopConsole();
        }
    };

    /**
     * Callback handler triggered when _console.addConnection() fails. This should only occur if an actual
     * exception occurs on the REST API Server due to invalid connectionOptions.
     */
    var handleConnectionFailure = function() {
        qmfui.Console.stopConsole();
        showFailedToConnect();
    }

    // ******************************************* Accessor Methods *******************************************

    /**
     * Retrieve the broker Management Object, optionally as a QmfConsoleData Object (with invokeMethod attached).
     * @param makeConsoleData if true attach the invokeMethod method to the returned Management Object.
     * @return the QMF broker Management Object optionally as a QmfConsoleData.
     */
    this.getBroker = function(makeConsoleData) {
        var brokers = _objects.broker;

        if (brokers == null || brokers.length == 0) {
            // Return a fake QmfConsoleData Object with an invokeMethod that calls the callback handler with a
            // response object containing error_text. It is actually pretty uncommon for the brokers array to
            // be empty so this approach allows us to call invokeMethod without lots of checking for broker == null.
            return {invokeMethod: function(name, inArgs, handler) {
                handler({"error_text" : "Could not retrieve broker Management Object"});
            }};
        } else {
            var broker = brokers[0];
            if (makeConsoleData) {
                _console.makeConsoleData(broker);
            }
            return broker;
        }
    };

    /**
     * These methods are basic accessors returning the cached lists of QmfData objects returned by getObjects()
     */
    this.getQueues = function() {
        return _objects.queue;
    };

    this.getExchanges = function() {
        return _objects.exchange;
    };

    this.getBindings = function() {
        return _objects.binding;
    };

    this.getSubscriptions = function() {
        return _objects.subscription;
    };

    this.getConnections = function() {
        return _objects.connection;
    };

    this.getSessions = function() {
        return _objects.session;
    };

/* TODO
    this.getLinks = function() {
        return _objects.link;
    };

    this.getBridges = function() {
        return _objects.bridge;
    };
*/

    /**
     * Calls the underlying QMF Console's makeConsoleData(). This call turns a QmfData object into a QmfConsoleData
     * object, which adds methods such as invokeMethod() to the object.
     * @param the object that we want to turn into a QmfConsoleData.
     */
    this.makeConsoleData = function(object) {
        _console.makeConsoleData(object);
    };

    /**
     * @return the list of QMF Console Connections that the user is interested in. The returned list is a list of
     * objects containing url, name and connectionOptions properties.
     */
    this.getConsoleConnectionList = function() {
        return this.consoleConnections;
    };

    /**
     * Note that in the following it's important to note that there is separation between adding/removing
     * Console Connections and actually starting/stopping Console Connections, this is because a user may wish
     * to add several QMF Console Connections to point to a number of different brokers before actually
     * chosing to connect to a particular broker. Similarly a user may wish to delete a QMF Console Connection
     * from the list (s)he is interested in independently from selecting a new connection.
     */

    /**
     * Append a new Console Connection with the specified url, name and connectionOptions to the end of the
     * list of QMF Console Connections the the user many be interested in. Note that this method *does not*
     * actually start a connection to the new console for that we must call the startConsole() method.
     * The name supplied in this method is simply a user friendly name and is not related to the name that
     * may be applied to the Connection when it is stored on the REST API, that name is really best considered
     * as an opaque "handle" and is intended to be unique for each connection.
     *
     * @param name a user friendly name for the Console Connection.
     * @param url the broker Connection URL in one of the formats supported by the Java ConnectionHelper class
     * namely an AMQP 0.10 URL, an extended AMQP 0-10 URL, a Broker URL or a Java Connection URL.
     * @param connectionOptions a JSON string containing the Connection Options in the same form as used
     * in the qpid::messaging API.
     */
    this.addConsoleConnection = function(name, url, connectionOptions, disableEvents) {
        if (disableEvents) {
            this.consoleConnections.push({name: name, url: url, connectionOptions: connectionOptions,
                                          disableEvents: true});
        } else {
            this.consoleConnections.push({name: name, url: url, connectionOptions: connectionOptions});
        }
    };

    /**
     * Remove the Console Connection specified by the index. Note that this method *does not* actually stop
     * a connection to the console for that we must call the stopConsole() method.
     * @param index the index of the Console Connection that we want to remove.
     */
    this.removeConsoleConnection = function(index) {
        if (_activeConsoleConnection > index) {
            _activeConsoleConnection--;
        }
        this.consoleConnections.splice(index, 1);  // remove a single array item at the specified index.
    };

    /**
     * Actually start the Qpid Connection and QMF Console for the Console Connection stored at the specified index.
     * When the QMF Console successfully starts it will start sending QMF Events and updating the Management
     * Objects automatically, this will in turn cause the User Interface pages to refresh.
     * Alternatively if QMF Event delivery is disabled this method initiates the pollForData().
     * @param index the index of the Console Connection that we wish to start. Index zero is the default Console.
     */
    this.startConsole = function(index) {
        var connection = this.consoleConnections[index];

        // Using a Connection URL of "" makes the REST API use its default configured broker connection.
        var factory = new qpid.ConnectionFactory(connection.url, connection.connectionOptions);
        _connection = factory.createConnection();

        // Initialise QMF Console
        _console = new qmf.Console(onEvent);
        if (connection.disableEvents != null) {
            _disableEvents = true;
            _console.disableEvents();
        } else {
            _disableEvents = false;
        }

        _receivedData = false;
        _console.addConnection(_connection, handleConnectionFailure);
        _activeConsoleConnection = index;

        // If disableEvents is set we have to use a timed poll to get the Management Objects. We check if the polling
        // loop is already running, because if we try and start it multiple times we may get spurious refreshes.
        if (_disableEvents && !_polling) {
            pollForData();
        }
    };

    /**
     * Stops the currently running Console Connection and closes the Qpid Connection. This will result in the
     * underlying Connection object on the REST API Server getting properly cleaned up. It's not essential
     * to call stopConsole() before a call to startConsole() as the server Connection objects will eventually
     * time out, but it's good practice to do it if at all possible.
     */
    this.stopConsole = function() {
        if (_console) {
            _console.destroy();
        }

        if (_connection) {
            _connection.close();
            _connection = null;
        }
    };

    /**
     * @return the index of the currently active (connected) QMF Console Connection.
     */
    this.getActiveConsoleConnection = function() {
        return _activeConsoleConnection;
    };

    // *********************** Initialise class when the DOM loads using jQuery.ready() ***********************
    $(function() {
        // Create a fake logging console for browsers that don't have a real console.log - only for debugging.
        if (!window.console) {
            /* // A slightly hacky console.log() to help with debugging on old versions of IE.
            console = window.open("", "console", "toolbar, menubar, status, width=500, height=500, scrollbars=yes");
            console.document.open("text/plain");
            console.log = function(text) {console.document.writeln(text);};*/

            console = {log: function(text) {}}; // Dummy to avoid bad references in case logging accidentally added.
        }

        // Add a default show handler. Pages that bind update() to show should remove this by doing unbind("show").
        $(".main").bind("show", function() {$("#resource-deleted").hide();});

        // pagehide and unload each work better than the other in certain circumstances so we trigger on both.
        $(window).bind("unload pagehide", unloadHandler);

        // Iterate through each page calling its initialise method if one is present.
        for (var i in qmfui) {
            if (qmfui[i].initialise) {
                qmfui[i].initialise();
            }
        }

        // Send a synthesised click event to the settings-tab element to select the settings page on startup.
        // We check if the left property of the main class is zero, if it is then the sidebar has been expanded
        // to become the main menu (e.g. for mobile devices) otherwise we show the settings page.
        if (parseInt($(".main").css('left'), 10) != 0) {
            $("#settings-tab").click();
        }

        // Hide the splash page.
        $("#splash").hide();
    });

    $(window).load(loadHandler);
}; // End of qmfui.Console definition


//-------------------------------------------------------------------------------------------------------------------
//                                            Configure Settings                                             
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Settings class managing the id="settings" page.
 */
qmfui.Settings = new function() {
    /**
     * Show the Settings page, rendering any dynamic content if necessary.
     */
    var show = function() {
        // Retrieve the currently configured QMF Console Connections.
        var qmfConsoleConnections = qmfui.Console.getConsoleConnectionList();

        iTablet.renderList($("#qmf-console-selector"), function(i) {
            var qmfConsoleConnection = qmfConsoleConnections[i];
            var name = qmfConsoleConnection.name;
            var url = qmfConsoleConnection.url;
            url = (url == null || url == "") ? "" : " (" + url + ")";
            var label = (name == null || name == "") ? url : name + url;
            var checked = (i == qmfui.Console.getActiveConsoleConnection()) ? "checked" : "";

            return "<li class='arrow'><label for='qmf-console" + i + "'>" + label + "</label><input type='radio' id='qmf-console" + i + "' name='qmf-console-selector' value='" + i + "' " + checked + "/><a href='#selected-qmf-console-connection?index=" + i + "'></a></li>";
        }, qmfConsoleConnections.length);

        $("#qmf-console-selector input").change(changeConsole);
    };

    /**
     * If the settings-hide-qmf-objects checkbox gets changed refresh the Queues and Exchanges pages to reflect this.
     */
    var changeHideQmf = function() {
        qmfui.Queues.update();
        qmfui.Exchanges.update();
    };

    /**
     * Handles changes to the Console selection Radio buttons. If a change occurs the Console Connection is stopped
     * and the newly selected Console Connection is started (we chose based on the index into the list)
     */
    var changeConsole = function() {
        qmfui.Console.stopConsole();
        qmfui.Console.startConsole($(this).val());
    };

    this.initialise = function() {
        $("#settings").bind("show", show);
        $("#settings-hide-qmf-objects").change(changeHideQmf);
    };
}; // End of qmfui.Settings definition


/**
 * Create a Singleton instance of the AddConsoleConnection class managing the id="add-console-connection" page.
 */
qmfui.AddConsoleConnection = new function() {
    var submit = function() {
        var consoleURL = $("#console-url");

        // Check that a URL value has been supplied. TODO Probably worth doing some validation that the supplied
        // Connection URL is at least syntactically valid too.
        var url = consoleURL.val();
        if (url == "") {
            consoleURL.addClass("error");
            return;
        } else {
            consoleURL.removeClass("error");
        }

        var name = $("#console-name").val();
        try {
            var connectionOptions = $.parseJSON($("#add-connection-options textarea").val());
            // TODO worth checking that the connection options are valid and in a usable format. Connection Options
            // is still a bit of a work in progressed. though it seems to work fine if sensible options are used.
            qmfui.Console.addConsoleConnection(name, url, connectionOptions, $("#console-disable-events")[0].checked);
            iTablet.location.back();
        } catch(e) {
            setTimeout(function() {
                alert("Connection Options must be entered as a well-formed JSON string.");
                return;
            }, 0);
        }
    };

    this.initialise = function() {
        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#add-console-connection .right.button").bind(qmfui.END_EV, submit);
    };
}; // End of qmfui.AddConsoleConnection definition


/**
 * Create a Singleton instance of the SelectedQMFConsoleConnection class managing the 
 * id="selected-qmf-console-connection" page.
 */
qmfui.SelectedQMFConsoleConnection = new function() {
    var _index = null;
    var _name = "";
    var _url = "";

    /**
     * This method deletes the selected QMFConsoleConnection.
     */
    var deleteHandler = function() {
        // Flag to check if the Console we're trying to delete is the currently selected/connected one.
        var currentlySelected = ($("#qmf-console-selector input[checked]").val() == _index);

        // Text for the confirm dialogue with additional wording if it's currently connected.
        var confirmText = 'Delete QMF Connection "' + _name + '" to ' + _url + '?';
        confirmText += currentlySelected ? "\nNote that this is the current active Connection, so deleting it will cause a reconnection to the default Connection." : ""

        // Wrap in a timeout call because confirm doesn't play nicely with touchend and causes it to trigger twice.
        // Calling confirm within a timeout ensures things are placed correctly onto the event queue.
        setTimeout(function() {
            if (confirm(confirmText) == false) {
                return;
            } else {
                qmfui.Console.removeConsoleConnection(_index);
                // If the QMF Console Connection being deleted is the currently connected one we stop the Console
                // and establish a connection to the default QMF Console Connection.
                if (currentlySelected) {
                    qmfui.Console.stopConsole();
                    qmfui.Console.startConsole(0); // Start the default QMF Console Connection.
                }

                iTablet.location.back(); // Navigate to the previous page.
            }
        }, 0);
    };

    var show = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#selected-qmf-console-connection") {
            return;
        }

        // Get the selected QMFConsoleConnection
        _index = parseInt(data.index); // The parseInt is important! Without it the index lookup gives odd results..
        var qmfConsoleConnections = qmfui.Console.getConsoleConnectionList();
        var qmfConsoleConnection = qmfConsoleConnections[_index];
        _name = qmfConsoleConnection.name;
        _url = qmfConsoleConnection.url;
        var connectionOptions = qmfConsoleConnection.connectionOptions;

        var eventsDisabled = false;
        if (qmfConsoleConnection.disableEvents != null) {
            eventsDisabled = qmfConsoleConnection.disableEvents;
        }

        // If the selected Console is the default one hide the delete button, otherwise show it.
        if (_index == 0) {
            $("#selected-qmf-console-connection .header a.delete").hide();
        } else {
            $("#selected-qmf-console-connection .header a.delete").show();
        }

        // Populate the page header with the Console Connection name/url.
        var urlText = (_url == null || _url == "") ? "" : " (" + _url + ")";
        var label = (_name == null || _name == "") ? urlText : _name + urlText;
        $("#selected-qmf-console-connection .header h1").text(label);

        $("#selected-qmf-console-connection-url p").text(((_url == "") ? 'default' : _url));
        $("#selected-qmf-console-connection-name p").text(((_name == "") ? '""' : _name));
        $("#selected-qmf-console-connection-events-disabled p").text(eventsDisabled);

        if (_url == "") {
            $("#selected-qmf-console-connection-default-info").show();
        } else {
            $("#selected-qmf-console-connection-default-info").hide();
        }

        if (connectionOptions == "") {
            $("#selected-qmf-console-connection-connection-options").hide();
        } else {
            $("#selected-qmf-console-connection-connection-options textarea").val(util.stringify(connectionOptions));
            $("#selected-qmf-console-connection-connection-options").show();
        }
    };

    this.initialise = function() {
        $("#selected-qmf-console-connection").unbind("show").bind("show", show);

        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#selected-qmf-console-connection .header a.delete").bind(qmfui.END_EV, deleteHandler);
    };
}; // End of qmfui.SelectedQMFConsoleConnection definition


//-------------------------------------------------------------------------------------------------------------------
//                                            Broker Information                                             
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Broker class managing the id="broker" page.
 */
qmfui.Broker = new function() {
    /**
     * Convert nanoseconds into hours, minutes and seconds.
     */
    var convertTime = function(ns) {
        var milliSecs = ns/1000000;
        var msSecs = (1000);
        var msMins = (msSecs * 60);
        var msHours = (msMins * 60);
        var numHours = Math.floor(milliSecs/msHours);
        var numMins = Math.floor((milliSecs - (numHours * msHours)) / msMins);
        var numSecs = Math.floor((milliSecs - (numHours * msHours) - (numMins * msMins))/ msSecs);
        numSecs = numSecs < 10 ? numSecs = "0" + numSecs : numSecs;
        numMins = numMins < 10 ? numMins = "0" + numMins : numMins;

        return (numHours + ":" + numMins + ":" + numSecs);
    };

    this.update = function() {
        var broker = qmfui.Console.getBroker();
        broker.uptime = convertTime(broker.uptime); // Convert uptime to a more human readable value.

        qmfui.renderObject($("#broker-list"), broker, ["name", "version", "uptime", "port", "maxConns", "connBacklog", 
                           "dataDir", "mgmtPublish", "mgmtPubInterval", "workerThreads",
                           /* 0.20 publishes many more stats so include them if available */
                           "queueCount", "acquires", "releases", "abandoned", "abandonedViaAlt"]);

        // Render a number of 0.20 statistics in their own subsections to improve readability.

        // Render the Message Input Output Statistics.
        if (broker.msgDepth == null) {
            $("#broker-msgio-container").hide();
        } else {
            $("#broker-msgio-container").show();
            qmfui.renderObject($("#broker-msgio"), broker, ["msgDepth", "msgTotalEnqueues", "msgTotalDequeues"]);
        }

        // Render the Byte Input Output Statistics.
        if (broker.byteDepth == null) {
            $("#broker-byteio-container").hide();
        } else {
            $("#broker-byteio-container").show();
            qmfui.renderObject($("#broker-byteio"), broker, ["byteDepth", "byteTotalEnqueues", "byteTotalDequeues"]);
        }

        var hideDetails = $("#settings-hide-details")[0].checked;

        // Render the Flow-to-disk Statistics.
        if (broker.msgFtdDepth == null || hideDetails) {
            $("#broker-flow-to-disk-container").hide();
        } else {
            $("#broker-flow-to-disk-container").show();
            qmfui.renderObject($("#broker-flow-to-disk"), broker, ["msgFtdDepth", "msgFtdEnqueues", "msgFtdDequeues",
                               "byteFtdDepth", "byteFtdEnqueues", "byteFtdDequeues"]);
        }

        // Render the Dequeue Details.
        if (broker.discardsTtl == null || hideDetails) {
            $("#broker-dequeue-container").hide();
        } else {
            $("#broker-dequeue-container").show();
            qmfui.renderObject($("#broker-dequeue"), broker, ["discardsTtl", "discardsRing", "discardsLvq",       
                               "discardsOverflow", "discardsSubscriber", "discardsPurge", "reroutes"]);
        }
    };

    /**
     * This click handler is triggered by a click on the broker-log-level radio button. It invokes the QMF
     * setLogLevel method to set the log level of the connected broker to debug or normal.
     */
    var setLogLevel = function(e) {
        var level = $(e.target).val();

        // Set to the QMF2 levels for broker debug and normal.
        level = (level == "debug") ? "debug+:Broker" : "notice+";

        var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
        broker.invokeMethod("setLogLevel", {"level": level}, function(data) {
            if (data.error_text) {
                alert(data.error_text);
            }
        });
    };

    this.initialise = function() {
        // Explicitly using a click handler rather than change as it's possible that another instance has changed
        // the log level so we may want to be able to reset it by clicking the currently selected level.
        $("#broker-log-level li input").click(setLogLevel);
    };
}; // End of qmfui.Broker definition


//-------------------------------------------------------------------------------------------------------------------
//                                            Connection Information                                             
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Connections class managing the id="connections" page.
 */
qmfui.Connections = new function() {
    var _connectionMap = {};   // Connections indexed by ObjectId.
    var _sessionMap = {};      // Sessions indexed by ObjectId.
    var _subscriptionMap = {}; // Subscriptions indexed by ObjectId.
    var _queueToSubscriptionAssociations = {}; // 0..* association between Queue and Subscription keyed by Queue ID.

    /**
     * Return the Connection object indexed by QMF ObjectId.
     */
    this.getConnection = function(oid) {
        return _connectionMap[oid];
    };

    /**
     * Return the Session object indexed by QMF ObjectId.
     */
    this.getSession = function(oid) {
        return _sessionMap[oid];
    };

    /**
     * Return the Subscription object indexed by QMF ObjectId.
     */
    this.getSubscription = function(oid) {
        return _subscriptionMap[oid];
    };

    /**
     * Return the Subscription association List indexed by a queue's QMF ObjectId.
     */
    this.getQueueSubscriptions = function(oid) {
        var subs = _queueToSubscriptionAssociations[oid];
        return (subs == null) ? [] : subs; // If it's null set it to an empty array.
    };

    /**
     * The Connections update method includes a number of subtle complexities due to the way QMF Management
     * Objects are associated with each other. For example a Subscription is associated with a single
     * Session however a Session may have zero or more Subscriptions, similarly a Session is associated with
     * a single Connection but a Connection may have zero or more Sessions.
     *
     * The QMF Management Objects maintain the single unidirectional associations, which generally makes sense
     * but an unfortunate side-effect of this is that if one wishes to obtain information about Sessions and
     * Subscriptions related to a given Connection it's somewhat of a pain as one has to do a multi-pass dereference.
     *
     * This method does the dereferencing and creates a 0..* association to Session in each Connection object and
     * a 0..* association to Subscription in each Session object so other pages can avoid their own dereferencing.
     * N.B. these added associations are references to actual Session or Subscription objects and NOT via ObjectIds
     * This is because these associations are not *really* QmfData object properties and only exist within the local
     * memory space of this application, so using actual memory references avoids an additional dereference.
     */
    this.update = function() {
        _subscriptionMap = {}; // Clear _subscriptionMap.
        _sessionMap = {};      // Clear _sessionMap.
        _queueToSubscriptionAssociations = {}; // Clear _queueToSubscriptionAssociations.

        var subscriptions = qmfui.Console.getSubscriptions();
        for (var i in subscriptions) {
            var subscription = subscriptions[i];
            var subscriptionId = subscription._object_id;
            var sessionRef = subscription.sessionRef;
            var queueRef = subscription.queueRef;

            // Create the Session->Subscriptions association array and store it keyed by the sessionRef, when we go
            // to store the actual Session we can retrieve the association and store it as a property of the Session.
            if (_sessionMap[sessionRef] == null) {
                _sessionMap[sessionRef] = [subscription];
            } else {
                _sessionMap[sessionRef].push(subscription);
            }

            // Create the Queue->Subscriptions association array and store it keyed by the queueRef, when we go
            // to store the actual Session we can retrieve the association and store it as a property of the Session.
            if (_queueToSubscriptionAssociations[queueRef] == null) {
                _queueToSubscriptionAssociations[queueRef] = [subscription];
            } else {
                _queueToSubscriptionAssociations[queueRef].push(subscription);
            }

            _subscriptionMap[subscriptionId] = subscription; // Index subscriptions by ObjectId.
        }

        var connectionToSessionAssociations = {};
        var sessions = qmfui.Console.getSessions();
        for (var i in sessions) {
            var session = sessions[i];
            var sessionId = session._object_id;
            var connectionRef = session.connectionRef;

            var subs = _sessionMap[sessionId]; // Retrieve the association array and store it as a property.
            subs = (subs == null) ? [] : subs; // If it's null set it to an empty array.
            session._subscriptions = subs;

            // Create the Connection->Sessions association array and store it keyed by the connectionRef, when we go to
            // store the actual Connection we can retrieve the association and store it as a property of the Connection.
            if (connectionToSessionAssociations[connectionRef] == null) {
                connectionToSessionAssociations[connectionRef] = [session];
            } else {
                connectionToSessionAssociations[connectionRef].push(session);
            }
            _sessionMap[sessionId] = session; // Index sessions by ObjectId.
        }

        // Temporary connections map, we move active connections to this so deleted connections wither and die.
        var temp = {};
        var connections = qmfui.Console.getConnections();
        iTablet.renderList($("#connections-list"), function(i) {
            var connection = connections[i];
            var connectionId = connection._object_id;

            // Look up the previous value for the indexed connection using its objectId.
            var prev = _connectionMap[connectionId];
            var stats = (prev == null) ? new qmfui.Statistics(["msgsFromClient", "msgsToClient"]) : prev._statistics;
            stats.put([connection.msgsFromClient, connection.msgsToClient, connection._update_ts]);
            connection._statistics = stats;

            // Retrieve the association array and store it as a property.
            var sessions = connectionToSessionAssociations[connectionId];
            sessions = (sessions == null) ? [] : sessions; // If it's null set it to an empty array.
            connection._sessions = sessions;
            temp[connectionId] = connection;

            // Calculate the total number of subscriptions for this connection, this is useful because a count of
            // zero indicates that a connection is probably a producer only connection.
            var connectionSubscriptions = 0;
            for (var i in connection._sessions) {
                var session = connection._sessions[i];
                connectionSubscriptions += session._subscriptions.length;
            }

            var address = connection.address + " (" + connection.remoteProcessName + ")";
            if (connectionSubscriptions == 0) {
                return "<li class='arrow'><a href='#selected-connection?id=" + connectionId + "'>" +  address + 
                        "<p>No Subscriptions</p></a></li>";
            } else {
                return "<li class='arrow'><a href='#selected-connection?id=" + connectionId + "'>" +  address + 
                        "</a></li>";
            }
        }, connections.length);

        // Replace the saved statistics with the newly populated temp instance, which only has active objects
        // moved to it, this means that any deleted objects are no longer present.
        _connectionMap = temp;
    };

}; // End of qmfui.Connections definition


/**
 * Create a Singleton instance of the SelectedConnection class managing the id="selected-connection" page.
 */
qmfui.SelectedConnection = new function() {
    var _sessions = []; // Populate this with the ID of matching sessions enabling navigation to sessions.

    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#selected-connection") {
            return;
        }

        // Get the latest statistics update of the selected connection object.
        var connectionId = data.id;
        var connection = qmfui.Connections.getConnection(connectionId);
        if (connection == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            var name = connection.address + " (" + connection.remoteProcessName + ")";
            $("#selected-connection .header h1").text(name);

            // Populate the back button with "Subscription" or "Connections" depending on context
            var backText = data.fromSubscription ? "Subscrip..." : "Connect...";

            // Using $("#selected-connection .header a").text(backText) wipes all child elements so use the following.
            $("#selected-connection .header a")[0].firstChild.nodeValue = backText;

            // Render the connection message statistics to #selected-connection-msgio
            qmfui.renderObject($("#selected-connection-msgio"), connection, ["msgsFromClient", "msgsToClient"], 
                               "#graphs?connectionId=" + connectionId);

            // Render the connection byte statistics to #selected-connection-byteio
            qmfui.renderObject($("#selected-connection-byteio"), connection, ["bytesFromClient", "bytesToClient"]);

            // Render the connection frame statistics to #selected-connection-frameio
            qmfui.renderObject($("#selected-connection-frameio"), connection, ["framesFromClient", "framesToClient"]);

            // Render selected general connection properties to #selected-connection-general.
            qmfui.renderObject($("#selected-connection-general"), connection, ["federationLink", "SystemConnection",
                               "incoming", "authIdentity", "userProxyAuth", "saslMechanism", "saslSsf", "remotePid", 
                               "shadow", "closing", "protocol"]);

            // Render links to the sessions associated with this connection.
            _sessions = connection._sessions;
            var subscribedSessions = $("#selected-connection-subscribed-sessions");
            var unsubscribedSessions = $("#selected-connection-unsubscribed-sessions");
            if (_sessions.length == 0) { // Show a message if there are no sessions at all
                subscribedSessions.hide();
                subscribedSessions.prev().hide();
                unsubscribedSessions.show();
                unsubscribedSessions.prev().show();
                iTablet.renderList(unsubscribedSessions, function(i) {
                    return "<li class='grey'><a href='#'>There are currently no sessions attached to " + 
                            name + "</a></li>";
                });
            } else {
                var subscribed = [];
                var unsubscribed = [];
                for (var i in _sessions) {
                    var session = _sessions[i];
                    var id = session._object_id;
                    var subscriptionCount = session._subscriptions.length;
                    if (subscriptionCount == 0) {
                        unsubscribed.push("<li><a href='#'>" + session.name + "</a></li>");
                    } else {
                        var plural = subscriptionCount > 1 ? " Subscriptions" : " Subscription";
                        subscribed.push("<li class='multiline arrow'><a href='#connection-subscriptions?id=" + id + "'><div>" + 
                                        session.name + "<p class='sub'>" + subscriptionCount + plural + "</p></div></a></li>");
                    }
                }

                if (subscribed.length > 0) {
                    subscribedSessions.show();
                    subscribedSessions.prev().show();
                    qmfui.renderArray(subscribedSessions, subscribed);
                } else {
                    subscribedSessions.hide();
                    subscribedSessions.prev().hide();
                }

                if (unsubscribed.length > 0) {
                    unsubscribedSessions.show();
                    unsubscribedSessions.prev().show();
                    qmfui.renderArray(unsubscribedSessions, unsubscribed);
                } else {
                    unsubscribedSessions.hide();
                    unsubscribedSessions.prev().hide();
                }
            }
        }
    };

    this.initialise = function() {
        $("#selected-connection").unbind("show").bind("show", qmfui.SelectedConnection.update);
    };
}; // End of qmfui.SelectedConnection definition


/**
 * Create a Singleton instance of the ConnectionSubscriptions class managing the id="connection-subscriptions" page.
 * This page is slightly different than most of the others as there can be multiple subscriptions and thus multiple
 * arbitrary lists. Most pages have tried to reuse HTML list items for efficiency but in this page we clear and
 * regenerate the contents of the page div each update as it's simpler than attempting to reuse elements.
 */
qmfui.ConnectionSubscriptions = new function() {
    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#connection-subscriptions") {
            return;
        }

        var sessionId = data.id;
        var session = qmfui.Connections.getSession(sessionId);
        if (session == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            var hideQmfObjects = $("#settings-hide-qmf-objects")[0].checked;

            // Populate the page header with the session name.
            $("#connection-subscriptions .header h1").text(session.name);

            var subscriptions = session._subscriptions;
            var length = subscriptions.length;

            var page = $("#connection-subscriptions .page");
            page.children().remove(); // Clear the contents of the page div.

            for (var i = 0; i < length; i++) {
                var subscription = subscriptions[i];
                var id = subscription.queueRef;
                var queue = qmfui.Queues.getQueue(id);

                if (i == 0) {
                    page.append("<h1 class='first'>Subscription 1</h1>");
                } else {
                    page.append("<h1>Subscription " + (i + 1) + "</h1>");
                }

                var name = $("<ul id='connection-subscription-name" + i + "' class='list'></ul>");
                page.append(name);

                // Render the associated queue name to #connection-subscription-name.
                var isQmfQueue = queue._isQmfQueue;
                iTablet.renderList(name, function(i) {
                    // If the associated Queue is a QMF Queue and hideQmfObjects has been selected render the
                    // Queue name grey and make it non-navigable otherwise render normally.
                    if (isQmfQueue && hideQmfObjects) {
                        return "<li class='grey'><a href='#selected-queue?id=" + id + "&fromSubscriptions=true'>" + 
                                queue.name + "</a></li>";
                    } else {
                        return "<li class='arrow'><a href='#selected-queue?id=" + id + "&fromSubscriptions=true'>" + 
                               queue.name + "</a></li>";
                    }
                });

                page.append("<p/>");

                var list = $("<ul id='connection-subscription" + i + "' class='list'></ul>");
                page.append(list);

                // Render the useful subscription properties to #connection-subscriptions-list.
                qmfui.renderObject(list, subscription, 
                                   ["delivered", "browsing", "acknowledged", "exclusive", "creditMode"]);
            }

            $("#connection-subscriptions").trigger("refresh"); // Make sure touch scroller is up-to-date.
        }
    };

    this.initialise = function() {
        $("#connection-subscriptions").unbind("show").bind("show", qmfui.ConnectionSubscriptions.update);
    };
}; // End of qmfui.ConnectionSubscriptions definition


//-------------------------------------------------------------------------------------------------------------------
//                                              Exchange Information                                             
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Exchanges class managing the id="exchanges" page.
 */
qmfui.Exchanges = new function() {
    var QMF_EXCHANGES = {"qmf.default.direct": true, "qmf.default.topic": true, "qpid.management": true};
    var _exchangeNameMap = {}; // Exchanges indexed by name.
    var _exchangeMap = {};     // Exchanges indexed by ObjectId.

    /**
     * Return the Exchange object for the given QMF ObjectId. The Exchange object contains the latest update
     * of the given object and a RingBuffer containing previous values of key statistics over a 24 hour period.
     */
    this.getExchange = function(oid) {
        return _exchangeMap[oid];
    };

    /**
     * Return the Exchange object with the given name. The Exchange object contains the latest update
     * of the given object and a RingBuffer containing previous values of key statistics over a 24 hour period.
     */
    this.getExchangeByName = function(name) {
        return _exchangeNameMap[name];
    };

    this.update = function() {
        var hideQmfObjects = $("#settings-hide-qmf-objects")[0].checked;

        // We move active exchanges to temp so deleted exchanges wither and die. We can't just do _exchangeMap = {}
        // as we need to retrieve and update statistics from any active (non-deleted) exchange.
        var temp = {};
        _exchangeNameMap = {}; // We can simply clear the map of exchanges indexed by name though.
        var exchanges = qmfui.Console.getExchanges();
        iTablet.renderList($("#exchanges-list"), function(i) {
            var exchange = exchanges[i];

            // Look up the previous value for the indexed exchange using its objectId.
            var prev = _exchangeMap[exchange._object_id];
            var stats = (prev == null) ? new qmfui.Statistics(["msgReceives", "msgRoutes", "msgDrops"]) : 
                                         prev._statistics;

            stats.put([exchange.msgReceives, exchange.msgRoutes, exchange.msgDrops, exchange._update_ts]);
            exchange._statistics = stats;

            var id = exchange._object_id;
            temp[id] = exchange;

            var name = exchange.name;
            name = (name == "") ? "'' (default direct)" : name;

            _exchangeNameMap[name] = exchange;

            if (QMF_EXCHANGES[name] && hideQmfObjects) {
                return false; // Filter out any QMF related exchanges if the settings filter is checked.
            } else {
                return "<li class='arrow'><a href='#selected-exchange?id=" + id + "'>" + name + 
                        "<p>" + exchange.type + "</p></a></li>";
            }
        }, exchanges.length);

        // Replace the saved statistics with the newly populated temp instance, which only has active objects
        // moved to it, this means that any deleted objects are no longer present.
        _exchangeMap = temp;
    };

}; // End of qmfui.Exchanges definition


/**
 * Create a Singleton instance of the AddExchange class managing the id="add-exchange" page.
 */
qmfui.AddExchange = new function() {
    var submit = function() {
        var properties = {};

        if ($("#exchange-durable")[0].checked) {
            properties["durable"] = true;
        } else {
            properties["durable"] = false;
        }

        if ($("#sequence")[0].checked) {
            properties["qpid.msg_sequence"] = 1;
        }

        if ($("#ive")[0].checked) {
            properties["qpid.ive"] = 1;
        }

        properties["exchange-type"] = $("#exchange-type input[checked]").val();

        var alternateExchangeName = $("#add-exchange-additional-alternate-exchange-name p").text();
        if (alternateExchangeName != "None (default)") {
            alternateExchangeName = alternateExchangeName.split(" (")[0]; // Remove the exchange type from the text.
            properties["alternate-exchange"] = alternateExchangeName;
        }

        var exchangeName = $("#exchange-name");
        var name = exchangeName.val();
        if (name == "") {
            exchangeName.addClass("error");
        } else {
            exchangeName.removeClass("error");

            var arguments = {"type": "exchange", "name": name, "properties": properties};
            var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
            broker.invokeMethod("create", arguments, function(data) {
                if (data.error_text) {
                    alert(data.error_text);
                } else {
                    iTablet.location.back();
                }
            });
        }
    };

    var changeType = function(e) {
        var jthis = $(e.target);
        if (jthis.attr("checked")) {
            $("#add-exchange-exchange-type p").text(jthis.siblings("label").text());
        }
    };

    this.initialise = function() {
        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#add-exchange .right.button").bind(qmfui.END_EV, submit);
        $("#exchange-type input").change(changeType);

        // Always initialise to default value irrespective of browser caching.
        $("#direct").click();
    };
}; // End of qmfui.AddExchange definition


/**
 * Create a Singleton instance of the ExchangeSelector class managing the id="exchange-selector" page.
 */
qmfui.ExchangeSelector = new function() {
    // Protected Exchanges are exchanges that we don't permit binding to - default direct and the QMF exchanges.
    var PROTECTED_EXCHANGES = {"": true, "''": true, "qmf.default.direct": true,
                               "qmf.default.topic": true, "qpid.management": true};
    var _id;

    /**
     * This method renders dynamic content in the ExchangeSelector page. This is necessary because the set
     * of exchanges to be rendered may change as exchanges are added or deleted.
     */
    var show = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#exchange-selector") {
            return;
        }

        // We pass in the ID of the list ltem that contains the anchor with an href to exchange-selector.
        _id = data.id;

        if (_id == "#add-binding-exchange-name") {
            $("#exchange-selector .header a").text("Add Bin...");
            $("#exchange-selector .header h1").text("Select Exchange");
        } else if (_id == "#reroute-messages-exchange-name") {
            $("#exchange-selector .header a").text("Reroute...");
            $("#exchange-selector .header h1").text("Select Exchange");
        } else {
            $("#exchange-selector .header a").text("Additio...");
            $("#exchange-selector .header h1").text("Alternate Exchange");
        }

        var exchanges = qmfui.Console.getExchanges();
        var filteredExchanges = [];
        var currentlySelected = $(_id + " p").text();

        // Check the status of any Exchange that the user may have previously selected prior to hitting "Done".
        if (currentlySelected != "None (default)") {
            // Remove the exchange type from the text before testing.
            currentlySelected = currentlySelected.split(" (")[0];
            if (qmfui.Exchanges.getExchangeByName(currentlySelected) == null) { // Check if it has been deleted.
                alert('The currently selected Exchange "' + currentlySelected + '" appears to have been deleted.');
                currentlySelected = "None (default)";
            }
        }

        var checked = (currentlySelected == "None (default)") ? "checked" : "";
        filteredExchanges.push("<li><label for='exchange-selector-exchangeNone'>None (default)</label><input type='radio' id='exchange-selector-exchangeNone' name='exchange-selector' value='None (default)' " + checked + "/></li>");

        var length = exchanges.length;
        for (var i = 0; i < length; i++) {
            var name = exchanges[i].name;
            var type = exchanges[i].type;
            checked = (currentlySelected == name) ? "checked" : "";

            // Filter out default direct and QMF exchanges as we don't want to allow binding to those.
            if (!PROTECTED_EXCHANGES[name]) {
                filteredExchanges.push("<li><label for='exchange-selector-exchange" + i + "'>" + name + " (" + type + ")</label><input type='radio' id='exchange-selector-exchange" + i + "' name='exchange-selector' value='" + name + "' " + checked + "/></li>");
            }
        }

        qmfui.renderArray($("#exchange-selector-list"), filteredExchanges);
        $("#exchange-selector-list input").change(changeExchange);
    };

    /**
     * Event handler for the change event on "#exchange-selector-list input". Note that this is bound "dynamically"
     * in the show handler because the exchange list is created dynamically each time the ExchangeSelector is shown.
     */
    var changeExchange = function(e) {
        var jthis = $(e.target);
        if (jthis.attr("checked")) {
            $(_id + " p").text(jthis.siblings("label").text());
        }
    };

    this.initialise = function() {
        $("#exchange-selector").unbind("show").bind("show", show);
    };
}; // End of qmfui.ExchangeSelector definition


/**
 * Create a Singleton instance of the SelectedExchange class managing the id="selected-exchange" page.
 */
qmfui.SelectedExchange = new function() {
    // System Exchanges are exchanges that should not be deleted so we hide the delete button for those Exchanges.
    var SYSTEM_EXCHANGES = {"''": true, "amq.direct": true, "amq.fanout": true, "amq.match": true, "amq.topic": true, 
                            "qmf.default.direct": true, "qmf.default.topic": true, "qpid.management": true};

    // Protected Exchanges are exchanges that we don't binding to - default direct and the QMF exchanges.
    var PROTECTED_EXCHANGES = {"": true, "''": true, "qmf.default.direct": true,
                               "qmf.default.topic": true, "qpid.management": true};
    var _name = "";

    /**
     * This method deletes the selected exchange by invoking the QMF delete method.
     */
    var deleteHandler = function() {
        // Wrap in a timeout call because confirm doesn't play nicely with touchend and causes it to trigger twice.
        // Calling confirm within a timeout ensures things are placed correctly onto the event queue.
        setTimeout(function() {
            if (confirm('Delete Exchange "' + _name + '"?') == false) {
                return;
            } else {
                var arguments = {"type": "exchange", "name": _name};
                var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
                broker.invokeMethod("delete", arguments, function(data) {
                    if (data.error_text) {
                        alert(data.error_text);
                    } else {
                        iTablet.location.back();
                    }
                });
            }
        }, 0);
    };

    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#selected-exchange") {
            return;
        }

        // Get the latest update of the selected exchange object.
        var exchangeId = data.id;
        var exchange = qmfui.Exchanges.getExchange(exchangeId);
        if (exchange == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            // Populate the page header with the exchange name and type
            _name = exchange.name;
            _name = (_name == "") ? "''" : _name;
            $("#selected-exchange .header h1").text(_name + " (" + exchange.type + ")");

            // If the selected Exchange is a system Exchange hide the delete button, otherwise show it.
            if (SYSTEM_EXCHANGES[_name]) {
                $("#selected-exchange .header a.delete").hide();
            } else {
                $("#selected-exchange .header a.delete").show();
            }

            // Populate the back button with "Exchanges" or "Bindings" depending on context
            var backText = "Exchan...";
            if (data.bindingKey) {
                backText = "Bindings";
                // Ensure that the binding that linked to this page gets correctly highlighted if we navigate back.
                // If default direct add an extra "&" otherwise all ObjectIds will match in a simple string search.
                qmfui.Bindings.setHighlightedBinding(exchangeId + (_name == "''" ? "&" : ""), 
                                                     data.bindingKey);            
            }

            // Using $("#selected-exchange .header a").text(backText) wipes all child elements so use the following.
            $("#selected-exchange .header a")[0].firstChild.nodeValue = backText;

            // Render the bindingCount to #selected-exchange-bindings
            if (exchange.bindingCount == 0) {
                // We don't allow bindings to be added to default direct or QMF Exchanges.
                if (PROTECTED_EXCHANGES[_name]) {
                    iTablet.renderList($("#selected-exchange-bindings"), function(i) {
                        return "<li class='grey'><a href='#'>There are currently no bindings to " + _name + "</a></li>";
                    });
                } else {
                    iTablet.renderList($("#selected-exchange-bindings"), function(i) {
                        return "<li class='pop'><a href='#add-binding?exchangeId=" + exchangeId + "'>Add Binding</a></li>";
                    });
                }
            } else {
                iTablet.renderList($("#selected-exchange-bindings"), function(i) {
                    return "<li class='arrow'><a href='#bindings?exchangeId=" + exchangeId + "'>bindingCount<p>" + 
                            exchange.bindingCount + "</p></a></li>";
                });
            }

            // Render the exchange statistics to #selected-exchange-msgio
            qmfui.renderObject($("#selected-exchange-msgio"), exchange, ["msgReceives", "msgRoutes", "msgDrops"], 
                               "#graphs?exchangeId=" + exchangeId);

            // Render the exchange statistics to #selected-exchange-byteio
            qmfui.renderObject($("#selected-exchange-byteio"), exchange, ["byteReceives", "byteRoutes", "byteDrops"]);

            // Render selected general exchange properties and exchange.declare arguments to #selected-exchange-general.
            keys = ["durable", "autoDelete", "producerCount"];
            var general = [];

            // Render any alternate exchange that may be attached to this exchange. Note that exchange.altExchange
            // is a reference property so we need to dereference it before extracting the exchange name.
            var altExchange = qmfui.Exchanges.getExchange(exchange.altExchange);
            if (altExchange) {
                general.push("<li><a href='#'>altExchange<p>" + altExchange.name + "</p></a></li>");
            }

            for (var i in keys) { // Populate with selected properties.
                var key = keys[i];
                general.push("<li><a href='#'>" + key + "<p>" + exchange[key] + "</p></a></li>");
            }
            for (var i in exchange.arguments) { // Populate with arguments.
                general.push("<li><a href='#'>" + i + "<p>" + exchange.arguments[i] + "</p></a></li>");
            }
            qmfui.renderArray($("#selected-exchange-general"), general);
        }
    };

    this.initialise = function() {
        $("#selected-exchange").unbind("show").bind("show", qmfui.SelectedExchange.update);

        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#selected-exchange .header a.delete").bind(qmfui.END_EV, deleteHandler);
    };
}; // End of qmfui.SelectedExchange definition


//-------------------------------------------------------------------------------------------------------------------
//                                               Queue Information                                               
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Queues class managing the id="queues" page.
 */
qmfui.Queues = new function() {
    var QMF_EXCHANGES = { "qmf.default.direct": true, "qmf.default.topic": true, "qpid.management": true};
    var _queueNameMap = {}; // Queues indexed by name.
    var _queueMap = {};     // Queues indexed by ObjectId.

    /**
     * Return the Queue object for the given QMF ObjectId. The Queue object contains the latest update
     * of the given object and a RingBuffer containing previous values of key statistics over a 24 hour period.
     */
    this.getQueue = function(oid) {
        return _queueMap[oid];
    };

    /**
     * Return the Queue object with the given name. The Queue object contains the latest update
     * of the given object and a RingBuffer containing previous values of key statistics over a 24 hour period.
     */
    this.getQueueByName = function(name) {
        return _queueNameMap[name];
    };

    this.update = function() {
        var hideQmfObjects = $("#settings-hide-qmf-objects")[0].checked;

        // We move active queues to temp so deleted queues wither and die. We can't just do _queueMap = {} as we
        // need to retrieve and update statistics from any active (non-deleted) queue.
        var temp = {}; 
        _queueNameMap = {}; // We can simply clear the map of queues indexed by name though.
        var queues = qmfui.Console.getQueues();
        iTablet.renderList($("#queues-list"), function(i) {
            var queue = queues[i];
            var objectId = queue._object_id;

            // Look up the previous value for the indexed queue using its objectId.
            var prev = _queueMap[objectId];
            var stats = (prev == null) ? new qmfui.Statistics(["msgDepth", "msgTotalEnqueues", "msgTotalDequeues"]) : 
                                         prev._statistics;

            stats.put([queue.msgDepth, queue.msgTotalEnqueues, queue.msgTotalDequeues, queue._update_ts]);

            // Add statistics as an additional property of the queue object.
            queue._statistics = stats;

            /*
             * Check if the queue is associated with a QMF exchange. Because we need to iterate through the
             * bindings in an inner loop we only want to do this once for each queue, however we can't only
             * do it when the queue is missing from the _queueMap, because there's a race condition with QMF
             * properties getting asynchronously returned, so it's possible for a queue to exist without a
             * binding object referencing it existing for a short period, so we don't add the _isQmfQueue
             * property until a binding referencing the queue actually exists.
             */
            if (prev == null || prev._isQmfQueue == null) {
                var bindings = qmfui.Console.getBindings(); // Get the cached list of binding objects.
                for (var i in bindings) {
                    var b = bindings[i];
                    if (b.queueRef == objectId) {
                        var exchange = qmfui.Exchanges.getExchange(b.exchangeRef); // Dereference the exchangeRef.
                        if (exchange != null) {
                            // If a binding referencing the queue and an exchange exists add isQmfQueue state as an 
                            // additional property of the queue object.
                            if (QMF_EXCHANGES[exchange.name]) {
                                queue._isQmfQueue = true;
                                break;
                            } else {
                                queue._isQmfQueue = false;
                            }
                        }
                    }
                }
            } else {
                // Add previous status of _isQmfQueue as an additional property of the queue object.
                queue._isQmfQueue = prev._isQmfQueue;
            }

            temp[objectId] = queue;
            _queueNameMap[queue.name] = queue;

            if (queue._isQmfQueue && hideQmfObjects) {
                return false; // Filter out any QMF related queues if the settings filter is checked.
            } else {
                return "<li class='arrow'><a href='#selected-queue?id=" + objectId + "'>" + queue.name + "</a></li>";
            }
        }, queues.length);

        // Replace the saved statistics with the newly populated temp instance, which only has active objects
        // moved to it, this means that any deleted objects are no longer present.
        _queueMap = temp;
    };

}; // End of qmfui.Queues definition


/**
 * Create a Singleton instance of the AddQueue class managing the id="add-queue" page.
 */
qmfui.AddQueue = new function() {
    var _properties = {};

    var parseIntegerProperty = function(selector, name) {
        var value = $(selector).removeClass("error").val();
        if (value == "") {
            return true; // "" doesn't populate the property, but it's still valid so return true;
        } else {
            if (value.search(/[kKmMgG]/) == (value.length - 1)) { // Does it end in K/M/G
                _properties[name] = value;
                return true;
            } else {
                var integer = parseInt(value);
                if (isNaN(integer)) {
                    $(selector).addClass("error");
                    return false;
                } else {
                    _properties[name] = integer;
                    return true;
                }
            }
        }
    };

    var submit = function() {
        _properties = {};

        if (!parseIntegerProperty("#max-queue-size", "qpid.max_size")) {
            return;
        } else if (!parseIntegerProperty("#max-queue-count", "qpid.max_count")) {
            return;
        } else if (!parseIntegerProperty("#flow-stop-size", "qpid.flow_stop_size")) {
            return;
        } else if (!parseIntegerProperty("#flow-stop-count", "qpid.flow_stop_count")) {
            return;
        } else if (!parseIntegerProperty("#flow-resume-size", "qpid.flow_resume_size")) {
            return;
        } else if (!parseIntegerProperty("#flow-resume-count", "qpid.flow_resume_count")) {
            return;
        }

        if ($("#queue-durable")[0].checked) {
            _properties["durable"] = true;
            if (!parseIntegerProperty("#file-size", "qpid.file_size")) {
                return;
            } else if (!parseIntegerProperty("#file-count", "qpid.file_count")) {
                return;
            }
        } else {
            _properties["durable"] = false;
        }

        var limitPolicy = $("#limit-policy input[checked]").val();
        if (limitPolicy != "none") {
            _properties["qpid.policy_type"] = limitPolicy;
        }

        var orderingPolicy = $("#ordering-policy input[checked]").val();
        if (orderingPolicy == "lvq") {
            _properties["qpid.last_value_queue"] = 1;
        } else if (orderingPolicy == "lvq-no-browse") {
            _properties["qpid.last_value_queue_no_browse"] = 1;
        }

        if (!parseIntegerProperty("#generate-queue-events input[checked]", "qpid.queue_event_generation")) {
            return;
        }

        var alternateExchangeName = $("#add-queue-additional-alternate-exchange-name p").text();
        if (alternateExchangeName != "None (default)") {
            alternateExchangeName = alternateExchangeName.split(" (")[0]; // Remove the exchange type from the text.
            _properties["alternate-exchange"] = alternateExchangeName;
        }

        var queueName = $("#queue-name");
        var name = queueName.val();
        if (name == "") {
            queueName.addClass("error");
        } else {
            queueName.removeClass("error");

            var arguments = {"type": "queue", "name": name, "properties": _properties};
            var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
            broker.invokeMethod("create", arguments, function(data) {
                if (data.error_text) {
                    alert(data.error_text);
                } else {
                    iTablet.location.back();
                }
            });
        }
    };

    var changeLimitPolicy = function(e) {
        var jthis = $(e.target);
        if (jthis.attr("checked")) {
            $("#add-queue-limit-policy p").text(jthis.siblings("label").text());
        }
    };

    var changeOrderingPolicy = function(e) {
        var jthis = $(e.target);
        if (jthis.attr("checked")) {
            var value = jthis.attr("value");
            if (value == "fifo") {
                $("#add-queue-ordering-policy p").text("Fifo (default)");   
            } else if (value == "lvq") {
                $("#add-queue-ordering-policy p").text("LVQ");   
            } else if (value == "lvq-no-browse") {
                $("#add-queue-ordering-policy p").text("LVQ No Browse");   
            }
        }
    };

    var changeQueueEventGeneration = function(e) {
        var jthis = $(e.target);
        if (jthis.attr("checked")) {
            $("#add-queue-generate-queue-events p").text(jthis.siblings("label").text());
        }
    };

    var changeDurable = function(e) {
        var durable = $("#queue-durable")[0].checked;
        var durableList = $("#add-queue-additional-durable-list");
        var hiddenList  = $("#add-queue-additional-hidden-list").hide();

        if (durable) {
            setTimeout(function() {
                iTablet.renderList(durableList);
                setTimeout(function() {
                    $("#file-size").parent().appendTo(durableList);
                    iTablet.renderList(durableList);
                    setTimeout(function() {
                        $("#file-count").parent().appendTo(durableList);
                        iTablet.renderList(durableList);
                        $("#add-queue-additional").trigger("refresh"); // Refresh touch scroller.
                    }, 30);
                }, 30);
            }, 30);

            $("#add-queue-additional-journal-note").show();
        } else {
            setTimeout(function() {
                $("#file-count").parent().appendTo(hiddenList);
                setTimeout(function() {
                    $("#file-size").parent().appendTo(hiddenList);
                    setTimeout(function() {
                        iTablet.renderList(durableList);
                        $("#add-queue-additional").trigger("refresh"); // Refresh touch scroller.
                    }, 30);
                }, 30);
            }, 30);

            $("#add-queue-additional-journal-note").hide();
        }
    };

    this.initialise = function() {
        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#add-queue .right.button").bind(qmfui.END_EV, submit);
        $("#limit-policy input").change(changeLimitPolicy);
        $("#ordering-policy input").change(changeOrderingPolicy);
        $("#generate-queue-events input").change(changeQueueEventGeneration);

        // Always initialise to default value irrespective of browser caching.
        $("#none").click();
        $("#fifo").click();
        $("#generate-no-events").click();

        changeDurable();
        $("#queue-durable").change(changeDurable);
    };
}; // End of qmfui.AddQueue definition


/**
 * Create a Singleton instance of the SelectedQueue class managing the id="selected-queue" page.
 */
qmfui.SelectedQueue = new function() {
    var _name = "";

    // The Queue depth and number of consumers are reported during the Queue delete confirmation process.
    var _depth = 0;
    var _consumers = 0;

    /**
     * This method deletes the selected queue by invoking the QMF delete method.
     */
    var deleteHandler = function() {
        var plural = (_consumers == 1) ? " consumer?" : " consumers?"

        // Wrap in a timeout call because confirm doesn't play nicely with touchend and causes it to trigger twice.
        // Calling confirm within a timeout ensures things are placed correctly onto the event queue.
        setTimeout(function() {
            if (confirm('Delete Queue "' + _name + 
                        '"\nwhich contains ' + _depth + " messages and has " + _consumers + plural) == false) {
                return;
            } else {
                var arguments = {"type": "queue", "name": _name};
                var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
                broker.invokeMethod("delete", arguments, function(data) {
                    if (data.error_text) {
                        alert(data.error_text);
                    } else {
                        iTablet.location.back();
                    }
                });
            }
        }, 0);
    };

    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#selected-queue") {
            return;
        }

        // Get the latest update of the selected queue object.
        var queueId = data.id;
        var queue = qmfui.Queues.getQueue(queueId);
        if (queue == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            _name = queue.name;
            _depth = queue.msgDepth; // Reported to user if Queue delete is selected.
            _consumers = queue.consumerCount; // Reported to user if Queue delete is selected.

            // Populate the page header with the queue name.
            $("#selected-queue .header h1").text(_name);

            // If the selected Queue is a QMF Queue hide the delete button, otherwise show it.
            // Deleting QMF Queues is a bad thing as it will stop any QMF Consoles behaving as the should.
            if (queue._isQmfQueue) {
                $("#selected-queue .header a.delete").hide();
                $("#selected-queue-admin-wrapper").hide();
            } else {
                $("#selected-queue .header a.delete").show();
                $("#selected-queue-admin-wrapper").show();
            }

            // Populate the back button with "Queues", "Bindings" or "Subscriptions" depending on context
            var backText = "Queues";
            if (data.bindingKey) {
                backText = "Bindings";
                // Ensure that the binding that linked to this page gets correctly highlighted if we navigate back.
                qmfui.Bindings.setHighlightedBinding(queueId, data.bindingKey);
            } else if (data.fromSubscriptions) {
                backText = "Subscrip...";
            }

            // Using $("#selected-queue .header a").text(backText) wipes all child elements so use the following.
            $("#selected-queue .header a")[0].firstChild.nodeValue = backText;

            // Render the bindingCount to #selected-queue-bindings
            // There should always be at least one binding to a queue as the default direct exchange is always bound.
            iTablet.renderList($("#selected-queue-bindings"), function(i) {
                return "<li class='arrow'><a href='#bindings?queueId=" + queueId + "'>bindingCount<p>" + 
                        queue.bindingCount + "</p></a></li>";
            });

            // Render the queue statistics to #selected-queue-msgio
            qmfui.renderObject($("#selected-queue-msgio"), queue, ["msgDepth", "msgTotalEnqueues", "msgTotalDequeues"], 
                               "#graphs?queueId=" + queueId);

            // Render the queue statistics to #selected-queue-byteio
            qmfui.renderObject($("#selected-queue-byteio"), queue, ["byteDepth", "byteTotalEnqueues",       
                               "byteTotalDequeues"]);

            // Render selected general queue properties and queue.declare arguments to #selected-queue-general.
            keys = ["durable", "autoDelete", "exclusive", "unackedMessages", "acquires", "releases", 
                    "messageLatency", "messageLatencyAvg", "consumerCount", "flowStopped", "flowStoppedCount"];
            var general = [];

            // Render any alternate exchange that may be attached to this queue. Note that queue.altExchange
            // is a reference property so we need to dereference it before extracting the exchange name.
            var altExchange = qmfui.Exchanges.getExchange(queue.altExchange);
            if (altExchange) {
                general.push("<li><a href='#'>altExchange<p>" + altExchange.name + "</p></a></li>");
            }

            for (var i in keys) { // Populate with selected properties.
                var key = keys[i];
                var value = queue[key];
                if (value != null) {
                    general.push("<li><a href='#'>" + key + "<p>" + value + "</p></a></li>");
                }
            }
            for (var i in queue.arguments) { // Populate with arguments.
                general.push("<li><a href='#'>" + i + "<p>" + queue.arguments[i] + "</p></a></li>");
            }
            qmfui.renderArray($("#selected-queue-general"), general);

            var hideDetails = $("#settings-hide-details")[0].checked;

            // Render the Flow-to-disk Statistics.
            if (queue.msgFtdDepth == null || hideDetails) {
                $("#selected-queue-flow-to-disk-container").hide();
            } else {
                $("#selected-queue-flow-to-disk-container").show();
                qmfui.renderObject($("#selected-queue-flow-to-disk"), queue, ["msgFtdDepth", "msgFtdEnqueues", 
                                   "msgFtdDequeues", "byteFtdDepth", "byteFtdEnqueues", "byteFtdDequeues"]);
            }

            // Render the Dequeue Details.
            if (queue.discardsTtl == null || hideDetails) {
                $("#selected-queue-dequeue-container").hide();
            } else {
                $("#selected-queue-dequeue-container").show();
                qmfui.renderObject($("#selected-queue-dequeue"), queue, ["discardsTtl", "discardsRing", "discardsLvq", 
                                   "discardsOverflow", "discardsSubscriber", "discardsPurge", "reroutes"]);
            }

            // Render links to the subscriptions associated with this queue to #selected-queue-subscriptions.
            // Unfortunately the subscription name isn't especially useful so find the associated connection
            // and display the connection address instead.
            var subscriptions = qmfui.Connections.getQueueSubscriptions(queueId);
            if (subscriptions.length == 0) {
                iTablet.renderList($("#selected-queue-subscriptions"), function(i) {
                    return "<li class='grey'><a href='#'>There are currently no subscriptions to " + _name + "</a></li>";
                });
            } else {
                iTablet.renderList($("#selected-queue-subscriptions"), function(i) {
                    var subscription = subscriptions[i];
                    var id = subscription._object_id;

                    // The subscription.sessionRef really should be present, but the Java Broker does not yet correctly
                    // populate the association between Subscription and Session so we need this defensive block.
                    if (subscription.sessionRef != null) {
                        var session = qmfui.Connections.getSession(subscription.sessionRef);
                        var connection = qmfui.Connections.getConnection(session.connectionRef);
                        var address = connection.address + " (" + connection.remoteProcessName + ")";
                        return "<li class='arrow'><a href='#queue-subscriptions?id=" + id + "'>" + address + "</a></li>";
                    } else {
                        return "<li class='arrow'><a href='#queue-subscriptions?id=" + id + "'>" + subscription.name + "</a></li>";
                    }
                }, subscriptions.length);
            }

            // We have to dynamically render the admin list so that we can attach the queueId to the URL.
            var admin = [];
            admin.push("<li class='arrow pop'><a href='#purge-queue?queueId=" + queueId + "'>Purge</a></li>");
            admin.push("<li class='arrow pop'><a href='#reroute-messages?queueId=" + queueId + "'>Reroute Messages</a></li>");
            admin.push("<li class='arrow pop'><a href='#move-messages?queueId=" + queueId + "'>Move Messages</a></li>");
            qmfui.renderArray($("#selected-queue-admin"), admin);
        }
    };

    this.initialise = function() {
        $("#selected-queue").unbind("show").bind("show", qmfui.SelectedQueue.update);

        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#selected-queue .header a.delete").bind(qmfui.END_EV, deleteHandler);
    };
}; // End of qmfui.SelectedQueue definition


/**
 * Create a Singleton instance of the QueueSubscriptions class managing the id="queue-subscriptions" page.
 */
qmfui.QueueSubscriptions = new function() {
    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#queue-subscriptions") {
            return;
        }

        var subscriptionId = data.id;
        var subscription = qmfui.Connections.getSubscription(subscriptionId);
        if (subscription == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            var session = qmfui.Connections.getSession(subscription.sessionRef);
            session = session ? session : {};
            var connection = qmfui.Connections.getConnection(session.connectionRef);
            connection = connection ? connection : {};

            // The connection.address should be present but for the 0.20 Java Broker it is not yet populated
            // so we need to do some defensive code to check if it's set and if not render subscription.name.
            var name = connection.address ? connection.address + " (" + connection.remoteProcessName + ")" :
                                            subscription.name;

            var connectionId = connection._object_id;

            // Populate the page header with the address of the connection associated with the subscription.
            $("#queue-subscriptions .header h1").text(name);

            iTablet.renderList($("#queue-subscriptions-connection"), function(i) {
                if (connectionId) {
                    return "<li class='arrow'><a href='#selected-connection?id=" + connectionId + 
                            "&fromSubscription=true'>" + name + "</a></li>";
                } else {
                    return "<li><a href='#'>Connection is Unknown</a></li>";
                }
            });

            // subscription.sessionRef should be present but for the 0.20 Java Broker it is not yet populated
            // so we need to do some defensive code to check if it's set and if not render "Session is Unknown".
            if (subscription.sessionRef) {
                // Render the useful session properties to #queue-subscriptions-session
                qmfui.renderObject($("#queue-subscriptions-session"), session, ["name", "framesOutstanding", 
                                   "unackedMessages", "channelId", "maxClientRate", "clientCredit"]);
            } else {
                iTablet.renderList($("#queue-subscriptions-session"), function(i) {
                    return "<li><a href='#'>Session is Unknown</a></li>";
                });
            }

            // Render the useful subscription properties to #queue-subscriptions-subscription
            qmfui.renderObject($("#queue-subscriptions-subscription"), subscription, ["name", "delivered", "browsing", 
                               "acknowledged", "exclusive", "creditMode"]);
        }
    };

    this.initialise = function() {
        $("#queue-subscriptions").unbind("show").bind("show", qmfui.QueueSubscriptions.update);
    };
}; // End of qmfui.QueueSubscriptions definition


//-------------------------------------------------------------------------------------------------------------------
//                                               Queue Admin                                              
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the PurgeQueue class managing the id="purge-queue" page.
 */
qmfui.PurgeQueue = new function() {
    /**
     * Actually purge the messages using the QMF purge method on the Queue Management Object.
     */
    var submit = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#purge-queue") {
            return;
        }

        var selector = $("#purge-queue-request-number");
        var value = selector.val();
        var messageCount = 0;
        if (value != "") {
            messageCount = parseInt(value);
            if (isNaN(messageCount)) {
                selector.addClass("error");
                return false;
            }
        }

        selector.removeClass("error");

        var queueId = data.queueId;
        var queue = qmfui.Queues.getQueue(queueId);
        qmfui.Console.makeConsoleData(queue); // Make queue a QmfConsoleData object with an invokeMethod method.

        // Wrap in a timeout call because confirm doesn't play nicely with touchend and causes it to trigger twice.
        // Calling confirm within a timeout ensures things are placed correctly onto the event queue.
        var countText = (messageCount == 0) ? "all" : messageCount;
        setTimeout(function() {
            if (confirm('Purge ' + countText + ' messages from "' + queue.name + '"?') == false) {
                return; 
            } else {
                var arguments = {"request": messageCount};

                queue.invokeMethod("purge", arguments, function(data) {
                    if (data.error_text) {
                        alert(data.error_text);
                    } else {
                        iTablet.location.back();
                    }
                });
            }
        }, 0);
    };

    this.initialise = function() {
        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#purge-queue .right.button").bind(qmfui.END_EV, submit);
    };
}; // End of qmfui.PurgeQueue definition


/**
 * Create a Singleton instance of the RerouteMessages class managing the id="reroute-messages" page.
 */
qmfui.RerouteMessages = new function() {
    /**
     * Actually reroute the messages using the QMF reroute method on the Queue Management Object.
     */
    var submit = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#reroute-messages") {
            return;
        }

        var selector = $("#reroute-messages-request-number");
        var value = selector.val();
        var messageCount = 0;
        if (value != "") {
            messageCount = parseInt(value);
            if (isNaN(messageCount)) {
                selector.addClass("error");
                return false;
            }
        }

        selector.removeClass("error");

        var queueId = data.queueId;
        var queue = qmfui.Queues.getQueue(queueId);
        qmfui.Console.makeConsoleData(queue); // Make queue a QmfConsoleData object with an invokeMethod method.

        var useAltExchange = $("#reroute-messages-use-alternate-exchange")[0].checked;
        var countText = (messageCount == 0) ? "all" : messageCount;

        var exchangeName = $("#reroute-messages-exchange-name p").text();
        if (exchangeName != "None (default)") {
            exchangeName = exchangeName.split(" (")[0]; // Remove the exchange type from the text.
        }

        var exchangeText = useAltExchange ? "Alternate Exchange?" : exchangeName + "?";
        // Wrap in a timeout call because confirm doesn't play nicely with touchend and causes it to trigger twice.
        // Calling confirm within a timeout ensures things are placed correctly onto the event queue.
        setTimeout(function() {
            if (confirm('Reroute ' + countText + ' messages from "' + queue.name + '" to ' + exchangeText) == false) {
                return; 
            } else {
                var arguments = {"request": messageCount, "useAltExchange": useAltExchange};
                if (!useAltExchange) {
                    arguments["exchange"] = exchangeName;
                }

                queue.invokeMethod("reroute", arguments, function(data) {
                    if (data.error_text) {
                        alert(data.error_text);
                    } else {
                        iTablet.location.back();
                    }
                });
            }
        }, 0);
    };

    /**
     * This method is the change handler for the use alternate exchange switch, it is used to show or hide the
     * exchange selector widget. This handler is also bound to "show" because the state of the alternate exchange
     * switch is cached in some browsers so triggering on change alone wouldn't handle that.
     */
    var changeUseAlternateExchange = function() {
        if ($("#reroute-messages-use-alternate-exchange")[0].checked) {
            $("#reroute-messages-use-selected-exchange").hide();
        } else {
            $("#reroute-messages-use-selected-exchange").show();
        }
    };

    this.initialise = function() {
        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#reroute-messages .right.button").bind(qmfui.END_EV, submit);
        $("#reroute-messages-use-alternate-exchange").change(changeUseAlternateExchange);
        $("#reroute-messages").unbind("show").bind("show", changeUseAlternateExchange);
    };
}; // End of qmfui.RerouteMessages definition


/**
 * Create a Singleton instance of the RerouteMessages class managing the id="move-messages" page.
 */
qmfui.MoveMessages = new function() {
    var _sourceQueueName = "";
    var _destinationQueueName = "";
    var _sourceQueue = {};

    /**
     * Actually reroute the messages using the QMF reroute method on the Queue Management Object.
     */
    var submit = function() {
        // The queueMoveMessages method returns an InvalidParameter Exception if called when srcQueue has msgDepth == 0
        // This is pretty confusing as the parameters *are* actually OK. https://issues.apache.org/jira/browse/QPID-4543 
        // has been raised on this but the following is some defensive logic to provide a more helpful warning.
        if (_sourceQueue.msgDepth == 0) {
            setTimeout(function() {
                alert("Can't call Move Messages on a queue with a msgDepth of zero");
                return false;
            }, 0);
        } else {
            var selector = $("#move-messages-request-number");
            var value = selector.val();
            var messageCount = 0;
            if (value != "") {
                messageCount = parseInt(value);
                if (isNaN(messageCount)) {
                    selector.addClass("error");
                    return false;
                }
            }

            selector.removeClass("error");

            // Wrap in a timeout call because confirm doesn't play nicely with touchend and causes it to trigger twice.
            // Calling confirm within a timeout ensures things are placed correctly onto the event queue.
            var countText = (messageCount == 0) ? "all" : messageCount;
            setTimeout(function() {
                if (confirm('Move ' + countText + ' messages from "' + _sourceQueueName + '" to "' + _destinationQueueName + '"?') == false) {
                    return; 
                } else {
                    var arguments = {"srcQueue": _sourceQueueName, "destQueue": _destinationQueueName, "qty": messageCount};
                    var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
                    broker.invokeMethod("queueMoveMessages", arguments, function(data) {
                        if (data.error_text) {
                            alert(data.error_text);
                        } else {
                            iTablet.location.back();
                        }
                    });
                }
            }, 0);
        }
    };

    /**
     * This method renders the main move-messages page when it is made visible by the show event being triggered.
     */
    var show = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#move-messages") {
            return;
        }

        var queueId = data.queueId;
        _sourceQueue = qmfui.Queues.getQueue(queueId);
        _sourceQueueName = (_sourceQueue == null) ? "" : _sourceQueue.name;
        _destinationQueueName = $("#move-messages-queue-name p").text();

        if (_sourceQueueName == _destinationQueueName) {
            $("#move-messages-queue-name p").text("None (default)");
        } else {
            $("#move-messages-queue-name p").text(_destinationQueueName);
        }
    };

    this.getSourceQueueName = function() {
        return _sourceQueueName;
    };

    this.initialise = function() {
        $("#move-messages").unbind("show").bind("show", show);

        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#move-messages .right.button").bind(qmfui.END_EV, submit);
    };
}; // End of qmfui.MoveMessages definition


//-------------------------------------------------------------------------------------------------------------------
//                                       Generic Bindings Rendering Page                                               
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Bindings class managing the id="bindings" page.
 */
qmfui.Bindings = new function() {
    // Protected Exchanges are exchanges that we don't permit unbinding from - default direct and the QMF exchanges.
    var PROTECTED_EXCHANGES = {"": true, "''": true, "qmf.default.direct": true,
                               "qmf.default.topic": true, "qpid.management": true};
    var _highlightedObject = null;
    var _highlightedObjectKey = null;

    /**
     * This method is used to render the binding information for headers and XML exchanges which need a little
     * bit more effort than just rendering the binding key. We use <p class="title"> and <p class="sub">
     * to give fairly neat formatting.
     *
     * @param exchange the exchange that the binding is bound to.
     * @param binding the binding that we wish to render.
     */
    var render = function(exchange, binding) {
        if (exchange.type == "headers") {
            // Arguments *should* be returned, but set to empty object if not to protect subsequent code.
            var arguments = binding.arguments ? binding.arguments : {"x-match": "any"};
            var headers = "<p class='title'>x-match: " + arguments["x-match"] + "</p>";
            for (var key in arguments) {
                if (key != "x-match") {
                    headers = headers + "<p class='sub'>" + key + ": " + arguments[key] + "</p>";
                }
            }
            return headers;
        } else if (exchange.type == "xml") {
            var arguments = binding.arguments;
            var xquery = "<p class='title'>xquery:</p>";
            xquery = xquery + "<p class='sub'>" + arguments["xquery"] + "</p>";
            return xquery;
        } else {
            return "&nbsp;";
        }
    };

    /**
     * This method confirms the unbind request and invokes the QMF delete binding method on the broker.
     *
     * @param exchangeName the exchange that we wish to unbind from.
     * @param queueName the queue that we wish to unbind from.
     * @param bindingKey the binding key that we wish to unbind from.
     */
    var unbind = function(exchangeName, queueName, bindingKey) {
        var bindingIdentifier = exchangeName + "/" + queueName;
        if (bindingKey != "") {
            bindingIdentifier = bindingIdentifier + "/" + bindingKey;
        }

        if (confirm('Delete Binding "' + bindingIdentifier + '"?') == false) {
            return;
        } else {
            var arguments = {"type": "binding", "name": bindingIdentifier};
            var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
            broker.invokeMethod("delete", arguments, function(data) {
                if (data.error_text) {
                    alert(data.error_text);
                }
            });
        }
    };

    /**
     * This handler is triggered by clicking a <li class="clickable-icon">. Unfortunately there's bit more work
     * to do because we only want to respond if the actual icon has been clicked so we need to work out the
     * mouse or tap position within the li and check it's less than the icon width.
     * Once we're happy that we've clicked the icon we need to work out which binding the <li> relates to. The
     * approach to this is a little messy and involves scraping some of the html associated with the <li>
     */
    var clickHandler = function(e) {
        var target = e.target;
        var jthis = $(target).closest("ul li.clickable-icon");

        if (jthis.length != 0) {
            var ICON_WIDTH = 45; // The width of the icon image plus some padding.
            var offset = Math.ceil(jthis.offset().left);
            var x = (e.pageX != null) ? e.pageX - offset : // Mouse position.
                    (e.originalEvent != null) ? e.originalEvent.targetTouches[0].pageX - offset : 0; // Touch pos.

            if (x < ICON_WIDTH) {
                var bindingKey = jthis.text().split("]")[0];
                bindingKey = bindingKey.split("[")[1];
                var href = jthis.children("a:first").attr("href"); 
                href = href.replace(window.location, "");

                if (href.indexOf("#selected-exchange") == 0) {
                    var queue = $("#bindings .header h1").text().split(" bindings")[0];
                    var exchange = jthis.find("p:last").text();
                    unbind(exchange, queue, bindingKey);

                } else {
                    var queue = jthis.find("p:last").text();
                    var exchange = $("#bindings .header h1").text().split(" bindings")[0];
                    unbind(exchange, queue, bindingKey);
                }
            }
        }
    };

    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#bindings") {
            return;
        }

        // Get the latest update of the selected object.
        var queueId = data.queueId;
        var exchangeId = data.exchangeId;
        var object = queueId ? qmfui.Queues.getQueue(queueId) : qmfui.Exchanges.getExchange(exchangeId);

        if (object == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            var name = object.name;
            name = (name == "") ? "''" : name;

            // Populate the page header with the object name.
            $("#bindings .header h1").text(name + " bindings");

            // Populate the back button with "Queues" or "Bindings" depending on context
            var backText = "Exchange";

            if (queueId) {
                backText = "Queue";
                $("#bindings").addClass("queue");
            } else {
                $("#bindings").removeClass("queue");
            }

            // Using $("#bindings .header a").text(backText) wipes all child elements so use the following.
            $("#bindings .header a")[0].firstChild.nodeValue = backText;

            // Ensure the correct item on the previous page is set active so it gets highlighted when we slide back.
            if (queueId) {
                $("#selected-queue-bindings").children("li").addClass("active");
                $("#selected-exchange-bindings").children("li").removeClass("active");

                // Add queueId to add-binding URL so add-binding page can automatically populate the queue name.
                iTablet.renderList($("#bindings-add-binding"), function(i) {
                    return "<li class='pop'><a href='#add-binding?queueId=" + queueId + "'>Add Binding</a></li>";
                });
            } else {
                $("#selected-queue-bindings").children("li").removeClass("active");
                $("#selected-exchange-bindings").children("li").addClass("active");

                // Add exchangeId to add-binding URL so add-binding page can automatically populate the exchange name.
                iTablet.renderList($("#bindings-add-binding"), function(i) {
                    return "<li class='pop'><a href='#add-binding?exchangeId=" + exchangeId + "'>Add Binding</a></li>";
                });
            }

            // Note that we don't allow bindings to be added to the default direct exchange, QMF exchanges or
            // queues that are bound to QMF exchanges as doing so may have undesired consequenses.
            if (PROTECTED_EXCHANGES[name] || object._isQmfQueue) {
                $("#bindings-add-binding").hide();
                $("#bindings .page h1").addClass("first");
            } else {
                $("#bindings-add-binding").show();
                $("#bindings .page h1").removeClass("first");
            }

            // Render selected queue's bindings to #queue-details-bindings.
            var bindings = qmfui.Console.getBindings();
            var binding = [];
            for (var i in bindings) {
                var b = bindings[i];
                if (b.queueRef == queueId) {
                    var exchange = qmfui.Exchanges.getExchange(b.exchangeRef); // Dereference exchangeRef
                    if (exchange != null) {
                        var ename = exchange.name;
                        if (ename == "") {
                            // Note that we don't allow bindings to be deleted from default direct exchange.
                            binding.push("<li class='arrow'><a href='#selected-exchange?id=" + 
                                         b.exchangeRef + "&bindingKey=" + b.bindingKey + "'>" +
                                         "bind [" + b.bindingKey + "] => <p>''</p></a></li>");
                        } else {
                            var text = render(exchange, b);

                            // Note that we don't allow bindings to be deleted from QMF exchanges.
                            if (PROTECTED_EXCHANGES[ename]) {
                                binding.push("<li class='multiline arrow'><a href='#selected-exchange?id=" + 
                                             b.exchangeRef + "&bindingKey=" + b.bindingKey + "'>" +
                                             "<div>bind [" + b.bindingKey + "] =></div>" +
                                             "<div>" + text + "<p>" + ename + "</p></div></a></li>");
                            } else {
                                binding.push("<li class='multiline arrow clickable-icon'><a class='delete' href='#selected-exchange?id=" + 
                                            b.exchangeRef + "&bindingKey=" + b.bindingKey + "'>" +
                                            "<div>bind [" + b.bindingKey + "] =></div>" +
                                            "<div>" + text + "<p>" + ename + "</p></div></a></li>");
                            }
                        }
                    }
                } else if (b.exchangeRef == exchangeId) {
                    var queue = qmfui.Queues.getQueue(b.queueRef); // Dereference queueRef
                    if (queue != null) {
                        var qname = queue.name;
                        var ename = object.name; // object is actually exchange in this case.
                        var text = render(object, b);

                        // Note that we don't allow bindings to be deleted from default direct or QMF exchanges.
                        if (PROTECTED_EXCHANGES[ename]) {
                            binding.push("<li class='multiline arrow'><a href='#selected-queue?id=" + 
                                         b.queueRef + "&bindingKey=" + b.bindingKey + "'>" + 
                                         "<div>bind [" + b.bindingKey + "] =></div>" +
                                         "<div>" + text + "<p class='fullwidth'>" + qname + "</p></div></a></li>");

                        } else {
                            binding.push("<li class='multiline arrow clickable-icon'><a class='delete' href='#selected-queue?id=" + 
                                         b.queueRef + "&bindingKey=" + b.bindingKey + "'>" + 
                                         "<div>bind [" + b.bindingKey + "] =></div>" +
                                         "<div>" + text + "<p class='fullwidth'>" + qname + "</p></div></a></li>");
                        }
                    }
                }
            }

            if (binding.length == 0) {
                iTablet.renderList($("#bindings-list"), function(i) {
                    return "<li class='grey'><a href='#'>There are currently no bindings to " + name + "</a></li>";
                });
            } else {
                qmfui.renderArray($("#bindings-list"), binding);

                // If _highlightedObject has been set by qmfui.SelectedQueue or qmfui.SelectedExchange search for
                // the list item that would have caused navigation to that page and set it active, which causes
                // it to be given a highlight that fades as we navigate back to qmfui.Bindings.
                if (_highlightedObject) {
                    $("#bindings-list li").each(function() {
                        var li = $(this);
                        // Find the HTML that contains the highlightedObject string and highlightedObjectKey string.
                        var html = li.html();
                        if (html.search(_highlightedObject) != -1 && html.search(_highlightedObjectKey) != -1) {
                            li.addClass("active");
                        }
                    });

                    _highlightedObject = null;
                    _highlightedObjectKey = null;
                }
            }
        }
    };

    /**
     * This method is called by qmfui.SelectedQueue or qmfui.SelectedExchange to allow qmfui.Bindings to set the
     * correct list item highlighting to allow it to fade out as we navigate back to qmfui.Bindings. This is necessary
     * because we maintain a singleton qmfui.Bindings page and as such its state will get modified as we navigate
     * through multiple queue->binding->exchange->binding etc. Setting the name of the most recent page navigated
     * to in this method allows the fading to be handled correctly.
     */
    this.setHighlightedBinding = function(name, bindingKey) {
        _highlightedObject = name;
        _highlightedObjectKey = "bindingKey=" + bindingKey; // Binding keys are always rendered within square braces.
    };

    this.initialise = function() {
        $("#bindings").unbind("show").bind("show", qmfui.Bindings.update);

        // Using a click handler here doesn't seem to have the 300ms delay that occurs when using click handlers
        // attached to anchors (hence why those use END_EV). It's *probably* because this is a delegating handler.
        // JavaScript alert() can interfere with touchend so it's generally better to use click() if it's possible
        // to do so without it causing that irritating delay!!
        $("#bindings-list").click(clickHandler);
    };
}; // End of qmfui.Bindings definition


/**
 * Create a Singleton instance of the AddBinding class managing the id="add-binding" page.
 */
qmfui.AddBinding = new function() {
    var _queueName = "";
    var _exchangeName = "";
    var _exchangeType = "";
    var _properties = {};

    /**
     * Actually add the new binding using the QMF create method.
     */
    var submit = function() {
        if (_exchangeType == "headers") {
            _properties["x-match"] = $("#x-match input[checked]").val();
        } else if (_exchangeType == "xml") {
            _properties = {};
            var textarea = $("#add-xml-binding textarea");
            var xquery = textarea.val();
            if (xquery == "") {
                textarea.addClass("error");
                return;
            } else {
                textarea.removeClass("error");
                _properties["xquery"] = xquery;
            }
        } else {
            _properties = {};
        }

        var bindingKey = $("#add-binding-key-name").val();
        var bindingIdentifier = _exchangeName + "/" + _queueName;
        if (bindingKey != "") {
            bindingIdentifier = bindingIdentifier + "/" + bindingKey;
        }

        // Before we actually add the new binding check to see if a binding with specified bindingIdentifier currently
        // exists. It's a slight faff, but the Qpid broker doesn't currently detect this condition.
        var duplicateKey = false;
        var bindings = qmfui.Console.getBindings();
        for (var i in bindings) {
            var b = bindings[i];
            // If a binding with the chosen key is found check if the queue and exchange names match.
            if (b.bindingKey == bindingKey) {
                var exchange = qmfui.Exchanges.getExchange(b.exchangeRef); // Dereference the exchangeRef.
                if (exchange != null && exchange.name == _exchangeName) {
                    var queue = qmfui.Queues.getQueue(b.queueRef); // Dereference the queueRef.
                    if (queue != null && queue.name == _queueName) {
                        duplicateKey = true;
                        break;
                    }
                }
            }
        }

        if (duplicateKey) {
            alert('A binding with the identifier "' + bindingIdentifier + '" aleady exists.');
        } else {
            var arguments = {"type": "binding", "name": bindingIdentifier, "properties": _properties};
            var broker = qmfui.Console.getBroker(true); // Retrieve broker QmfConsoleData object.
            broker.invokeMethod("create", arguments, function(data) {
                if (data.error_text) {
                    alert(data.error_text);
                } else {
                    iTablet.location.back();
                }
            });
        }

        // Delete the x-match and xquery properties. Note that the other properties are retained, this is
        // deliberate because it's quite often the case that one may wish to add several headers bindings
        // that may only differ slightly, it's pretty quick to delete match values that aren't needed.
        delete _properties["x-match"];
        delete _properties["xquery"];
    };

    /**
     * This method renders the main add-binding page when it is made visible by the show event being triggered.
     * It tries to be fairly clever by populating either the queue name or exchange name depending on where
     * add-binding was navigated from. If a queueId or exchangeId isn't available navigation is added to the
     * queue-selector or exchange-selector page by adding the arrow class to add-binding-queue-name or
     * add-binding-exchange-name. This method then checks the exchange type and provides additional rendering
     * necessary for XML or Headers exchanges.
     */
    var show = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#add-binding") {
            return;
        }

        var queueId = data.queueId;
        var exchangeId = data.exchangeId;

        if (queueId) {
            var queue = qmfui.Queues.getQueue(queueId);
            _queueName = (queue == null) ? "" : queue.name;
            _exchangeName = $("#add-binding-exchange-name p").text();
            if (_exchangeName != "None (default)") {
                _exchangeName = _exchangeName.split(" (")[0]; // Remove the exchange type from the text.
            }

            var exchange = qmfui.Exchanges.getExchangeByName(_exchangeName);
            _exchangeType = (exchange == null) ? "" : exchange.type;

            $("#add-binding-queue-name").removeClass("arrow");
            $("#add-binding-exchange-name").addClass("arrow");
        } else {
            _queueName = $("#add-binding-queue-name p").text();
            var exchange = qmfui.Exchanges.getExchange(exchangeId);
            _exchangeName = (exchange == null) ? "" : exchange.name;
            _exchangeType = (exchange == null) ? "" : exchange.type;

            $("#add-binding-queue-name").addClass("arrow");
            $("#add-binding-exchange-name").removeClass("arrow");
        }

        var typeText = (_exchangeType == "") ? "" : " (" + _exchangeType + ")";

        $("#add-binding-queue-name p").text(_queueName);
        $("#add-binding-exchange-name p").text(_exchangeName + typeText);

        if (_exchangeType == "headers") {
            // Render the properties and navigation needed to populate Headers bindings. These are dynamically
            // populated and updated as Headers key/value properties get added or deleted.
            $("#add-binding div.page h1").show().text("Headers");
            $("#add-headers-binding").show();
            $("#add-xml-binding").hide();

            var list = [];
            list.push("<li class='arrow'><a href='#x-match'>Match<p>" + $("#x-match input[checked]").val() + "</p></a></li>");

            for (var i in _properties) {
                var key = i;
                var value = _properties[i];
                // Note we add key/value to the query part of the URL too to let showHeaderMatch() populate the values.
                list.push("<li class='arrow clickable-icon'><a class='delete' href='#add-header-match?key=" + key + "&value=" + value + "'>" + key + "<p>" + value + "</p></a></li>");
            }

            list.push("<li class='arrow'><a href='#add-header-match'>Add...</a></li>");
            qmfui.renderArray($("#add-headers-binding"), list);
        } else if (_exchangeType == "xml") {
            $("#add-binding div.page h1").show().text("XML");
            $("#add-xml-binding").show();
            $("#add-headers-binding").hide();
        } else {
            $("#add-binding div.page h1").hide();
            $("#add-headers-binding").hide();
            $("#add-xml-binding").hide();
        }
    };

    /**
     * This method renders the add-header-match page when its show event is triggered. This page
     * needs a show handler because it needs to be dynamically updated with any previously selected key/value pairs.
     */
    var showHeaderMatch = function() {
        var location = iTablet.location;
        var data = location.data;
        var key = "";
        var value = "";

        if (data != null && location.hash == "#add-header-match") {
            key = data.key;
            value = data.value;
        }

        $("#header-match-key").val(key);
        $("#header-match-value").val(value);
    };

    /**
     * Adds a a header match value from the binding being populated.
     * This is triggered by the "Done" button on the add-header-match page. There's some validation logic
     * in place to ensure that a key and value are both supplied.
     */
    var addHeaderMatch = function() {
        var key = $("#header-match-key");
        var keyVal = key.val();
        var value = $("#header-match-value");
        var valueVal = value.val();

        if (keyVal == "") {
            key.addClass("error");
        } else {
            key.removeClass("error");
        }

        if (valueVal == "") {
            value.addClass("error");
        } else {
            value.removeClass("error");
        }

        if (keyVal != "" && valueVal != "") {
            _properties[keyVal] = valueVal;
            iTablet.location.back();
        }
    };

    /**
     * Removes a header match value from the binding being populated.
     * This handler is triggered by clicking a <li class="clickable-icon">. Unfortunately there's bit more work
     * to do because we only want to respond if the actual icon has been clicked so we need to work out the
     * mouse or tap position within the li and check it's less than the icon width.
     * Once we're happy that we've clicked the icon we need to work out which binding the <li> relates to. The
     * approach to this is a little messy and involves scraping some of the html associated with the <li>
     */
    var removeHeaderMatch = function(e) {
        var target = e.target;
        var jthis = $(target).closest("ul li.clickable-icon");

        if (jthis.length != 0) {
            var ICON_WIDTH = 45; // The width of the icon image plus some padding.
            var offset = Math.ceil(jthis.offset().left);
            var x = (e.pageX != null) ? e.pageX - offset : // Mouse position.
                    (e.originalEvent != null) ? e.originalEvent.targetTouches[0].pageX - offset : 0; // Touch pos.

            if (x < ICON_WIDTH) {
                var key = jthis.children("a:first")[0].firstChild.nodeValue;
                delete _properties[key];
                $("#add-binding").trigger("show");
            }
        }
    };

    this.initialise = function() {
        $("#add-binding").unbind("show").bind("show", show);
        $("#add-header-match").unbind("show").bind("show", showHeaderMatch);

        // Using END_EV avoids the 300ms delay responding to anchor click events that occurs on mobile browsers.
        $("#add-binding .right.button").bind(qmfui.END_EV, submit);
        $("#add-header-match .right.button").bind(qmfui.END_EV, addHeaderMatch);

        // Always initialise to default value irrespective of browser caching.
        $("#x-match-all").click();

        // Using a click handler here doesn't seem to have the 300ms delay that occurs when using click handlers
        // attached to anchors (hence why those use END_EV). It's *probably* because this is a delegating handler.
        // JavaScript alert() can interfere with touchend so it's generally better to use click() if it's possible
        // to do so without it causing that irritating delay!!
        $("#add-headers-binding").click(removeHeaderMatch);
    };
}; // End of qmfui.AddBinding definition


/**
 * Create a Singleton instance of the QueueSelector class managing the id="queue-selector" page.
 */
qmfui.QueueSelector = new function() {
    var _id;

    /**
     * This method renders dynamic content in the ExchangeSelector page. This is necessary because the set
     * of exchanges to be rendered may change as exchanges are added or deleted.
     */
    var show = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#queue-selector") {
            return;
        }

        // We pass in the ID of the list ltem that contains the anchor with an href to exchange-selector.
        _id = data.id;

        var sourceQueueName = "";
        if (_id == "#add-binding-queue-name") {
            $("#queue-selector .header a").text("Add Bin...");
        } else if (_id == "#move-messages-queue-name") {
            $("#queue-selector .header a").text("Move Me...");
            sourceQueueName = qmfui.MoveMessages.getSourceQueueName(); // Use this to filter out that queue name.
        }

        var queues = qmfui.Console.getQueues();
        var filteredQueues = [];
        var currentlySelected = $(_id + " p").text();

        // Check the status of any Queue that the user may have previously selected prior to hitting "Done".
        if (currentlySelected != "None (default)") {
            if (qmfui.Queues.getQueueByName(currentlySelected) == null) { // Check if it has been deleted.
                alert('The currently selected Queue "' + currentlySelected + '" appears to have been deleted.');
                currentlySelected = "None (default)";
            }
        }

        var checked = (currentlySelected == "None (default)") ? "checked" : "";
        filteredQueues.push("<li><label for='queue-selector-queueNone'>None (default)</label><input type='radio' id='queue-selector-queueNone' name='queue-selector' value='None (default)' " + checked + "/></li>");

        var length = queues.length;
        for (var i = 0; i < length; i++) {
            var name = queues[i].name;
            // We do getQueueByName(name) because the _isQmfQueue property is a "fake" property added by the
            // qmfui.Queues class, it's only stored in QMF objects held in the getQueueByName() and getQueue() caches.
            var isQmfQueue = qmfui.Queues.getQueueByName(name)._isQmfQueue;
            checked = (currentlySelected == name) ? "checked" : "";

            // Filter out queues bound to QMF exchanges as we don't want to allow additional binding to those.
            if (!isQmfQueue && (name != sourceQueueName)) {
                filteredQueues.push("<li><label for='queue-selector-queue" + i + "'>" + name + "</label><input type='radio' id='queue-selector-queue" + i + "' name='queue-selector' value='" + name + "' " + checked + "/></li>");
            }
        }

        qmfui.renderArray($("#queue-selector-list"), filteredQueues);
        $("#queue-selector-list input").change(changeQueue);
    };

    /**
     * Event handler for the change event on "#queue-selector-list input". Note that this is bound "dynamically"
     * in the show handler because the queue list is created dynamically each time the QueueSelector is shown.
     */
    var changeQueue = function(e) {
        var jthis = $(e.target);
        if (jthis.attr("checked")) {
            $(_id + " p").text(jthis.siblings("label").text());
        }
    };

    this.initialise = function() {
        $("#queue-selector").unbind("show").bind("show", show);
    };
}; // End of qmfui.QueueSelector definition.


//-------------------------------------------------------------------------------------------------------------------
//                                          Generic Graph Rendering Page                                               
//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Graphs class managing the id="graphs" page.
 */
qmfui.Graphs = new function() {
    var IS_IE = (navigator.appName == "Microsoft Internet Explorer");
	var IE_VERSION = IS_IE ? /MSIE (\d+)/.exec(navigator.userAgent)[1] : -1;

    var SECONDS_AS_NANOS = 1000000000; // One second represented in nanoseconds.
    var MILLIS_AS_NANOS  = 1000000; // One millisecond represented in nanoseconds.
    var HEIGHT = 300; // Canvas height (including radiused borders).
    var BORDER = 10;

    var _ctx = null;

    var _radius = new Image();
    _radius.src = "/itablet/images/ie/radius-10px-sprite.png";

    /**
     * qmfui is pretty much browser neutral as browser quirks have been taken care of by jQuery and the 
     * iTablet framework but canvas support is an edge case. Wrapping a canvas in a <li> doesn't seem to
     * work (<li> is where most of the fake border radius stuff is done), so for canvas we simply use the
     * canvas rendering itself and use drawImage() to render radius-10px-sprite.png. We use IE_VERSION
     * from iTablet to detect the IE version as we only draw borders for IE 7 & 8 as IE9 has fake border-radius
     * support and for IE6 radiused borders haven't been done at all because life is too short......
     */
    var drawBorderRadius = function(context) {
        if (IE_VERSION == 8 || IE_VERSION == 7) {
            var width = context.canvas.width;
            var height = context.canvas.height;

            // Draw the border lines.
            context.beginPath();
            // Drawing mid point of a pixel e.g. starting at 0.5 is important for getting one pixel lines.
            context.rect(0.5, 0.5, width - 2, height - 2); 
            context.strokeStyle = "black";
            context.stroke(); // Draw new path

            // Render the radiused borders from the radius-10px-sprite.png sprite using canvas drawImage().
            context.drawImage(_radius, 0, 0, 10, 10, 0, 0, 10, 10);
            context.drawImage(_radius, 0, 10, 10, 10, 0, height - 10, 10, 10);
            context.drawImage(_radius, 10, 0, 10, 10, width - 10, 0, 10, 10);
            context.drawImage(_radius, 10, 10, 10, 10, width - 10, height - 10, 10, 10);
        }
    };


    /**
     * Aaaargh. Another IE edge case!!
     * IE7 has very quirky behaviour - using $("#graphs-time-selector").innerWidth() is unreliable, it
     * fails when page is initially shown and calling it also seems to cause the page to take a long
     * time to re-render on resize. Using the width of body doesn't have that issue but getting the
     * css left value of graphs is unreliable too!!! so I've just coded the values of LEFT & PAGE_WIDTH.
     */
    var getWidth = function() {
        if (IE_VERSION == 7) {
            var LEFT = 251; // .main css left,
            var PAGE_WIDTH = 0.9; // .page 100% - padding left + right
            var width = $("body").outerWidth();
            width = Math.floor((width - LEFT) * PAGE_WIDTH - 0.5);
            return width;
        } else {
            return ($("#graphs-time-selector").innerWidth() - 2); // The -2 compensates for the border width.
        }
    };

    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#graphs") {
            return;
        }

        // Get the latest update of the selected object and populate the text of the back button.
        var object = null;
        var backText = "Unknown";

        if (data.queueId) {
            object = qmfui.Queues.getQueue(data.queueId);
            backText = "Queue";
            $("#graphs").removeClass("exchange connection");
        } else if (data.exchangeId) {
            object = qmfui.Exchanges.getExchange(data.exchangeId);
            backText = "Exchange";
            $("#graphs").addClass("exchange").removeClass("connection");
        } else if (data.connectionId) {
            object = qmfui.Connections.getConnection(data.connectionId);
            backText = "Connect...";
            $("#graphs").addClass("connection").removeClass("exchange");
        }

        if (object == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            var property = data.property; // An index to the particular property to be displayed.
            var statistics = object._statistics;

            if (statistics == null) {
                return;
            }

            var description = statistics.description[property]; // Lookup the description of the property.
            var isRate = (description == "msgDepth") ? false : true; // Is the statistic a rate.

            // Populate the page header with the property being graphed.
            var header = (object.name == null) ? object.address + " " + description : object.name + " " + description;
            $("#graphs .header h1").text(header);

            // Using $("#graphs .header a").text(backText) wipes all child elements so use the following.
            $("#graphs .header a")[0].firstChild.nodeValue = backText;

            // Populate the graph title with the property being graphed.
            if (isRate) {
                description += " " + statistics.getRate(property).toFixed(2) + "/sec"
            } else if (!statistics.short.isEmpty()) {
                description += " " + statistics.short.getLast()[property];
            }
            $("#graphs .page h1").text(description);

            var width = getWidth();
            if (_ctx) {
                if (width != _ctx.canvas.width) {
                    _ctx.canvas.width = width;
                }

                _ctx.clearRect(0, 0, width, HEIGHT); // Clear previous image.
                drawBorderRadius(_ctx); // Draw fake border radius on old versions of IE.

                _ctx.beginPath(); // Start drawing path for grid.
                for (var i = BORDER + 0.5; i < width; i += ((width - (BORDER*2))/10)) { // Draw vertical lines
                    _ctx.moveTo(i, BORDER);
                    _ctx.lineTo(i, HEIGHT - BORDER);
                }

                for (var i = BORDER + 0.5; i < HEIGHT; i += ((HEIGHT - (BORDER*2))/10)) { // Draw horizontal lines
                    _ctx.moveTo(BORDER, i);
                    _ctx.lineTo(width - BORDER, i);
                }
                _ctx.strokeStyle = "#dddddd";
                _ctx.stroke(); // Draw grid.
            }

            var stats = statistics.short; // 10 mins
            var period = 600*SECONDS_AS_NANOS; // 600 seconds (ten minutes) in nanoseconds.
            var interval = $("#graphs-time-selector input:radio[checked]").val();
            if (interval == "oneHour") {
                stats = statistics.medium; // 1 hr
                period = 3600*SECONDS_AS_NANOS; // 3600 seconds (one hour) in nanoseconds.

            } else if (interval == "oneDay") {
                stats = statistics.long; // 1 day
                period = 24*3600*SECONDS_AS_NANOS; // 24*3600 seconds (one day) in nanoseconds.
            }

            var size = stats.size();
            var isMinimumSize = isRate ? size > 1 : size > 0;

            if (isMinimumSize && _ctx) {
                /* 
                 * For reasons of memory efficiency statistics are stored in ring buffers and attached to certain
                 * management objects (queue, exchange & connection) we only record statistics for certain properties
                 * of these management objects. For efficiency each item stored in the ring buffer is an array
                 * containing the statistics for each stored property with the last array item being the update
                 * timestamp of the management object. Thus the timestamp for each statistic sample is the last
                 * item (length - 1) of the array. Getting the length of any item held in the stats ring buffer
                 * gives us the index to use to lookup the timestamp, we use getLast() for convenience get(0) would
                 * be fine too. The isMinimumSize test ensures we have at least one item in the stats ring buffer.
                 */
                var TIMESTAMP = stats.getLast().length - 1;

                var curtime = (+new Date() * MILLIS_AS_NANOS); // Current time represented in nanoseconds.
                var minimumTimestamp = curtime - period; // Items with timestamps less than this aren't in this period.
                var limit = isRate ? size - 1 : size; // Maximum number of sample points.
                var graph = [limit]; // Create an array to hold the data we'll put into the graph plot.
                var count = 0; // This will be the actual number of sample points that fall within this period.
                var i = 0; // Index into statistics circular buffer.

                // Skip over statistics older than the graph period. Mostly this won't happen, but for things like
                // iOS where the browser may sleep/hibernate there may be a considerable gap between samples.
                while ((i < limit) && (stats.get(i)[TIMESTAMP] < minimumTimestamp)) i++;

                var maxValue = 0;
                for (;i < limit; i++, count++) { // i starts with the minimum index, count starts at zero.
                    var sample = stats.get(i);         // Get each statistic sample from the circular buffer.
                    var value = sample[property];      // Get the sample value.
                    var timestamp = sample[TIMESTAMP]; // Get the sample timestamp.

                    if (isRate) {
                        var sample1 = stats.get(i + 1);     // Get the next sample so we can calculate the rate.
                        var value1 = sample1[property];     // Get the next sample's value.
                        var timestamp1 = sample1[TIMESTAMP];// Get the next sample's timestamp.
                        value = ((value1 - value)*SECONDS_AS_NANOS)/(timestamp1 - timestamp); // Rate in items/s
                    }

                    var x = width + (timestamp - curtime)*(width/period);
                    graph[count] = {x: x, y: value};

                    if (value > maxValue) {
                        maxValue = value;
                    }
                }

                var heightNormaliser = (HEIGHT - (2*BORDER))/maxValue;

                _ctx.beginPath(); // Start drawing path for main graph.
                var sample = graph[0];
                _ctx.moveTo(sample.x, (maxValue - sample.y)*heightNormaliser + BORDER);

                for (i = 1; i < count; i++) {
                    sample = graph[i];
                    _ctx.lineTo(sample.x, (maxValue - sample.y)*heightNormaliser + BORDER);
                }
                _ctx.strokeStyle = "black";
                _ctx.stroke(); // Draw main graph.

                // Draw grid text
                _ctx.font = "15px Helvetica, Arial, 'Liberation Sans', FreeSans, sans-serif";
                _ctx.fillStyle = "red";
                _ctx.textBaseline = "middle";
                _ctx.textAlign = "right";
                _ctx.fillText(maxValue.toFixed(2), width - BORDER, BORDER);
                _ctx.fillText((maxValue/2).toFixed(2), width - BORDER, HEIGHT/2);
                _ctx.fillText("0.00", width - BORDER, HEIGHT - BORDER);
            }
        }
    };

    this.initialise = function() {
        $(document).bind("orientationchange", qmfui.Graphs.update);
        $(window).resize(qmfui.Graphs.update);
        $("#graphs").unbind("show").bind("show", qmfui.Graphs.update);
        $("#graphs-time-selector input").change(qmfui.Graphs.update);

        var canvas = $("#graphs-canvas")[0];
        if (canvas != null) {
            // The following "shouldn't" be necessary as the graphs-canvas is part of the static markup and it
            // works in the author's IE8 XP and Win7 test VMs without it, however getContext("2d") doesn't seem
            // to be working on some IE8 deployments so it's added experimentally to see if it fixes the problem.
            if (IS_IE && IE_VERSION == 8 && typeof(G_vmlCanvasManager) != "undefined") {
                G_vmlCanvasManager.initElement(canvas);
            }

            canvas.height = HEIGHT;
            if (canvas.getContext) { // Is canvas supported?
                _ctx = canvas.getContext("2d");
            }
        }
    };
}; // End of qmfui.Graphs definition


//-------------------------------------------------------------------------------------------------------------------


/**
 * Create a Singleton instance of the Links class managing the id="links" page.
 * TODO Add link/bridge features.
 */
qmfui.Links = new function() {

    this.update = function() {

    };

    this.initialise = function() {
    };
}; // End of qmfui.Links definition


//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the RouteTopology class managing the id="route-topology" page.
 * TODO Add link/bridge features. The idea of the route topology page is to "discover" federated brokers linked
 * to a "seed" broker. Not sure if this is actually possible, but it'd be pretty cool.
 */
qmfui.RouteTopology = new function() {

    this.initialise = function() {
    };
}; // End of qmfui.RouteTopology definition


//-------------------------------------------------------------------------------------------------------------------

/**
 * Create a Singleton instance of the Events class managing the id="events" page.
 */
qmfui.Events = new function() {
    var _events = new util.RingBuffer(20); // Store the Event history in a circular buffer.

    this.getEvent = function(index) {
        return _events.get(index);
    };

    this.update = function(workItem) {
        if (workItem._type == "EVENT_RECEIVED") {
            var params = workItem._params;
            var agent = params.agent;

            if (agent._product == "qpidd") { // Only log events from the broker ManagementAgent
                _events.put(params.event);
                iTablet.renderList($("#events-list"), function(i) {
                    var event = _events.get(i);
                    var name = event._schema_id._class_name;
                    var timestamp = new Date(event._timestamp/1000000).toLocaleString();
                    var text = name;

                    return "<li class='multiline arrow'><a href='#selected-event?index=" + i + "'>" + i + " " + text + 
                            "<p class='sub'>" + timestamp + "</p></a></li>";
                }, _events.size());
            }
        }
    };
}; // End of qmfui.Events definition

/**
 * Create a Singleton instance of the SelectedEvent class managing the id="selected-event" page.
 */
qmfui.SelectedEvent = new function() {
    var _severities = ["emerg", "alert", "crit", "err", "warning", "notice", "info", "debug"];

    this.update = function() {
        var location = iTablet.location;
        var data = location.data;
        if (data == null || location.hash != "#selected-event") {
            return;
        }

        // Get the latest update of the selected event object.
        var index = parseInt(data.index); // The parseInt is important! Without it the index lookup gives odd results..
        var event = qmfui.Events.getEvent(index);

        if (event == null) {
            $("#resource-deleted").show();
        } else {
            $("#resource-deleted").hide();

            // Populate the page header with the event name.
            var name = event._schema_id._package_name + ":" + event._schema_id._class_name;
            $("#selected-event .header h1").text(name);

            var general = [];
            var timestamp = new Date(event._timestamp/1000000).toLocaleString();
            general.push("<li><a href='#'>timestamp<p>" + timestamp + "</p></a></li>");
            general.push("<li><a href='#'>severity<p>" + _severities[event._severity] + "</p></a></li>");
            qmfui.renderArray($("#selected-event-list"), general);

            var values = [];
            for (var i in event._values) { // Populate with event _values properties.
                var value = event._values[i];
                if (i == "args" || i == "properties") { // If there are any args try and display them.
                    value = util.stringify(value);
                }
                values.push("<li><a href='#'>" + i + "<p>" + value + "</p></a></li>");
            }
            qmfui.renderArray($("#selected-event-values"), values);
        }
    };

    this.initialise = function() {
        $("#selected-event").unbind("show").bind("show", qmfui.SelectedEvent.update);
    };
}; // End of qmfui.SelectedEvent definition

