package org.openmrs.module.fhir.utils;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_ORDER_TYPE_TO_FHIR_CODE_MAPPINGS;

@Component
public class GlobalPropertyLookUpService {

    public String getGlobalPropertyValue(String propertyName) {
        AdministrationService administrationService = Context.getAdministrationService();
        String propertyValue = administrationService.getGlobalProperty(propertyName);

        if (propertyValue != null && !propertyValue.isEmpty()) {
            return propertyValue;
        }
        return null;
    }

    public List<OpenMRSOrderTypeMap> getConfiguredOrderTypes() {
        String orderTypes = getGlobalPropertyValue(GLOBAL_PROPERTY_ORDER_TYPE_TO_FHIR_CODE_MAPPINGS);
        if (StringUtils.isBlank(orderTypes)) return Collections.emptyList();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return asList(mapper.readValue(orderTypes, OpenMRSOrderTypeMap[].class));
        } catch (Exception e) {
            throw new RuntimeException(String.format("Invalid Property value for %s", GLOBAL_PROPERTY_ORDER_TYPE_TO_FHIR_CODE_MAPPINGS));
        }
    }
}
