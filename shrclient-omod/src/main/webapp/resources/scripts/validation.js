    function isPatientIdValid(idType, patientId) {
    if (idType === "nid") {
        if (!patientId.match("^(\\d{13}|\\d{17})$")) {
            jq(".errorMessage").text("National Id should be 13 or 17 digit number.");
            jq(".errorMessage").show();
            return false;
        }
        return true;
    }
    if (idType === "hid") {
        if (!patientId.match("^\\d{11}$")) {
            jq(".errorMessage").text("Health Id should be 11 digit number.");
            jq(".errorMessage").show();
            return false;
        }
        return true;
    }
    if (idType === "brn") {
        if (!patientId.match("^\\d{17}$")) {
            jq(".errorMessage").text("Birth Registration Number should be 17 digits number.");
            jq(".errorMessage").show();
            return false;
        }
        return true;
    }
    if (idType === "uid") {
        if (!patientId.match("^[a-zA-Z0-9]{11}$")) {
            jq(".errorMessage").text("Unique Identifier should be 11 characters.");
            jq(".errorMessage").show();
            return false;
        }
        return true;
    }
    if (idType === "houseHoldCode") {
        if (!patientId.match("^\\d+$")) {
            jq(".errorMessage").text("Household Id should be a number.");
            jq(".errorMessage").show();
            return false;
        }
        return true;
    }
    if (idType === "phoneNo") {
        if (patientId.match("^\\d{1,12}$")) {
            return true;
        }
        jq(".errorMessage").text("Phone Number should be between 1 to 12 digit.");
        jq(".errorMessage").show();
        return false;
    }
    return false;
}