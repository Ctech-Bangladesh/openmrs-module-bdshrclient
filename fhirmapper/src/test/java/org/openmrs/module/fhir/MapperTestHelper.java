package org.openmrs.module.fhir;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import org.apache.commons.collections4.Predicate;
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
    public IResource loadSampleFHIREncounter(String filePath, ApplicationContext springContext) throws Exception {
        org.springframework.core.io.Resource resource = springContext.getResource(filePath);
        String bundleXML = org.apache.commons.io.IOUtils.toString(resource.getInputStream());
        return (IResource) FhirContextHelper.getFhirContext().newXmlParser().parseResource(bundleXML);
    }

    public static SystemProperties getSystemProperties(String facilityId) {
        Properties facilityRegistry = new Properties();
        facilityRegistry.setProperty(FACILITY_REFERENCE_PATH, "http://hrmtest.dghs.gov.bd/api/1.0/facilities");

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
        providerRegistry.setProperty(PropertyKeyConstants.PROVIDER_REFERENCE_PATH, "http://hrmtest.dghs.gov.bd/api/1.0/providers");

        Properties facilityInstanceProperties = new Properties();
        facilityInstanceProperties.setProperty(FACILITY_ID, facilityId);

        HashMap<String, String> baseUrls = new HashMap<>();
        baseUrls.put("mci", "http://mci");
        baseUrls.put("tr", "http://tr");

        Properties mciProperties = new Properties();
        mciProperties.put(PropertyKeyConstants.MCI_REFERENCE_PATH, "http://public.com/");
        mciProperties.put(PropertyKeyConstants.MCI_PATIENT_CONTEXT, "/api/default/patients");

        return new SystemProperties(baseUrls, facilityRegistry, trProperties, providerRegistry, facilityInstanceProperties, mciProperties);
    }

    public static boolean containsCoding(List<CodingDt> coding, final String code, final String system, final String display) {
        return exists(coding, new Predicate<CodingDt>() {
            @Override
            public boolean evaluate(CodingDt codingDt) {
                return codingDt.getCode().equals(code)
                        && codingDt.getSystem().equals(system)
                        && codingDt.getDisplay().equals(display);
            }
        });
    }
}
