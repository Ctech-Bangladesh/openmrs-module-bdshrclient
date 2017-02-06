<html>
<head>
    <title>Print HealthIds</title>
    <link rel="shortcut icon" type="image/ico" href="/${ui.contextPath()}/images/openmrs-favicon.ico"/>
    <link rel="icon" type="image/png\" href="/${ui.contextPath()}/images/openmrs-favicon.png"/>

    <%
        ui.includeJavascript("uicommons", "jquery-1.8.3.min.js", Integer.MAX_VALUE)
        ui.includeCss("uicommons", "styleguide/jquery-ui-1.9.2.custom.min.css", Integer.MAX_VALUE - 10)
        ui.includeJavascript("uicommons", "jquery.toastmessage.js", Integer.MAX_VALUE - 20)
        ui.includeJavascript("shrclient", "mustache.js", Integer.MAX_VALUE - 30)
        ui.includeJavascript("shrclient", "jsBarCode.js", Integer.MAX_VALUE - 30)
        ui.includeCss("uicommons", "styleguide/jquery.toastmessage.css", Integer.MAX_VALUE - 20)
        ui.includeCss("shrclient", "printHid.css", Integer.MAX_VALUE - 10)
    %>


    ${ui.resourceLinks()}
</head>

<body>
<script type="text/javascript">
    function onError(error) {
        var message = "Error occurred. Could not perform the action.";
        if (error.status === 401) {
            message = "The user doesn't have privilege to access this page.";
            jQuery('div:not(.errorMessage)').hide();
            jQuery('#heading').appendTo('body');
            jQuery('.errorMessage').appendTo('body');
        }
        jQuery(".errorMessage").text(message);
        jQuery(".errorMessage").show();
    }

    function getDate(date) {
        var month = date.getMonth() + 1;
        var day = date.getDate();
        var year = date.getFullYear();

        if (month < 10)
            month = '0' + month.toString();
        if (day < 10)
            day = '0' + day.toString();

        return year + '-' + month + '-' + day;
    }

    function restrictDatesToToday() {
        var dtToday = new Date();

        var maxDate = getDate(dtToday);
        jQuery("#fromDate").attr('max', maxDate);
        jQuery("#toDate").attr('max', maxDate);
    }

    function validateFromDate() {
        var maxFromDate = new Date(jQuery("#toDate").val());
        if (maxFromDate != "") {
            var maxDate = getDate(maxFromDate);
            jQuery("#fromDate").attr('max', maxDate);
        }
    }

    function validateToDate() {
        var minToDate = new Date(jQuery("#fromDate").val());
        if (minToDate != "") {
            var minDate = getDate(minToDate);
            jQuery("#toDate").attr('min', minDate);
        }

    }

    function toggleGetAllButton() {
        if (jQuery('#user').val() != -1 &&
            jQuery('#fromDate').val().length > 0 &&
            jQuery('#toDate').val().length > 0) {
            jQuery('#getAll').prop("disabled", false);
        }
        else {
            jQuery('#getAll').prop("disabled", true);
        }
    }

    window.translations = window.translations || {};
    var template;
    jQuery(document).ready(function (e) {
        jQuery.ajax({
            type: "GET",
            url: "/openmrs/ws/users/getAll",
            dataType: "json"
        }).done(function (responseData) {
            var userSelect = jQuery('#user');
            var template = userSelect.html();
            Mustache.parse(template);
            var rendered = Mustache.render(template, {"list": responseData});
            userSelect.html(rendered)
        }).fail(onError);

        restrictDatesToToday();

        jQuery("#fromDate").on('change', validateToDate);
        jQuery("#toDate").on('change', validateFromDate);

        jQuery('#user, #fromDate, #toDate').change(toggleGetAllButton);

        jQuery('#getAll').click(function (e) {
            var userId = jQuery('#user').val();
            var fromDate = jQuery('#fromDate').val();
            var toDate = jQuery('#toDate').val();

            jQuery.ajax({
                type: "GET",
                url: "/openmrs/ws/users/" + userId + "/findAllPatients?from=" + fromDate + "&to=" + toDate,
                dataType: "json"
            }).done(function (responseData) {
                var printArea = jQuery('#printArea');
                var infoMessage;
                var noOfHIDCards = responseData.length;
                if (!responseData || noOfHIDCards < 1) {
                    infoMessage = "There are no patients registered within the selected period.";
                    jQuery("#info").text(infoMessage).show();
                    printArea.hide();
                    return;
                }
                infoMessage = "Showing " + noOfHIDCards + " Health ID card(s).";
                jQuery("#info").text(infoMessage).show();

                template = template || printArea.html();
                Mustache.parse(template);
                var finalEnlishToBanglaNumber = {
                    '0': '&#2534;',
                    '1': '&#2535;',
                    '2': '&#2536;',
                    '3': '&#2537;',
                    '4': '&#2538;',
                    '5': '&#2539;',
                    '6': '&#2540;',
                    '7': '&#2541;',
                    '8': '&#2542;',
                    '9': '&#2543;'
                };
                var data = {"cards": responseData};
                data.convertDigitToBangla = function () {
                    return function (text, render) {
                        var strArray = render(text).split("");
                        var retStr = "";
                        for (var x in strArray) {
                            if (isNaN(strArray[x])) {
                                retStr = retStr + strArray[x];
                            } else {
                                retStr = retStr + finalEnlishToBanglaNumber[strArray[x]];
                            }
                        }
                        return retStr;
                    }
                };
                data.getGenderInBangla = function () {
                    return function (text, render) {
                        var gender = render(text);
                        if (gender == "M") {
                            return '&#2474;&#2497;&#2480;&#2497;&#2487;'
                        } else if (gender == "F") {
                            return '&#2488;&#2509;&#2468;&#2509;&#2480;&#2496;'
                        } else if (gender == "O") {
                            return '&#2489;&#2495;&#2460;&#2465;&#2492;&#2494;'
                        }
                    }
                };

                var rendered = Mustache.render(template, data);
                printArea.html(rendered);
                JsBarcode(".barcode").init();
                printArea.show();
                jQuery('#print').prop('disabled', false);
            }).fail(onError);
        })
    })
