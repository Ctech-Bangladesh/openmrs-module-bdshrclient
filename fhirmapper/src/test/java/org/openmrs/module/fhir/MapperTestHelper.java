package org.openmrs.module.fhir;

import org.apache.commons.collections4.Predicate;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Resource;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.collections4.CollectionUtils.exists;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_ID;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_REFERENCE_PATH;

public class MapperTestHelper {
    public Resource loadSampleFHIREncounter(String filePath, ApplicationContext springContext) throws Exception {
        org.springframework.core.io.Resource resource = springContext.getResource(filePath);
        String bundleXML = org.apache.commons.io.IOUtils.toString(resource.getInputStream());
        return (Resource) FhirContextHelper.getFhirContext().newXmlParser().parseResource(bundleXML);
    }

    public static SystemProperties getSystemProperties(String facilityId) {
        Properties facilityRegistry = new Properties();
        facilityRegistry.setProperty(FACILITY_REFERENCE_PATH, "http://localhost:9997/api/1.0/facilities");

        Properties trProperties = new Properties();
        trProperties.setProperty(PropertyKeyConstants.TR_REFERENCE_PATH, "http://localhost:9080");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_PATH_INFO, "openmrs/ws/rest/v1/tr/vs");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_RELATIONSHIP_TYPE, "Relationship-Type");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_ROUTE, "Route-of-Administration");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_QUANTITY_UNITS, "Quantity-Units");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_MEDICATION_FORMS, "Medication-Forms");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_MEDICATION_PACKAGE_FORMS, "Medication-Package-Forms");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_IMMUNIZATION_REASON, "Immunization-Reason");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_IMMUNIZATION_REFUSAL_REASON, "No-Immunization-Reason");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_PROCEDURE_OUTCOME, "Procedure-Outcome");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_PROCEDURE_FOLLOWUP, "Procedure-Followup");

        Properties providerRegistry = new Properties();
        providerRegistry.setProperty(PropertyKeyConstants.PROVIDER_REFERENCE_PATH, "http://localhost:9997/api/1.0/providers");

        Properties facilityInstanceProperties = new Properties();
        facilityInstanceProperties.setProperty(FACILITY_ID, facilityId);

        HashMap<String, String> baseUrls = new HashMap<>();
        baseUrls.put("mci", "http://mci");
        baseUrls.put("tr", "http://tr");

        Properties mciProperties = new Properties();
        mciProperties.put(PropertyKeyConstants.MCI_REFERENCE_PATH, "http://public.com/");
        mciProperties.put(PropertyKeyConstants.MCI_PATIENT_CONTEXT, "/api/default/patients");

        Properties shrProperties = new Properties();
        shrProperties.put(PropertyKeyConstants.SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(PropertyKeyConstants.SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");

        return new SystemProperties(facilityRegistry, trProperties, providerRegistry, facilityInstanceProperties, mciProperties, shrProperties);
    }

    public static boolean containsCoding(List<Coding> coding, final String code, final String system, final String display) {
        return exists(coding, new Predicate<Coding>() {
            @Override
            public boolean evaluate(Coding codingDt) {
                return (nullSafeEquals(code, codingDt.getCode()))
                        && (nullSafeEquals(system, codingDt.getSystem()))
                        && (nullSafeEquals(display, codingDt.getDisplay()));
            }
        });
    }

    private static boolean nullSafeEquals(String s1, String s2) {
        if (s1 != null && s2 != null) return s1.equals(s2);
        return s1 == null && s2 == null;
    }
}
