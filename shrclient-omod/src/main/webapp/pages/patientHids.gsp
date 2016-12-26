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
        ui.includeJavascript("shrclient", "validation.js", Integer.MAX_VALUE - 30)
        ui.includeCss("uicommons", "styleguide/jquery.toastmessage.css", Integer.MAX_VALUE - 20)
    %>

    <style>
    body {
        font-family: Arial, Sans-serif;
        font-size: 12px;
        color: #333;
    }

    .btn {
        background: #88af28;
        color: #fff;
        border: 1px solid #88af28;
        font-size: 14px;
        border-radius: 3px;
    }

    .dateRange {
        padding: 5px;
        margin: 5px;
    }

    lebel, input {
        margin: 0px 10px 0px 10px;
        font-size: 18px;
    }

    .container {
        width: 960px;
        margin: 0 auto;
    }

    h1 {
        font-size: 24px;
        color: #438D80;
    }

    .dateRange div {
        display: inline-block;
    }

    select.user {
        border: 1px solid #ccc;
        height: 30px;
    }

    #printArea {
        display: none;
    }

    #printArea .healthId {
        border-style: solid;
        width: 48%;
        margin: 0.5%;
        display: inline-block;
    }

    #printArea .healthId img {
        height: 25%;
        width: 30%;
        margin: 0;
        float: left;
    }

    #printArea .healthId label {
        float: right;
        width: 70%;
        margin-top: 0.5%;
    }

    .errorMessage {
        padding: 2px 4px;
        margin: 0px;
        border: solid 1px #FBD3C6;
        background: #FDE4E1;
        color: #CB4721;
        font-family: Arial, Helvetica, sans-serif;
        font-size: 14px;
        font-weight: bold;
    }
    </style>

    ${ui.resourceLinks()}
</head>

<body>
<script type="text/javascript">
    function onError(error) {
        var message = "Error occurred. Could not perform the action.";
        jQuery(".errorMessage").text(message);
        jQuery(".errorMessage").show();
    }
    window.translations = window.translations || {};
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

        jQuery('#printAll').click(function (e) {
            var userId = jQuery('#user').val()
            var fromDate = jQuery('#fromDate').val()
            var toDate = jQuery('#toDate').val()

            jQuery.ajax({
                type: "GET",
                url: "/openmrs/ws/users/" + userId + "/findAllPatients?from=" + fromDate + "&to=" + toDate,
                dataType: "json"
            }).done(function (responseData) {
                var printArea = jQuery('#printArea');
                var template = printArea.html();
                Mustache.parse(template);
                var rendered = Mustache.render(template, {"cards": responseData});
                printArea.html(rendered);
                printArea.show();
            }).fail(onError);
        })
    })
</script>

<div id="body-wrapper">
    ${ui.includeFragment("uicommons", "infoAndErrorMessage")}
    <div id="content" class="container">
        <h1>Print Patient HIDs</h1>

        <div style="display:none" class="errorMessage"></div>
        <select id="user" class="user">
            <option value="-1" selected="selected">Select a user</option>
            {{#list}}
            <option value='{{id}}'>{{name}}</option>
            {{/list}}
        </select>

        <div class="dateRange">
            <div>
                <label for="fromDate">From:-</label>
                <input type="date" id="fromDate">
            </div>

            <div>
                <label for="toDate">To:-</label>
                <input type="date" id="toDate">
            </div>
            <button class="btn" id="printAll">Print</button>
        </div>

        <div id="printArea">
            {{#cards}}
            <div class="healthId">
                <img src="http://app.dghs.gov.bd/hrm-transfer/assets/dghs/images/gov_logo.jpg" alt="dhis_logo"/>
                <div class="details_1">
                    <label class="name">Name:-    {{name}}</label>
                    <label class="gender">Gender:-    {{gender}}</label>
                    <label class="dob">Date of Birth:-    {{dob}}</label>
                </div>
                <label class="address">Address:-        {{address}}</label>
                <label class="hid">HID:-    {{hid}}</label>

                <h1>Some Bar code</h1>
            </div>
            {{/cards}}
        </div>
    </div>
</div>
</body>
</html>



