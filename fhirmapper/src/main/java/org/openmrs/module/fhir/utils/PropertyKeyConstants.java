package org.openmrs.module.fhir.utils;

public class PropertyKeyConstants {
    public static final String PROVIDER_REFERENCE_PATH = "pr.referenceUrl";
    public static final String FACILITY_URL_FORMAT = "fr.facilityUrlFormat";
    public static final String FACILITY_REFERENCE_PATH = "fr.referenceUrl";
    public static final String LOCATION_REFERENCE_PATH = "lr.referenceUrl";

    public static final String MCI_REFERENCE_PATH = "mci.referenceUrl";
    public static final String MCI_PATIENT_CONTEXT = "mci.patientContext";

    public static final String SHR_REFERENCE_PATH = "shr.referenceUrl";
    public static final String SHR_CATCHMENT_PATH_PATTERN = "shr.catchmentPathPattern"; ///catchments/%s/encounters
    public static final String SHR_PATIENT_ENC_PATH_PATTERN = "shr.patientEncPathPattern"; ///patients/%s/encounters

    public static final String IDP_SERVER_URL = "idP.referenceUrl";
    public static final String IDP_SIGNIN_PATH = "idP.signinPath";

    public static final String FACILITY_CATCHMENTS = "facility.catchments";
    public static final String FACILITY_EMAIL_KEY = "facility.email";
    public static final String FACILITY_PASSWORD_KEY = "facility.password";
    public static final String FACILITY_ID = "facility.facilityId";

    public static final String TR_REFERENCE_PATH = "tr.referenceUrl";
    public static final String TR_VALUESET_PATH_INFO = "tr.valueset.url.pathInfo";
    public static final String TR_VALUESET_ROUTE = "tr.valueset.route";
    public static final String TR_VALUESET_QTY_UNITS = "tr.valueset.quantityunits";
    public static final String TR_VALUESET_IMMUNIZATION_REASON = "tr.valueset.immunizationReason";
    public static final String TR_VALUESET_REFUSAL_REASON = "tr.valueset.refusalReason";
}
