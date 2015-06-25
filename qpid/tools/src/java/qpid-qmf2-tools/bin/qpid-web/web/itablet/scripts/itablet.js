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
 * This library implements a general user interface look and feel similar to a "well know tablet PC" :-)
 * It provides animated page transition eye-candy but is, in essence, really just a fancy tabbed window.
 *
 * It has dependencies on the following:
 * itablet.css
 * iscroll.js
 * jquery.js (jquery-1.7.1.min.js)
 *
 * author Fraser Adams
 */

/**
 * Create a Singleton instance of the iTablet user interface generic look and feel.
 */
var iTablet = new function() {
    if (!String.prototype.trim) { // Add a String trim method if one doesn't exist (modern browsers should have it.)
        String.prototype.trim = function() {
            return this.replace(/^\s\s*/, '').replace(/\s\s*$/, '');
        };
    }

    /**
     * The Location inner class allows iTablet.location to be used similarly to window.location.
     */
    var Location = function(href) {
        this.href = href;
        var split = href.split("?"); // Split string by query part.
        this.hash = split[0];
        this.search = split.length == 2 ? "?" + split[1] : "";
        this.data = null;

        // Populate the data property with key/value pairs extracted from search part of URL.
        if (split.length == 2) {
            this.data = {};
            var kv = split[1].split("&"); // Split string into key/value pairs using the & is the separator.
            var length = kv.length;
            for (var i = 0; i < length; i++) {
                var s = kv[i].split("=");
                if (s.length == 2) {
                    var key = s[0].trim();
                    var value = s[1].trim();
                    this.data[key] = value;
                }
            }
        }

        Location.prototype.toString = function() {
            return this.href;
        };

        // Location.back() is mapped to the iTablet.goBack() method.
        // This allows clients to explicitly return to a previous page, useful for coding submit handlers etc.
        Location.prototype.back = goBack;
    };

    /**
     * The TextChange inner class adds support for a textchange Event on text input and textarea tags.
     * Initialise with: $.event.special.textchange = new TextChange();
     * Uses info derived from from http://benalman.com/news/2010/03/jquery-special-events/
     */
    var TextChange = function() {
        /**
         * Directly call the triggerIfChanged method on keyup.
         */
        var handler = function() {
			triggerIfChanged(this);
        };

        /**
         * For cut, paste and input handlers we need to call triggerIfChanged from a timeout.
         */
        var handlerWithDelay = function() {
			var element = this;
			setTimeout(function() {
				triggerIfChanged(element);
			}, 25);
        };

        /**
         * Trigger textchange Event handler bound to target element if the text has changed.
         */
        var triggerIfChanged = function(domElement) {
			var element = $(domElement);
            var current = domElement.contentEditable === "true" ? element.html() : element.val();
			if (current !== element.data("lastValue")) {
				element.trigger("textchange",  [element.data("lastValue")]);
				element.data("lastValue", current);
			}
        };

        /**
         * Called by jQuery when the first event handler is bound to a particular element.
         */
        this.setup = function(data) {
            var jthis = $(this);
		    jthis.data("lastValue", this.contentEditable === 'true' ? jthis.html() : jthis.val());
            // Bind keyup, cut, paste and input handlers in the .textchange Event namespace to allow easy unbinding.
			jthis.bind("keyup.textchange", handler);
			jthis.bind("cut.textchange paste.textchange input.textchange", handlerWithDelay);
        };

        /**
         * Called by jQuery when the last event handler is unbound from a particular element.
         */
        this.teardown = function (data) {
			$(this).unbind('.textchange'); // Unbind the Events linked to the .textchange Event namespace.
        }
    };

//-------------------------------------------------------------------------------------------------------------------

    var TOUCH_ENABLED = 'ontouchstart' in window && !((/hp-tablet/gi).test(navigator.appVersion));

    // Select start, move and end events based on whether or not the user agent is a touch device.
    var START_EV = (TOUCH_ENABLED) ? "touchstart" : "mousedown";
    var MOVE_EV  = (TOUCH_ENABLED) ? "touchmove"  : "mousemove";
    var END_EV   = (TOUCH_ENABLED) ? "touchend"   : "mouseup";

    // Populated in initialiseCSSAnimations()
    var ANIMATION_END_EV = "";// = "animationend webkitAnimationEnd MSAnimationEnd oAnimationEnd";
    var TRANSITION_END_EV = "";//"transitionend webkitTransitionEnd MSTransitionEnd oTransitionEnd";

    /**
     * The general perceived wisdom is to use feature detection rather than browser sniffing, which seems a good
     * aim, but as it happens *most* of the browser abstraction is happening in jQuery and CSS but there remain
     * a few quirks, mostly for IE < 9. IE < 9 and Opera < 12 both appear not to trigger a change when radio or
     * checkbox state changes and it's not clear how to "feature detect" this, which is the main reason for
     * including the Opera version sniffing.
     */
    var IS_IE = (navigator.appName == "Microsoft Internet Explorer");
	var IE_VERSION = IS_IE ? /MSIE (\d+)/.exec(navigator.userAgent)[1] : 1000000;
    var IS_OPERA = (navigator.appName == "Opera") && (window.opera.version != null);
    var OPERA_VERSION = IS_OPERA ? opera.version() : 1000000;

    var BODY; // jQuery object for body is used in several places so let's cache it (gets set in jQuery.ready()).
    var IS_MOBILE = false; // Gets set if we detect a small display (e.g. mobile) device.

    var _mainLeft = 0;     // N.B. We need to get the actual value after the DOM has loaded.
    var _history = [];     // URL history to enable back button behaviour.
    var _scrollers = {};
    var _transitions = {}; // Map of transition names to transition functions (populate later after functions defined).
    this.location = null;

    /**
     * This public helper method renders an HTML list with up to <maxlength> list items using the <contents> function.
     * If the length of the list is less than the number added by the contents function additional list items are 
     * appended, conversely if the length of the list is greater than the number added by the contents function then
     * the list is truncated.
     *
     * The contents of the list are populated via the supplied <contents> function, which should take an index as a 
     * parameter and return an <li> with contents or false. If false is returned that item is skipped.
     * If an HTML list is supplied without a maxlength and contents function this method will simply carry out
     * reskinning of the input widgets for the supplied list (and adding radiused borders for older versions of IE).
     *
     * For some reason this method seems to run very sluggishly on IE6, is's especially noticable when doing a
     * resize. It runs fine on every other browser. I've not noticed any obvious inefficiencies but IE6 is weird..
     *
     * @param list jQuery object representing the html list (ul) we wish to populate.
     * @param contents a function to populate the list contents, this function should take an index as a parameter.
     *        and return an <li> with the required contents or false to skip (useful if filtering is needed).
     * @param maxlength the maximum number of items that we wish to populate (default is 1).
     */
    this.renderList = function(list, contents, maxlength) {
        // For IE 6 get the width. Have to use .main or .popup-container because list.innerWidth() may not be set yet.
        var listWidth = 0;

        if (IE_VERSION < 7) { // This seems to be very slow on IE6, no idea why???
            var main = list.closest(".main");
            if (main.length == 0) { // It's a list in a popup
                listWidth = Math.round(parseInt($(".popup-container").css('width'), 10) * 0.9);
            } else {
                listWidth = Math.round($(".main").width() * 0.9);
            }
        }

        var lengthChanged = false;
        var items = list.children("li");

        if (contents == null) {
            maxlength = items.length;
        } else if (maxlength == null) {
            maxlength = 1; // If no maxlength is supplied default to calling the contents function once.
        }

        var actualLength = 0; // Actual number of <li> supplied, this caters for the contents function returning false.
        for (var i = 0; i < maxlength; i++) {
            var li = false;
            if (actualLength < items.length) {
                if (contents) { // Modify list item with the new contents at index i.
                    var newItem = contents(i);
                    if (newItem) { // If the contents function didn't return false add contents to current li.
                        li = $(items[actualLength]);
                        actualLength++;
                        var active = li.hasClass("active") ? "active" : "";
                        var newItem = $(newItem).addClass(active);
                        li.removeClass().addClass(newItem.attr("class")); // Remove existing classes and add new ones.
                        li.html(newItem.html());
                    }
                } else { // If contents function not present we simply reskin the current list item.
                    li = $(items[actualLength]);
                    actualLength++;

                    if (IE_VERSION < 9) {
                        // Remove any markup used for faking :first-child, :last-child, :before, :after
                        li.removeClass("first-child last-child");
                        li.children("div.before, div.after, div.fbefore, div.fafter").remove();
                    }
                }
            } else { // If there are fewer items in the list than there are contents then append new list items.
                var newItem = contents(i);
                if (newItem) { // If the contents function didn't return false append contents as new li.
                    li = $(newItem);
                    actualLength++;
                    list.append(li);
                    lengthChanged = true;
                }
            }

            if (li) {
                // Reskin input widgets.
                Radio.reskin(li.children("input:radio"));
                Checkbox.reskin(li.children("input:checkbox"));

                // Fix several quirks in early versions of IE.
                if (IE_VERSION < 8) {
                    var anchor = li.children("a");
                    anchor.attr("hideFocus", "hidefocus"); // Fix lack of outline: none; in IE < 8

                    if (IE_VERSION < 7) { // IE6 percentage widths are messed up so need to set absolute width.
                        // 41 comes from padding: 0 30px 0 11px; 22 comes from padding: 0 11px 0 11px;
                        // 34 comes from margin-left: 5px; text-indent: 40px; minus 11.
                        var anchorWidth = listWidth - ((li.hasClass("arrow") ? 41 : 22) + 
                                                       (anchor.hasClass("icon") ? 34 : 11));
                        anchor.css({"width": anchorWidth + "px"});

                        // IE6 can't cope with multiple CSS classes so we need to merge these into a single class.
                        if (li.is(".arrow.radio")) {
                            li.addClass("ie6-radio-arrow");
                            if (li.hasClass("checked")) {
                                li.addClass("ie6-checked-arrow");
                            }
                        }
                    }
                }
            }
        }

        // If list is longer than the contents being added then trim the list so it's the same size.
        if (actualLength < items.length) {
            items.slice(actualLength).remove();
            lengthChanged = true;
        }

        // Add radiused borders for IE8 and below.
        // We have to do this after completely populating the list so first() and last() are correct.
        if (IE_VERSION < 9) {
            items = list.children("li");
            var last = items.last();
            last.addClass("last-child").prepend("<div class='before'></div>").append("<div class='after'></div>");

            if (IE_VERSION < 8)   {
                var first = items.first();
                if (IE_VERSION == 7) { // For IE7 fake :first-child:before and :first-child:after
                    first.prepend("<div class='fbefore'></div>").append("<div class='fafter'></div>");
                } else if (IE_VERSION < 7) { // For IE6 fake :first-child
                    // We're not adding radiused borders to IE6, this class provides the proper top border colour.
                    first.addClass("first-child");
                }
            }
        }

        // If the length has changed trigger an iScroll refresh on the top level page that contains the list.
        if (lengthChanged) {
            list.closest(".main").trigger("refresh"); // refresh is a synthetic event handled by parents of scroll-area
        }
    };

//-------------------------------------------------------------------------------------------------------------------
//                                                 UI Widgets                                             
//-------------------------------------------------------------------------------------------------------------------

    /**
     * Create a Singleton instance of the Radio class used to reskin and manage HTML input type="radio" widgets.
     */
    var Radio = new function() {
        /**
         * "Reskin" HTML input type="radio" items into a tablet "tick" style radio item. Note that the radio item
         * needs to have a label sibling and be wrapped in a parent <li> for this to work correctly.
         * e.g. <ul class="list"><li><label>test</label><input type="radio" name="test-radio" checked /></li></ul>
         */
        this.reskin = function(radios) {
            if (radios == null) { // if no input:radio is specified attempt to reskin every one in the document.
                radios = $("input:radio");
            }

            // Add classes to container <li> based on "template" radio buttons. This "re-skins" the radio buttons.
            radios.each(function() { // Iterate through each radio button.
                var jthis = $(this).hide();
                if (!jthis.hasClass("reskinned")) { // If checkbox has already been reskinned move on.
                    var parent = jthis.parent();
                    parent.addClass("radio");
                    if (this.checked) {
                        parent.addClass("checked");
                    }
                    jthis.addClass("reskinned"); // Mark as reskinned to avoid trying to reskin it again.
                }
            });
        };

        /**
         * Handle radio button state changes. This method is delegated to by the main handlePointer end() method.
         * Note that we are actually passing in the $("ul li.radio") jQuery object that the Event was bound to
         * rather than the actual Event object as this has already been extracted by handlePointer and is more
         * useful for the implementation of this method.
         */
        this.handleClick = function(jthis, type) {
            var checked = jthis.parent().children(".checked");
            var radio = jthis.children("input:radio"); // Select the template radio button.

            fade(jthis); // Fade out the highlighting of the selected <li>

            if (type != "click") { // If this handler wasn't triggered by a radio button click we synthesise one.
                BODY.unbind("click", handlePointer); // Prevent the synthetic click from triggering handlePointer.
                radio.click(); // Trigger radio button's click (on modern browsers this triggers change too if changed).
                BODY.click(handlePointer);
            }

            // We explicitly manipulate input:radio checked attr in the following block in case a name attr wasn't
            // specified in the HTML. With reskinned radio buttons the parent <ul> is the real container.
            if (!jthis.is(checked)) { // If the clicked item is not the previously checked item.
                // Clear any check mark on the previously selected <li> and the same on the template radio button.
                checked.removeClass("checked");
                checked.children("input:radio").attr("checked", false);

                // Mark the current <li> as checked and do the same on the template radio button.
                jthis.addClass("checked").change();
                jthis.children("input:radio").attr("checked", true);

                // For older IE/Opera triggering the click as done earlier won't trigger the change event so do it now.
                if (IE_VERSION < 9 || OPERA_VERSION < 12) {
                    radio.change();
                }
            }
        };
    };

    /**
     * Create a Singleton instance of the Checkbox class used to reskin and manage HTML input type="checkbox" widgets.
     */
    var Checkbox = new function() {
        /**
          * Return the input element associated with the specified label. This is used because the input element
          * may be associated to the label in a number of different was (via the "for" attribute, by containment etc.)
          */
        var findInputElement = function(label) {
            var input = $("#" + label.attr("for")); // First check if label for points to the input.
            if (input.length == 0) { // If not check if the label contains the input.
                input = label.children("input");
            }
            if (input.length == 0) { // Finally check if the label's next sibling is an input.
                input = label.next("input");
            }

            return input;
        };

        /**
         * "Reskin" HTML input type="checkbox" items into a tablet "switch" style checkbox. Note that the checkbox
         * needs to have a label sibling and be wrapped in a parent <li> for this to work correctly.
         * e.g. <ul class="list"><li><label>test</label><input type="checkbox" name="test-checkbox" checked /></li></ul>
         * If multiple <input type="checkbox"> elements are placed in an <li> then they will automatically be
         * reskinned as a "horiz-checkbox", this can also be done explicitly by doing <li class="horiz-checkbox">
         */
        this.reskin = function(checkboxes) {
            if (checkboxes == null) { // if no input:checkbox is specified attempt to reskin every one in the document.
                checkboxes = $("input:checkbox");
            }

            checkboxes.each(function() { // Iterate through each checkbox.
                var jthis = $(this).hide();
                if (!jthis.hasClass("reskinned")) { // If checkbox has already been reskinned move on.
                    // If there are multiple checkboxes in a container create a horizontal checkbox by adding class
                    // to parent. Note that <li class="horiz-checkbox"> may also be explicitly set in the HTML.
                    jthis.siblings("input:checkbox").parent().addClass("horiz-checkbox");

                    // Mop up where checkbox is contained by label e.g. <label>test<input type="checkbox"/></label>
                    jthis.parent().siblings("label").parent().addClass("horiz-checkbox");

                    var li = jthis.closest("li"); // Find containing li.

                    if (li.hasClass("horiz-checkbox")) { // Reskin horizontal checkbox.
                        // IE8 doesn't seem to distinguish between the selectors ul.list li:first-child:before and 
                        // ul.list li.horiz-checkbox:first-child:before when horiz-checkbox is dynamically added
                        // this stops the <li> fake radiused border being correctly positioned for horiz-checkboxes.
                        // By adding horiz-checkbox class to the <ul> too we can use a more explicit rule in the CSS.
                        li.parent().addClass("horiz-checkbox");

                        // As we use inline-block for horiz-checkbox we need to remove any whitespace text nodes.
                        li.contents().filter(function() {
                            return (this.nodeType == 3 && $.trim($(this).text()).length == 0);
                        }).remove();

                        // For horiz-checkbox we set the width of each item and add a span containing the right border.
                        // The inner span is needed so the border doesn't impact the element width calculation.
                        li.each(function() {
                            var buttons = $(this).children("label");
                            var width = 100/buttons.length;

                            buttons.css("width", width + "%"); // Using the percentage works for browsers > IE7.
                            // The child span helps position the border - see css (ul.list li.horiz-checkbox label span)
                            buttons.filter(":not(:last)").append("<span></span>");
                            buttons.first().addClass("first-child");
                            buttons.last().addClass("last-child");

                            // Early IE doesn't respect the width so reduce the width of the last item a little.
                            if (width < 100 && (IE_VERSION < 8 )) {
                                buttons.last().css("width", width - 1 + "%")
                            }

                            // Find input associated with each label and if it's checked add "checked" class to label.
                            buttons.each(function() {
                                var button = $(this);
                                var input = findInputElement(button);
                                input.addClass("reskinned"); // Mark as reskinned to avoid trying to reskin it again.
                                if (input.attr("checked")) {
                                    button.addClass("checked");
                                    if (button.is(buttons.last())) {
                                        // Use toggle-on not checked to avoid confusing IE6.
                                        button.parent().addClass("toggle-on"); 
                                    }
                                }
                            });
                        });
                    } else { // Reskin normal checkbox.
                        // Add markup to container <li> based on "template" checkboxes. This "re-skins" the checkboxes.
                        jthis.parent().
                            append("<div class='checkbox'><div class='mask'></div><div class='onoff'></div></div>");

                        // If the checkbox is checked find onoff switch and change its initial position to "on".
                        jthis.filter(":checked").siblings(".checkbox").children(".onoff").css("left", "0px");
                        jthis.addClass("reskinned"); // Mark as reskinned to avoid trying to reskin it again.
                    }
                }
            });
        };

        /**
         * Handle checkbox state changes. This method is delegated to by the main handlePointer method.
         */
        this.handlePointer = function(e, type) {
            // If triggered by a synthetic click the target is an input:checkbox otherwise it's a div.mask
            var jthis = (type == "click") ? $(e.target).siblings("div.checkbox") : $(e.target).parent();
            var onoff = jthis.children(".onoff");
            var checkbox = jthis.parent().children("input:checkbox"); // Select the underlying <input type="checkbox">
            var offsetX = onoff.offset().left - jthis.offset().left;
            var offset = -parseInt(onoff.css("left"), 10); // If we use translate we adjust by the left CSS position.
            var startX = (e.pageX != null) ? e.pageX : e.originalEvent.targetTouches[0].pageX;
            var clicked = true; // Set to false if move handler is called;

            var move = function(e) {
                var newX = (e.pageX != null) ? e.pageX : e.originalEvent.changedTouches[0].pageX;
                var diffX = offsetX + newX - startX;
                clicked = false;

                if (diffX >= -50 && diffX <= 0) {
                    setPosition(onoff, offset, diffX);
                }
                e.stopPropagation(); // Prevent iScroll from trying to scroll when we drag the switch.
            };

            /**
             * The pointer up handler is only bound when the pointer down has been triggered and so behaves like a click.
             */
            var end = function(e) {
                var pos = onoff.offset().left - jthis.offset().left;
                var duration = 300;

                if (clicked) {
                    pos = (pos == 0) ? -50 : 0;
                } else {
                    // Animation duration is between 150ms and 0ms based on position.
                    duration = (25 - Math.abs(pos + 25)) * 6;
                    pos = (pos < -25) ? -50 : 0;

                }

                setPosition(onoff, offset, pos, duration);

                BODY.unbind("click", handlePointer); // Prevent the synthetic click from triggering handlePointer.
                var currentlyChecked = checkbox[0].checked;
                if ((currentlyChecked && pos == -50) || (!currentlyChecked && pos == 0)) {
                    if (type == "click") {
                        checkbox.attr("checked", !currentlyChecked);
                    } else {
                        checkbox.click();
                        // For older IE/Opera triggering the click won't trigger the change event so do it now.
                        if (IE_VERSION < 9 || OPERA_VERSION < 12) {
                            checkbox.change();
                        }
                    }
                }
                BODY.click(handlePointer);

                if (TOUCH_ENABLED) {
                    jthis.unbind(MOVE_EV + " " + END_EV);
                } else {
                    BODY.unbind(MOVE_EV + " " + END_EV + " mouseleave");
                }
            };

            if (type == "click") {
                end(e);
            } else {
                // Bind move, end and mouseleave events to our internal handlers.
                if (TOUCH_ENABLED) { // Touch events track over the whole page by default.
                    jthis.bind(MOVE_EV, move).bind(END_EV, end);
                } else { // Bind mouse events to body so we can track them over the whole page.
                    BODY.bind(MOVE_EV, move).bind(END_EV + " mouseleave", end);
                }
            }
        };

        /**
         * Handle horiz-checkbox state changes. This method is delegated to by the main handlePointer end() method.
         * Note that we are actually passing in the $("ul li.horiz-checkbox label") jQuery object that the Event was 
         * bound to rather than the actual Event object as this has already been extracted by handlePointer and is
         * more useful for the implementation of this method.
         */
        this.handleClick = function(jthis, type) { 
            var parent = jthis.parent();
            var lastChild = parent.children("label").last();

            if (jthis.hasClass("checked")) {
                jthis.removeClass("checked");
                if (jthis.is(lastChild)) {
                    parent.removeClass("toggle-on"); // Use toggle-on rather than checked to avoid confusing IE6.
                }
            } else {
                jthis.addClass("checked");
                if (jthis.is(lastChild)) {
                    parent.addClass("toggle-on"); // Use toggle-on rather than checked to avoid confusing IE6.
                }
            }

            if (type == "click") {
                var input = findInputElement(jthis);
                input.attr("checked", !input.attr("checked"));
            } else { // If this handler wasn't triggered by a checkbox click we synthesise one.
                BODY.unbind("click", handlePointer); // Prevent the synthetic click from triggering handlePointer.
                var checkbox = findInputElement(jthis);
                checkbox.click();
                // For older IE/Opera triggering the click won't trigger the change event so do it now.
                if (IE_VERSION < 9 || OPERA_VERSION < 9) {
                    checkbox.change();
                }
                BODY.click(handlePointer);
            }
        }
    };

//-------------------------------------------------------------------------------------------------------------------
//                                                Main Event Handler                                            
//-------------------------------------------------------------------------------------------------------------------

    /**
     * This method handles the pointer down, move and up events.
     * We handle the discrete events because mobile Safari adds a 300ms delay to the click handler, in addition
     * we want to be able to un-highlight rows if we move the mouse/finger. Note that this handler is in the form
     * of a delegating event handler - we actually bind the events to the html body, this is so that if we modify
     * the DOM externally, e.g. via an AJAX update we will trigger from newly created elements too.
     * Note that the start(), removeHighlight(), resetHighlight(), touchMove() and end() methods are private.
     * This method does actually handle click events but its main job is to prevent the default navigation action
     * on href, however if the click event is a synthetic click caused by a jQuery trigger the method behaves like
     * a proper click handler.
     */
    var handlePointer = function(e) {
        // These are the *actual* selectors that we are interested in handling events for.
        var selectors = "ul.contents li, ul.mail li, ul li.radio, ul li div.checkbox, ul li.horiz-checkbox label, " + 
                        "ul li.arrow, ul li.pop, div.header a.back, div.header a.done, div.header a.cancel, " + 
                        "div.header a.menu";

        var target = e.target;
        var jthis = $(target);
        var parent;
        var prev;
        var href = jthis.attr("href");
        var chevronClicked = false; // Set true if we've clicked on a chevron (used for navigable radio buttons).
        var scrolled;    // Gets set if a page has been (touch) scrolled.
        var highlighted; // Gets set if a sidebar or mail item has been selected/highlighted.
        var startY;      // The vertical position when a touchstart gets triggered.

        var start = function(e) {
            // This block checks if the event target is one of the selectors, if not it uses jQuery closest to get the
            // first element that matches the selector, start at the current element and progress up through the DOM.
            if (!jthis.is(selectors)) {
                jthis = jthis.closest(selectors);
            }
        
            // If closest object doesn't match any of the selectors return true if it has an href else return false.
            if (jthis.length == 0) {
                return (!!href); // The href test allows the browser to add the active pseudoclass.
            }

            if (jthis.hasClass("checkbox")) { // If a checkbox then delegate to the Checkbox handlePointer() handler.
                Checkbox.handlePointer(e);
                return false;
            }

            parent = jthis.parent();

            // prev is the previously highlighted item. We search from parent's parent as we may have multiple lists.
            prev = parent.parent().find(".active");

            href = jthis.attr("href"); // Get the href of the element matching the selector.

            var TOUCHED_SIDEBAR = TOUCH_ENABLED && parent.is(".contents, .mail"); // Is this a touch on a sidebar list?

            // If the href isn't directly present then it's an <li> that contains the anchor, so we have to look further.
            if (!href) {
                var ICON_WIDTH = 45; // The width of the icon image plus some padding.
                var offset = Math.ceil(jthis.offset().left);

                // Get the pointer x value relative to the <li>
                var x = (e.pageX != null) ? e.pageX - offset : // Mouse position.
                        (e.originalEvent != null) ? e.originalEvent.targetTouches[0].pageX - offset : 0; // Touch pos.

                // If target is a clickable-icon we return immediately thus preventing any highlighting or navigation.
                if (jthis.hasClass("clickable-icon") && (x < ICON_WIDTH)) {
                    return false;
                }

                // If target is a navigable radio button then check if chevron was clicked/tapped.
                if (jthis.hasClass("radio") && jthis.hasClass("arrow") && ((jthis.outerWidth() - x) < ICON_WIDTH)) {
                    chevronClicked = true;
                }

                e.preventDefault(); // Stop the anchor default highlighting, we'll add our own prettier highlight.

                // This block highlights the selected <li> on mousedown or touchstart. For touch enabled devices we wait
                // for a short time before highlighting in case the touchstart was the start of a scroll rather than a
                // selection. If it was a scroll then the scrolled flag will get set by the touchMove handler and the
                // highlight is aborted when the timeout gets triggered.
                if (!prev[0] || prev[0] != jthis[0]) {
                    if (TOUCHED_SIDEBAR && !IS_MOBILE) {
                        // If TOUCH_ENABLED and a sidebar or mail list wait 50ms before highlighting in case the
                        // touch start is really the start of a touch scroll event.
                        scrolled = false;
                        highlighted = false;

                        setTimeout(function() {
                            if (!scrolled) { // scrolled may be set by touchMove if the list has been scrolled.
                                prev.removeClass("active");
                                jthis.addClass("active"); // Highlight the current <li>
                                highlighted = true;
                            }
                        }, 50);
                    } else { // If not TOUCH_ENABLED or a sidebar or mail list highlight immediately.
                        prev.removeClass("active");
                        // Navigable radio buttons don't highlight when the chevron is clicked.
                        if (!chevronClicked) {
                            jthis.addClass("active"); // Highlight the current <li>
                        }
                    }
                }

                href = jthis.children("a:first").attr("href"); // Get the href from the first anchor enclosed by the <li>
                href = (href == null) ? "#" : href;            // Create default href of "#" if none has been specified.
                href = href.replace(window.location, "");      // Fix "absolute href" bug found in IE7 and below.
            }

            BODY.bind(END_EV, end).unbind(START_EV, handlePointer);

            if (parent.hasClass("list") || (TOUCHED_SIDEBAR && IS_MOBILE)) {
                // For a small (e.g. mobile) displays the sidebar becomes the main menu page and touches behave
                // like normal list touches and become inactive on any touch move.
                BODY.bind(MOVE_EV, removeHighlight);
            } else if (TOUCHED_SIDEBAR && !IS_MOBILE) {
                // For a real sidebar detect touch scrolling on these items.
                startY = (e.originalEvent) ? e.originalEvent.targetTouches[0].pageY : 0;
                BODY.bind(MOVE_EV, touchMove);
            }
        };

        /**
         * If we move the mouse or finger in a list item we un-highlight and deactivate.
         */
        var removeHighlight = function(e) {
            BODY.unbind(MOVE_EV + " " + END_EV).bind(START_EV, handlePointer);
            jthis.removeClass("active");
        };

        /**
         * If we move a finger up or down in a sidebar item we reinstate the previous highlight ontouchend.
         */
        var resetHighlight = function(e) {
            BODY.unbind(END_EV).bind(START_EV, handlePointer);
            prev.addClass("active");
        };

        /**
         * This touch move handler is only bound if the initial event is a touch start event bound to a <li>
         * with a <ul> parent that has a .contents or .mail class. These lists need to be touch scrollable, but
         * they also need to have a persistent highlight on selected items. This method checks how many vertical
         * pixels have been scrolled and if it exceeds a threshold it triggers a scrolled state. Once scrolled it removes
         * any new highlighting and binds resetHighlight to touchend, which reinstates the previous highlight.
         */
        var touchMove = function(e) {
            var newY = e.originalEvent.changedTouches[0].pageY;
            if (Math.abs(newY - startY) > 7) { // Only trigger on an up/down finger movement.
                if (highlighted) { // If a new item was highlighted set the highlighting back to the previous item.
                    BODY.unbind(MOVE_EV + " " + END_EV).bind(END_EV, resetHighlight);
                } else {
                    BODY.unbind(MOVE_EV + " " + END_EV).bind(START_EV, handlePointer);
                }

                if (prev[0] != jthis[0]) {
                    jthis.removeClass("active");
                }
                scrolled = true;
            }
        };

        /**
         * The pointer up handler is only bound when the pointer down has been triggered and so behaves like a click
         * handler. If we have been triggered by a back selector or if we re-click an already selected sidebar entry
         * that has previously transitioned the we call goBack() to transition backwards otherwise we goTo(href).
         */
        var end = function(e) {
            BODY.unbind(MOVE_EV + " " + END_EV).bind(START_EV, handlePointer);

            if (!IS_MOBILE && (parent.hasClass("contents") || parent.hasClass("mail"))) {
                // Handle sidebar transitions.
                if (_history.length == 2 && href == _history[1].href) {
                    goBack();
                } else {
                    _history = [];
                    goTo(href);
                }
            } else if (parent.is("ul")) {
                if (jthis.hasClass("radio") && !chevronClicked) { // Delegate radio button state changes.
                    Radio.handleClick(jthis, e.type);
                } else { // Handle goTo page transition.
                    var classes = jthis.attr("class").split(" ");
                    var transition = slide; // Default animation.
                    // Look up the transition animation based upon the item's class
                    for (var i in classes) {
                        var current = classes[i];
                        var newTransition = _transitions[current];
                        if (newTransition != null) {
                            transition = newTransition;
                            break;
                        }
                    }
                    goTo(href, transition);
                }
            } else if (parent.hasClass("horiz-checkbox")) {
                Checkbox.handleClick(jthis);
            } else if (jthis.is(".back, .done, .cancel")) {
                goBack();
            } else if (jthis.hasClass("menu")) {
                // For mobile devices the home button allows immediate navigation back to the main menu, which may
                // be useful if several pages have been navigated through.
                _history = [_history[0], {href:"#menu?", transition:null}];
                goBack();
            }
        };

        if (e.type == "click") {
            e.preventDefault(); // Prevent the browser trying to navigate to the href itself onclick.
            if (!e.originalEvent) { // If event is triggered by calling the jQuery click() method.
                if (jthis.is("li, input:radio")) {
                    start(e);
                    end(e);
                } else if (jthis.is("input:checkbox")) {
                    var li = jthis.closest("li.horiz-checkbox");

                    if (li.length == 0) { // For a normal checkbox add pageX to the event and delegate handlePointer()
                        e.pageX = 0;
                        Checkbox.handlePointer(e, e.type);
                    } else {
                        // It's a horiz-checkbox. We need to find the label associated with the checkbox.
                        var label = jthis.parent("label"); // First check if the label contains the checkbox.
                        if (label.length == 0) { // If not look for a label containing a for matching the checkbox ID.
                            label = $("label[for='" + jthis.attr("id")+"']");
                        }
                        if (label.length == 0) { // If not assume the label is the preceding sibling (a bit fragile..).
                            var items = li.children();
                            label = $(items[items.index(jthis) - 1]);
                        }

                        Checkbox.handleClick(label, e.type);
                    }
                }
            }
        } else { // Event type is mousedown or touchstart so call main event start handler.
            start(e);
        }
    };

    /**
     * This method handles keyboard input. Its main purpose is to detect the return key being pressed and if it
     * has this method will attempt to trigger the handler bound to the right button, which should be a done/submit.
     */
    var handleKeyboard = function(e) {
        if (e.which == 13) { // Handle return key;
            // When the return key is pressed find any right button present in the header and trigger a click
            // on it, this should have the effect of triggering the done/submit handler for the form.
            var jthis = $(e.target);
            var done = jthis.closest(".main, .popup").find(".header a.right.button");
            done.trigger(START_EV).trigger(END_EV);
        }
    };

//-------------------------------------------------------------------------------------------------------------------
//                                              Page Navigation Methods                                          
//-------------------------------------------------------------------------------------------------------------------

    /**
     * This event handler handles the synthetic refresh event that may be triggered on a top level page containing
     * a scroll-area. The handler uses the id of the page to index the iScroll object then calls its refresh().
     */
    var handleRefresh = function(e) {
        var id = $(this).attr("id");
        if (_scrollers[id] != null) {
            _scrollers[id].refresh();
        }
    }

    /**
     * This method transitions to a selected destination. If the destination is "#" it simply returns, if there
     * is no history the destination page is shown otherwise we transition using an animation.
     * The ".split("?")[0]" blocks are there to cater for the case where the destination URL contains data to
     * be passed between page fragments, where the data is delimited by a "?" and separated by "&". 
     */
    var goTo = function(destination, transition) {
        iTablet.location = new Location(destination);
        var previous = (_history.length == 0) ? null : _history[0].href;

        if (destination == "#" || destination == previous) { // The second test guards against multiple clicks
            return;
        } else if (!IS_MOBILE && _history.length == 0) {
            var pages = $(".main");
            $(pages).each(function(index) {
                var jthis = $(this);
                var id = jthis.attr("id");
                if (("#" + id) == destination.split("?")[0]) {
                    jthis.show().trigger("show").find(".active").removeClass("active");
                    _scrollers[id].refresh(); // Refresh the touch scroller on the new page.
                    _history.unshift({href:destination, transition:null});
                } else {
                    jthis.hide().trigger("hide");
                }
            });
        } else {
            var currentPage = $(_history[0].href.split("?")[0]);
            var newPage = $(destination.split("?")[0]);
            transition(currentPage, newPage, false);
            _scrollers[newPage.attr("id")].refresh(); // Refresh the touch scroller on the new page. 
            _history.unshift({href:destination, transition:transition});
        }
    };

    /**
     * This method transitions back to the previous item in the history using an animation.
     * The ".split("?")[0]" blocks are there to cater for the case where the fragment URLs contain data to
     * be passed between page fragments, where the data is delimited by a "?" and separated by "&".
     */
    var goBack = function() {
        if (_history.length > 1) {
            iTablet.location = new Location(_history[1].href);
            var transition = _history[0].transition;
            var currentPage = $(_history[0].href.split("?")[0]);
            var newPage = $(_history[1].href.split("?")[0]);
            transition(currentPage, newPage, true);
            _scrollers[newPage.attr("id")].refresh(); // Refresh the touch scroller on the new page.
            _history.shift();

            // Hide virtual keyboard.
            document.activeElement.blur();
        }
    };

//-------------------------------------------------------------------------------------------------------------------
//                                                  Animations                                             
//-------------------------------------------------------------------------------------------------------------------

    /**
     * This method detects support for CSS3 animations and transitions and uses them if present. It will attempt
     * to use translate3d if present as this is most likely to be GPU accelerated and uses translate as fallback.
     * In an ideal world this would be set up in a stylesheet using media queries, but unfortunately using
     * prefixes for all of the keyframes is rather verbose and untidy and media query of translate3d only seems
     * to be supported in WebKit, so scripting is needed whatever. This method "injects" the relevant styles.
     */
    var initialiseCSSAnimations = function() {
        // TODO It'd be nicer to use feature detection but that can be hard to get right - how do we *reliably"
        // detect support for animationend and transitionend events, which are essential for these animations???
        if ((/android/gi).test(navigator.appVersion) || OPERA_VERSION <= 12.01) {
            // Android has poor CSS3 animation support so use jQuery.
            // Opera <= 12.01 doesn't have animationend which messes up state management, what's worse is that it
            // passes the animationSupported test, so just bomb out early Opera 12.01 at least fails that...
            return;
        }

        var domPrefixes = ["Webkit", "Moz", "O", "ms", "Khtml"];

        // For the following lookups ensure that prefix key is forced to lower case!!
        var transitionEndLookup = { // Lookup transitionend Event. Note prefixes are different to DOM prefixes 
			""			: "transitionend",
			"webkit"	: "webkitTransitionEnd",
			"moz"		: "transitionend",
			"o"			: "oTransitionEnd",
			"ms"		: "transitionend"
		};

        var animationEndLookup = { // Lookup transitionend Event. Note prefixes are different to DOM prefixes 
			""			: "animationend",
			"webkit"	: "webkitAnimationEnd",
			"moz"		: "animationend",
			"o"			: "oAnimationEnd",
			"ms"		: "transitionend"
		};

        var has3d = false;
        var domPrefix = "";
        var prefix = "";

        var style = $("<style/>");
        var styles = style[0].style;

        // We first check for animation-name and transform CSS support.
        var animationSupported = styles.animationName && styles.transform ? true : false;
        var animationend = "animationend";
        var transitionend = "transitionend";

        if (!animationSupported) { // If prefix free versions not present check for prefixed versions.
            var length = domPrefixes.length;
            for (var i = 0; i < length; i++) {
                if (styles[domPrefixes[i] + "AnimationName"] !== undefined) {
                    animationend = animationEndLookup[domPrefixes[i].toLowerCase()];
                    transitionend = transitionEndLookup[domPrefixes[i].toLowerCase()];

                    var domPrefix = domPrefixes[i];
                    prefix = "-" + domPrefix.toLowerCase() + "-";

                    if (styles[domPrefix + "Transform"] !== undefined) {
                        if (styles[domPrefix + "Perspective"] !== undefined) {
                            has3d = true;
                        }
                        animationSupported = true;
                        break;
                    } else {
                        return;
                    }
                }
            }
        }

        ANIMATION_END_EV = animationend;
        TRANSITION_END_EV = transitionend;

        if (animationSupported) { // Animating transforms is supported if this is true.
            // Webkit's 3D transforms are passed off to the browser's own graphics renderer so may give a
            // false positive, the test below should double check that 3d is indeed supported.
            if (has3d && prefix == "-webkit-") {
                has3d = 'WebKitCSSMatrix' in window && 'm11' in new WebKitCSSMatrix();
            }
            
            var s = ".sidebar, .main, .popup, .popup-window, .popup-container, ul li {" + 
                        prefix + "animation: 350ms ease-in-out;}";
            // Define the key animation styles. cssSlide simply adds these classes to trigger the animation.
            s += ".slideIn          {" + prefix + "animation-name: slideinfromright;}";
            s += ".slideOut         {" + prefix + "animation-name: slideouttoleft;}";
            s += ".slideIn.reverse  {" + prefix + "animation-name: slideinfromleft;}";
            s += ".slideOut.reverse {" + prefix + "animation-name: slideouttoright;}";
            s += ".fade             {" + prefix + "animation-name: fadehighlight;}";
            s += ".slideUp          {" + prefix + "animation-name: slideinfrombottom;}";
            s += ".slideDown        {" + prefix + "animation-name: slideouttobottom;}";
            s += ".dissolveIn50     {" + prefix + "animation-name: dissolvein50;}";
            s += ".dissolveOut50    {" + prefix + "animation-name: dissolveout50;}";

            // Helper method to render transform using translate3d if supported or translate if not.
            var renderTranslate = function(val) {
                return prefix + "transform: translate" + (has3d ? "3d(" : "(") + val + (has3d ? ", 0);" : ");");
            };

            // Implement setting the left css attribute of the supplied element using css translate.
            var cssSetPosition = function(element, offset, left, duration) {
                // Invoked when the animation completes, resets styles.
                var completeCallback = function() {
                    element.removeAttr("style").css("left", left + "px").unbind(TRANSITION_END_EV);
                };

                // If a duration is provided then use a transition to animate the transform.
                if (duration != null) {
                    element.css(prefix + "transition", prefix + "transform " + duration + "ms ease-out 0ms").
                            bind(TRANSITION_END_EV, completeCallback);
                }

                element.css(prefix + "transform",
                            "translate" + (has3d ? "3d(" : "(") + (left + offset) + "px, 0" + (has3d ? ", 0)" : ")"));
            };

            var keyframes = prefix + "keyframes ";

            // Now define the keframes for the animations.
            s += "@" + keyframes + "slideinfromright {";
            s += "from {" + renderTranslate("100%, 0") + "}";
            s +=   "to {" + renderTranslate("0, 0") + "}}";

            s += "@" + keyframes + "slideouttoleft {";
            s += "from {" + renderTranslate("0, 0") + "}";
            s +=   "to {" + renderTranslate("-100%, 0") + "}}";

            s += "@" + keyframes + "slideinfromleft {";
            s += "from {" + renderTranslate("-100%, 0") + "}";
            s +=   "to {" + renderTranslate("0, 0") + "}}";

            s += "@" + keyframes + "slideouttoright {";
            s += "from {" + renderTranslate("0, 0") + "}";
            s +=   "to {" + renderTranslate("100%, 0") + "}}";

            s += "@" + keyframes + "fadehighlight {";
            s += "from {background-color: #035de7; color: #ffffff;}";
            s +=   "to {background-color: #f7f7f7; color: #324F85;}}";

            s += "@" + keyframes + "slideinfrombottom {";
            s += "from {" + renderTranslate("0, 100%") + "}";
            s +=   "to {" + renderTranslate("0, 0") + "}}";

            s += "@" + keyframes + "slideouttobottom {";
            s += "from {" + renderTranslate("0, 0") + "}";
            s +=   "to {" + renderTranslate("0, 120%") + "}}";

            s += "@" + keyframes + "dissolvein50 {";
            s += "from {background-color: rgba(0, 0, 0, 0.0);}";
            s +=   "to {background-color: rgba(0, 0, 0, 0.49);}}"; // Setting to 0.49 not 0.5 prevents Firefox glitch.

            s += "@" + keyframes + "dissolveout50 {";
            s += "from {background-color: rgba(0, 0, 0, 0.5);}";
            s +=   "to {background-color: rgba(0, 0, 0, 0.0);}}";

            style.append(s);
            $("head").append(style); // "inject" animation styles into DOM.
            slide  = cssSlide; // Override slide method with the cssSlide version.
            fade   = cssFade;  // Override fade method with the cssFade version.
            popup  = cssPopup; // Override fade method with the cssFade version.
            setPosition = cssSetPosition;
        }
    };

    /**
     * Implement a simple colour fade animation using a look up table to fade active colour back to background.
     * This is the default fade implementation, which may be overridden if CSS3 animations are supported.
     */
    var fade = function(selected) {
        var table = [{"background-color": "#0360e8", "color": "#ffffff"},
                     {"background-color": "#337feb", "color": "#ffffff"},
                     {"background-color": "#669eef", "color": "#ffffff"},
                     {"background-color": "#99b8f0", "color": "#324F85"},
                     {"background-color": "#c0d7f3", "color": "#324F85"},
                     {"background-color": "#f6f6f6", "color": "#324F85"}];

        var stepCallback = function(i) {
            if (i < 6) {
                selected.css(table[i]); // Set style from look up table.
                setTimeout(function() {stepCallback(i + 1);}, 50); // 7 steps at 50ms per step = 350ms
            } else {
                selected.removeAttr("style"); // When the animation ends remove the styles we've just added.
            }
        };

        if (selected.is(":visible")) { // Only apply animation is element is visible.
            selected.removeClass("active");
            stepCallback(0);
        } else {
            selected.removeClass("active");
        }
    };

    /**
     * Implement a colour fade using a CSS3 animation to fade active colour back to background.
     */
    var cssFade = function(selected) {
        // Invoked when the animation completes, resets styles.
        var completeCallback = function() {
            selected.removeClass("fade").unbind(ANIMATION_END_EV);
        };

        if (selected.is(":visible")) { // Only apply animation is element is visible.
            selected.removeClass("active").addClass("fade").bind(ANIMATION_END_EV, completeCallback);
        } else {
            selected.removeClass("active");
        }
    };

    /**
     * Implement a horizontal slide from the currentPage to the newPage using jQuery animate() of the left and right
     * css properies. If isReverse is true the slide is left to right otherwise the slide is right to left.
     * This is the default slide implementation, which may be overridden if CSS3 animations are supported.
     */
    var slide = function(currentPage, newPage, isReverse) {
        var width = isReverse ? -currentPage.outerWidth() : currentPage.outerWidth();
        var left = newPage.hasClass("popup") ? 0 : _mainLeft;

        // Invoked when the animation completes, resets style and hides the old page and rebinds the START_EV.
        var completeCallback = function() {
            currentPage.hide(); // Hide the current page after animation completes.
            currentPage.css({"left": left + "px", "right": "0"}); // Reset css back to original settings.
            BODY.bind(START_EV, handlePointer); // Re-enable START_EV when transition completes.
        };

        BODY.unbind(START_EV, handlePointer); // Disable START_EV until transition completes.
        currentPage.trigger("hide"); // Trigger the hide handler *before* the animation.
        newPage.css({"left": left + width + "px", "right": -width + "px"}).show().trigger("show").
                animate({left: left, right: 0}, 350);
        var selected = newPage.find(".active").removeClass("active");
        if (isReverse) {
            fade(selected);
        }
        currentPage.animate({left: left - width, right: width}, 350, completeCallback);
    };

    /**
     * Implement a horizontal slide from the currentPage to the newPage using a CSS3 animation.
     * This should give a much smoother animation than the jQuery one and is GPU accelerated on some devices.
     * If isReverse is true the slide is left to right otherwise the slide is right to left.
     */
    var cssSlide = function(currentPage, newPage, isReverse) {
        var width = isReverse ? -currentPage.outerWidth() : currentPage.outerWidth();
        var classes = "slideIn slideOut reverse";

        // Invoked when the animation completes, resets styles and hides the old page and rebinds the START_EV.
        var completeCallback = function() {
            newPage.removeClass(classes);
            currentPage.hide().removeClass(classes).unbind(ANIMATION_END_EV, completeCallback);
            BODY.bind(START_EV, handlePointer); // Re-enable START_EV when transition completes.
        };

        BODY.unbind(START_EV, handlePointer); // Disable START_EV until transition completes.

        var reverse = isReverse ? " reverse" : "";
        currentPage.trigger("hide"); // Trigger the hide handler *before* the animation.
        newPage.show().trigger("show").addClass("slideIn" + reverse);
        var selected = newPage.find(".active").removeClass("active");
        if (isReverse) {
            fade(selected);
        }
        currentPage.bind(ANIMATION_END_EV, completeCallback).addClass("slideOut" + reverse);
    };

    /**
     * Implement a pop up to the newPage using jQuery animate() of the top, bottom and rgba css properies.
     * If isReverse is true the popup slides down otherwise it slides up.
     * This is the default popup implementation, which may be overridden if CSS3 animations are supported.
     */
    var popup = function(currentPage, newPage, isReverse) {
        fade(currentPage.find(".active"));
        var height = currentPage.outerWidth();
        var background = $(".popup-window");
        var container = $(".popup-container");

        var setOpacity = function(i) { // Animate opacity value.
            if (!IS_IE || IE_VERSION > 8) { // CSS rgba is supported by modern browsers.
                background.css({"background-color": "rgba(0, 0, 0, 0." + i + ")"});
            } else { // IE8 and below don't support rgba so use MS DXImageTransform filter.
                // This is a bit subtle, unfortunately progid:DXImageTransform messes with the fonts so we only use
                // it to animate the opacity, for the final black with 50% alpha effect we add the "smoked" style
                // which uses a png image background, the combination gives fairly smooth animation and normal font.
                var hexAlpha = (Math.round(25.6 * i)).toString(16); // Convert index to a hex alpha value.
                hexAlpha = (hexAlpha.length < 2) ? "0" + hexAlpha : hexAlpha; // Pad to two hex digits if necessary.
                hexAlpha = "#" + hexAlpha + "000000"; // Modify the alpha of a black  background.
                background.css({"filter": "progid:DXImageTransform.Microsoft.gradient(startColorstr=" + 
                                            hexAlpha + ",endColorstr=" + hexAlpha + ")"});
            }
        };

        var removeStyles = function(i) {
            if (IE_VERSION <= 6) { // For IE6 we've added other dynamic styles, so we need to preserve those.
            } else { // For every other browser we remove all dynamic styling.
                background.removeAttr("style");
            }
        };
    
        var fadeInBackground = function(i) { // Animate rgba opacity property from 0.0 to 0.5.
            if (i < 6) {
                setOpacity(i);
                setTimeout(function() {fadeInBackground(i + 1);}, 50);
            } else {
                removeStyles(); // When the animation ends remove the styles we've just added.
                background.addClass("smoked"); // Only does anything for IE < 9
            }
        };

        var fadeOutBackground = function(i) { // Animate rgba opacity property from 0.5 to 0.0.
            if (i >= 0) {
                setOpacity(i);
                setTimeout(function() {fadeOutBackground(i - 1);}, 50);
            } else {
                removeStyles(); // When the animation ends remove the styles we've just added.
                background.hide();
                newPage.trigger("show");
            }
        };

        currentPage.trigger("hide"); // Trigger the hide handler *before* the animation.
        if (isReverse) {
            // Wrapping in a timeout prevents IE8 glitching button styles when returning from :active state.
            setTimeout(function() {
                background.removeClass("smoked");
                fadeOutBackground(5);
            }, 10);

            container.css({"top": "64px", "bottom": "64px"}).
                      animate({top: height + "px", bottom: 128 - height + "px"}, 350);
        } else {
            $(".popup").hide();
            newPage.show().trigger("show");
            background.show();
            fadeInBackground(0);
            container.css({"top": height + "px", "bottom": 128 - height + "px"}).
                animate({top: "64px", bottom: "64px"}, 350);
        }
    };

    /**
     * Implement a pop up to the newPage using a CSS3 animation.
     * This should give a much smoother animation than the jQuery one and is GPU accelerated on some devices.
     * If isReverse is true the popup slides down otherwise it slides up.
     */
    var cssPopup = function(currentPage, newPage, isReverse) {
        fade(currentPage.find(".active"));
        var background = $(".popup-window");
        var container = $(".popup-container");
        background.removeClass("dissolveIn50 dissolveOut50");
        container.removeClass("slideUp slideDown");

        // Invoked when the animation completes, hides the background.
        var completeCallback = function() {
            background.hide();
            container.unbind(ANIMATION_END_EV, completeCallback);
            newPage.trigger("show");
        };

        currentPage.trigger("hide"); // Trigger the hide handler *before* the animation.
        if (isReverse) {
            background.addClass("dissolveOut50");
            container.bind(ANIMATION_END_EV, completeCallback).addClass("slideDown");
        } else {
            $(".popup").hide();
            newPage.show().trigger("show");
            background.show().addClass("dissolveIn50");
            container.addClass("slideUp");
        }
    };

    /**
     * Implement setting the left css attribute of the supplied element using standard jQuery css call.
     */
    var setPosition = function(element, offset, left, duration) {
        if (duration != null) {
            element.animate({left: left}, duration);
        } else {
            element.css("left", left + "px");
        }
    };

//-------------------------------------------------------------------------------------------------------------------
//                   Add HTML5 placeholder support to browsers that don't have native support.        
//-------------------------------------------------------------------------------------------------------------------

    var addPlaceholderSupport = function(inputs) {
        inputs.each(function() {
            var jthis = $(this);
            // Add textarea class to allow textarea placeholder to be styled differently to input placeholder.
            var classes = jthis.is("textarea") ? "placeholder textarea" :  "placeholder";

            jthis.focus(function() {
                jthis.siblings("span").hide();
            }).blur(function() {
                if (jthis.val() == "") {
                    jthis.siblings("span").show();
                }
            }).parent().append("<span class='" + classes + "'>" + jthis.attr("placeholder") + "</span>");
        });
    };

//-------------------------------------------------------------------------------------------------------------------
//         Some IE specific code to manipulate additional styles to help "pretty up" earlier versions of IE.         
//-------------------------------------------------------------------------------------------------------------------

    /**
     * For IE6 adjust the size as it doesn't seem to be computed correctly in pure CSS.
     * To be really nice we should probably compute SCROLLBAR, HEADER, PADDING but hey, it's only for IE6 :-)
     */
    var adjustSize = function() {
        var SCROLLBAR = 20;
        var HEADER = 44;
        var PADDING = 128;
        var width = BODY.outerWidth() + SCROLLBAR;
        var height = $(window).outerHeight();
        var mainWidth = width - _mainLeft;
        $(".main").css({"width": mainWidth + "px"});
        $(".main, .sidebar").css({"height": (height - HEADER) + "px"});
        $(".popup-window").css({"width": width + "px", "height": height + "px"});
        $(".popup-container").css({"width": (width - width * 0.4) + "px",
                                   "height": (height - (PADDING + HEADER)) + "px"});
        // Render each list (this reskins checkboxes & radio buttons and adds the border radius on old browsers).
        $("ul.list").each(function() {iTablet.renderList($(this));});
    };

    /**
     * Among its long list of failings IE6 doesn't properly support multiple classes. The active class is used in
     * several places, so this method overrides jQuery's addClass and removeClass in order to create IE6 specific
     * classes when the active or checked class is added or removed. It's a messy, but fairly effective approach.
     */
    var mergeClasses = function() {
        // Merge blue and back classes - this only works if blue is added statically which is how it's expected to be.
        $("a.blue.back.button").addClass("blue-back");

        var addClass = jQuery.fn.addClass; // Retrieve original jQuery addClass.
        jQuery.fn.addClass = function() {  // Override jQuery addClass.
            var jthis = $(this);
            // Execute the original method and save result.
            var result = addClass.apply(this, arguments);

            var length = arguments.length;
            for (var i = 0; i < length; i++) {
                if (arguments[i] == "checked") {
                    if (jthis.hasClass("arrow")) {
                        jthis.addClass("ie6-checked-arrow");
                    }
                }

                if (arguments[i] == "active") {
                    if (jthis.hasClass("arrow")) {
                        jthis.addClass("ie6-arrow-active");
                    }

                    if (jthis.hasClass("checked")) {
                        jthis.addClass("ie6-checked-active");
                    }

                    if (jthis.hasClass("radio") && jthis.hasClass("arrow")) {
                        jthis.addClass("ie6-radio-arrow-active");
                    } 

                    if (jthis.hasClass("checked") && jthis.hasClass("arrow")) {
                        jthis.addClass("ie6-checked-arrow-active");
                    } 
                }
            }

            return result;
        };

        var removeClass = jQuery.fn.removeClass; // Retrieve original jQuery removeClass.
        jQuery.fn.removeClass = function() {     // Override jQuery removeClass.
            var jthis = $(this);
            // Execute the original method and save result.
            var result = removeClass.apply(this, arguments);

            var length = arguments.length;
            for (var i = 0; i < length; i++) {
                if (arguments[i] == "checked") {
                    jthis.removeClass("ie6-checked-arrow");
                }

                if (arguments[i] == "active") {
                    jthis.removeClass("ie6-arrow-active ie6-checked-active ie6-radio-arrow-active ie6-checked-arrow-active");
                }
            }

            return result;
        };
    };

    /**
     * Sort out quirks for various versions of IE less than 9 - IE9 is fortunately *fairly* well behaved.
     */
    var fixIEQuirks = function() {
        if (IE_VERSION < 8) { // For IE7 and below.
            $("textarea").parent().addClass("textarea"); // Add textarea class to parent <li> - used by IE7 CSS styles.

            // Wrapping the sidebar in another div with zoom: 1 to enable "layout mode" fixes an
            // annoying page transition slide animation quirk with IE < 8.
            BODY.prepend($("<div id='sidebar-wrapper'></div>"));
            $("#sidebar-wrapper").append($("#menu"));

            // Sort out button styling and issue with :active not being switched off.
            $("a.button").attr('hideFocus', 'hidefocus').
                          append("<div class='before'></div><div class='after'></div>").
                          bind("click mouseleave", function() {this.blur();});
            $("ul.contents li a").attr('hideFocus', 'hidefocus'); // Fix lack of outline: none; in IE < 8
        }

        if (IE_VERSION < 7) { // IE6 is a real drag......
            adjustSize();   // Set css width and height as it doesn't seem to be computed correctly in pure CSS.
            mergeClasses(); // IE < 7 doesn't support multiple classes so merge them into IE specific class.
            $(window).resize(adjustSize);
        } else { // For IE7 and above just render list.
            // Render each list (this reskins checkboxes & radio buttons and adds the border radius on old browsers).
            $("ul.list").each(function() {iTablet.renderList($(this));});
        }
    }

//-------------------------------------------------------------------------------------------------------------------
//                            Initialise when DOM loads using (shorthand) jQuery.ready()
//-------------------------------------------------------------------------------------------------------------------

    $(function() {
        /*
         * These handlers help make the UI behave more like a User Interface than a web page. The selectstart handler
         * disables selection on everything other than input/textarea etc. the dragstart handler disables IE image
         * dragging, the touchmove handler disables mobile Safari web page "bounce" and the contextmenu handler
         * disables the right click context menu. In a web page disabling this stuff might be frowned on, but in
         * a web app it makes the application look and feel much more like a native app, which is rather the point.
         */
        $(document).bind("selectstart contextmenu", function(e) {
            return $(e.target).is('input, textarea, select, option');});
        $(document).bind("dragstart touchmove submit", function (e) {e.preventDefault();});
        $(document).bind("orientationchange", function (e) {scrollTo(0, 0);}); // Make sure position is OK
        $(document).keyup(handleKeyboard);

        // jQuery object for body is used in several places so let's cache it.
        BODY = $("body");

        _mainLeft = parseInt($(".main").css("left"), 10);

        //IS_MOBILE = BODY.hasClass("mobile"); // We use the class "mobile" for mobile devices.
        IS_MOBILE = (_mainLeft == 0); // For mobile devices there is no sidebar.
        if (IS_MOBILE) {
            _history = [{href:"#menu?", transition:null}];
        }

        // Prevent the browser trying to navigate to the href itself onclick then bind pointer Events to handlePointer.
        BODY.bind(START_EV + " click", handlePointer);

        // Ensure various form fields behave correctly with iScroll.
        $("input, textarea, select").bind(START_EV, function(e) {e.stopPropagation();});

        // Detect input and textarea placeholders separately as some browsers (Opera 11) have native support for
        // input placeholders but not textarea placeholders.
        if (!("placeholder" in $("<input>")[0])) { // Add HTML5 input placeholder support if not natively present.
            addPlaceholderSupport($("input[placeholder]"));
        }

        if (!("placeholder" in $("<textarea>")[0])) { // Add HTML5 textarea placeholder support if not natively present.
            addPlaceholderSupport($("textarea[placeholder]"));
        }

	    $.event.special.textchange = new TextChange(); // Add support for textchange Events on text input and textarea.

        // This block looks for scroll-areas and constructs an iScroll touch scroller. Note that scrollers are indexed
        // by the containing "main" page not by the id of the scroll-area. The latter id is only used by iScroll itself.
        $(".scroll-area").each(function(index) {
            var parent = $(this).parent();
            parent.bind("refresh", handleRefresh); // Bind the synthetic refresh event to the refresh handler
            var parentId = parent.attr("id");

            if (TOUCH_ENABLED) {
                _scrollers[parentId] = new iScroll(this);
            } else { // Adds a dummy scroller object if not a touch device.
                _scrollers[parentId] = {refresh: function() {}};
            }
        });

        initialiseCSSAnimations();
        _transitions = {"pop": popup};

        iTablet.location = new Location("#" + $(".main").attr("id"));

        if (IS_IE) {
            fixIEQuirks();
        } else {
            // Render each list (this reskins checkboxes & radio buttons and adds the border radius on old browsers).
            $("ul.list").each(function() {iTablet.renderList($(this));});
        }

        /*
         * iOS6 safari has a number of "quirks" that seem to be related to overly aggressive caching, one of these
         * these relates to attempting to reuse browser connections before the HTTP response has returned. In theory
         * this may be a good thing as it allows better "pipelining" on persistent connections, but in practice
         * if the "long polling" pattern is being used it can really mess things up for image "rollovers" when
         * dynamic state gets changed. This usually manifests itself by the active images taking an age to appear.
         * The following line works around this issue by initially adding the active state to the relevant items
         * this forces the active images to be loaded, which are then subsequently cached.
         */
        $("div.main ul.list li").addClass("active");

        $(".main, .popup-window").hide();
    });
};