</script>

<div id="body-wrapper">
    ${ui.includeFragment("uicommons", "infoAndErrorMessage")}
    <div id="content" class="container">
        <h1 id="heading">Print Patient HIDs</h1>

        <div style="display:none" class="errorMessage"></div>
        <label for="user">Select a user</label>
        <select id="user" class="user">
            <option value="-1" selected="selected">Select a user</option>
            {{#list}}
            <option value='{{id}}'>{{name}}</option>
            {{/list}}
        </select>

        <div class="dateRange">
            <div>
                <label for="fromDate">From</label>
                <input type="date" id="fromDate" onkeydown="return false">
            </div>

            <div>
                <label for="toDate">To</label>
                <input type="date" id="toDate" onkeydown="return false">
            </div>
            <button class="btn" id="getAll" disabled>Get All Patients</button>
            <button class="btn" id="print" onclick="window.print()" disabled>Print All</button>
        </div>

        <div id="info"></div>

        <div id="printArea">
            <div class="print_wrap">
                {{#cards}}
                <div class="healthId">
                    <div class="healthId_body">

                        <div class="heading">
                            <img src="${ui.resourceLink("shrclient", "images/gov_logo.jpg")}" alt="dhis_logo"/>

                            <div id="bdgov">&#2455;&#2467;&#2474;&#2509;&#2480;&#2460;&#2494;&#2468;&#2472;&#2509;&#2468;&#2509;&#2480;&#2496;&#32;&#2476;&#2494;&#2434;&#2482;&#2494;&#2470;&#2503;&#2486;&#32;&#2488;&#2480;&#2453;&#2494;&#2480;&#44;&#32;&#2488;&#2509;&#2476;&#2494;&#2488;&#2509;&#2469;&#2509;&#2479;&#32;&#2451;&#32;&#2474;&#2480;&#2495;&#2476;&#2494;&#2480;&#32;&#2453;&#2482;&#2509;&#2479;&#2494;&#2467;&#32;&#2478;&#2472;&#2509;&#2468;&#2509;&#2480;&#2467;&#2494;&#2482;&#2527;</div>

                            <div id="hidCard">Health ID card</div>
                        </div>

                        <div class="patient_details">
                            <div id="english_and_bangla_name">
                                <div class="name_no_overflow">
                                    <span class="label_tag" id="name_tag">&#2472;&#2494;&#2478;:</span>
                                    <span id="name">{{givenName}} {{familyName}}</span>
                                </div>

                                <div id="bangla_name" class="name_no_overflow">{{#givenNameLocal}}{{#familyNameLocal}}{{givenNameLocal}} {{familyNameLocal}}{{/familyNameLocal}}{{/givenNameLocal}}</div>
                            </div>

                            <div id="dob_and_gender">
                                <div class="form-field">
                                    <span class="label_tag"
                                          id="dob_tag">&#2460;&#2472;&#2509;&#2478; &#2468;&#2494;&#2480;&#2495;&#2454;:</span>
                                    <span id="dob">{{#convertDigitToBangla}}{{dob}}{{/convertDigitToBangla}}</span>
                                </div>

                                <div class="form-field">
                                    <span class="label_tag"
                                          id="gender_tag">&#2482;&#2495;&#2457;&#2509;&#2455;:</span>
                                    <span id="gender">{{#getGenderInBangla}}{{gender}}{{/getGenderInBangla}}</span>
                                </div>
                            </div>

                            <div id="hid_and_issued">
                                <div class="form-field">
                                    <span class="label_tag" id="hid_tag">HID:</span>
                                    <span id="hid">{{hid}}</span>
                                </div>

                                <div class="form-field">
                                    <span class="label_tag"
                                          id="issued_tag">&#2474;&#2509;&#2480;&#2470;&#2494;&#2472;&#2503;&#2480; &#2468;&#2494;&#2480;&#2495;&#2454;:</span>
                                    <span id="issued">{{#convertDigitToBangla}}{{issuedDate}}{{/convertDigitToBangla}}</span>
                                </div>
                            </div>

                            <div class="address_details">
                                <div class="address_line">
                                    <span class="label_tag"
                                          id="address_tag">&#2464;&#2495;&#2453;&#2494;&#2472;&#2494;:</span>
                                    <span id="address_line" class="address">{{address.address1}}</span>
                                </div>
                                <div id="address_levels" class="address address_levels">
                                    <span>{{#address.address2}}{{address.address2}}, {{/address.address2}}</span>
                                    <span>{{#address.address3}}{{address.address3}}, {{/address.address3}}</span>
                                    <span>{{#address.address4}}{{address.address4}}, {{/address.address4}}</span>
                                    <span>{{#address.address5}}{{address.address5}}, {{/address.address5}}</span>
                                    <span>{{#address.countyDistrict}}{{address.countyDistrict}}, {{/address.countyDistrict}}</span>
                                    <span>{{#address.stateProvince}}{{address.stateProvince}}{{/address.stateProvince}}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="hid_footer">
                            <svg class="barcode" jsbarcode-height="35px" jsbarcode-format="CODE39"
                                 jsbarcode-value="{{hid}}" jsbarcode-displayValue="false"
                                 jsbarcode-background="lightgrey"/>

                        <div class="disclaimer">&#2447;&#2439;&#32;&#2453;&#2494;&#2480;&#2509;&#2465;&#32;&#2455;&#2467;&#2474;&#2509;&#2480;&#2460;&#2494;&#2468;&#2472;&#2509;&#2468;&#2509;&#2480;&#2496;&#32;&#2476;&#2494;&#2434;&#2482;&#2494;&#2470;&#2503;&#2486;&#32;&#32;&#2488;&#2480;&#2453;&#2494;&#2480;&#2503;&#2480;&#32;&#2488;&#2478;&#2509;&#2474;&#2468;&#2509;&#2468;&#2495;&#2404;&#32;&#2453;&#2494;&#2480;&#2509;&#2465;&#2463;&#2495;&#32;&#2486;&#2497;&#2471;&#2497;&#2478;&#2494;&#2468;&#2509;&#2480;&#32;&#2476;&#2509;&#2479;&#2476;&#2489;&#2494;&#2480;&#2453;&#2494;&#2480;&#2496;&#2480;&#32;&#2460;&#2472;&#2509;&#2479;&#2404;&#32;&#2453;&#2494;&#2480;&#2509;&#2465;&#2463;&#2495;&#32;&#2437;&#2472;&#2509;&#2479;&#32;&#2453;&#2507;&#2469;&#2494;&#2451;&#32;&#2474;&#2494;&#2451;&#2527;&#2494;&#32;&#2455;&#2503;&#2482;&#2503;&#32;&#2472;&#2495;&#2453;&#2463;&#2488;&#2509;&#2469;&#32;&#2488;&#2509;&#2476;&#2494;&#2488;&#2509;&#2469;&#2509;&#2479;&#2453;&#2503;&#2472;&#2509;&#2470;&#2509;&#2480;&#2503;&#32;&#2460;&#2478;&#2494;&#32;&#2470;&#2503;&#2451;&#2527;&#2494;&#2480;&#32;&#2460;&#2472;&#2509;&#2479;&#32;&#2437;&#2472;&#2497;&#2480;&#2507;&#2471;&#32;&#2453;&#2480;&#2494;&#32;&#2489;&#2482;&#2404;</div>
                    </div>
                </div>
                {{/cards}}
            </div>
        </div>
    </div>
</div>
</body>
</html>



