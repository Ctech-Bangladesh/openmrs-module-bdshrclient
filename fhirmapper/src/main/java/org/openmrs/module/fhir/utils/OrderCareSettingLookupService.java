package org.openmrs.module.fhir.utils;

import org.openmrs.CareSetting;
import org.openmrs.api.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.openmrs.module.fhir.MRSProperties.MRS_CARE_SETTING_FOR_INPATIENT;
import static org.openmrs.module.fhir.MRSProperties.MRS_CARE_SETTING_FOR_OUTPATIENT;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.getEncounter;

@Component
public class OrderCareSettingLookupService {
    @Autowired
    private OrderService orderService;

    public CareSetting getCareSetting() {
        //TODO : change to encounter
        return orderService.getCareSettingByName(MRS_CARE_SETTING_FOR_OUTPATIENT);
    }
}
