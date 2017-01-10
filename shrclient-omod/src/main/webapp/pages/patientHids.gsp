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

    input {
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
        height:250px;
        margin: 0.5%;
        display: inline-block;
        font-weight: bold;
        font-size: 17px;
        font-family: monospace;
    }

    #printArea .healthId .patient_details{
        height: 170px;
    }

    #printArea .healthId .details_1{
        height:90px;
    }

    #printArea .healthId .hid_details{
        height: 100px;
        text-align: center;
    }

    #printArea .healthId img {
        margin: 0;
        float: left;
        width:120px;
        height:90px;
    }

    #printArea .healthId  label {
        margin: 0.5%;
    }

    #printArea .healthId .details_1 .name,.issued{
        display:block
    }

    #printArea .healthId .details_1 .dob{
        float:right;
        margin: 0% 2% 0% 0%;
    }

    #printArea .healthId .address{
        display: block;
        margin-left: 5px;
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
                JsBarcode(".barcode").init();
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
                <div class="patient_details">
                    <img src="${ ui.resourceLink("shrclient", "images/gov_logo.jpg")}" alt="dhis_logo"/>
                    <div class="details_1">
                        <label class="name">Name: {{name}}</label>
                        <label class="gender">Gender: {{gender}}</label>
                        <label class="dob">DOB: {{dob}}</label>
                        <label class="issued">Issued Date: {{issuedDate}}</label>
                    </div>
                    <label class="address">Address: {{address}}</label>
                </div>
                <div class="hid_details">
                    <svg class="barcode" jsbarcode-height="35px" jsbarcode-format="CODE39"
                         jsbarcode-value="{{hid}}" jsbarcode-textmargin="0"
                         jsbarcode-fontoptions="bold"></svg>

                </div>
            </div>
            {{/cards}}
        </div>
    </div>
</div>
</body>
</html>



