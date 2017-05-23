package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.Period;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;

import static org.hl7.fhir.dstu3.model.codesystems.V3ActCode.*;
import static org.openmrs.module.fhir.MRSProperties.MRS_INPATIENT_VISIT_TYPE;
import static org.openmrs.module.fhir.MRSProperties.MRS_OUTPATIENT_VISIT_TYPE;

@Component
public class FHIRMapper {
    public FHIREncounterMapper fhirEncounterMapper;
    private FHIRSubResourceMapper fhirSubResourceMapper;
    private EncounterService encounterService;
    private ProviderLookupService providerLookupService;
    private VisitService visitService;

    @Autowired
    public FHIRMapper(FHIREncounterMapper fhirEncounterMapper, FHIRSubResourceMapper fhirSubResourceMapper,
                      EncounterService encounterService, ProviderLookupService providerLookupService, VisitService visitService) {
        this.fhirEncounterMapper = fhirEncounterMapper;
        this.fhirSubResourceMapper = fhirSubResourceMapper;
        this.encounterService = encounterService;
        this.providerLookupService = providerLookupService;
        this.visitService = visitService;
    }

    public Encounter map(Patient emrPatient, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) throws ParseException {
        org.hl7.fhir.dstu3.model.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        Encounter openmrsEncounter = fhirEncounterMapper.map(emrPatient, shrEncounterBundle, systemProperties);

        addEncounterType(fhirEncounter, openmrsEncounter);
        addEncounterLocation(fhirEncounter, openmrsEncounter);
        addEncounterProviders(fhirEncounter, openmrsEncounter);

        fhirSubResourceMapper.map(openmrsEncounter, shrEncounterBundle, systemProperties);
        return openmrsEncounter;
    }

    private void addEncounterProviders(org.hl7.fhir.dstu3.model.Encounter fhirEncounter, Encounter openmrsEncounter) {
        List<Provider> encounterProviders = fhirEncounterMapper.getEncounterProviders(fhirEncounter);
        EncounterRole unknownRole = encounterService.getEncounterRoleByUuid(EncounterRole.UNKNOWN_ENCOUNTER_ROLE_UUID);
        if (CollectionUtils.isEmpty(openmrsEncounter.getEncounterProviders())) {
            Provider provider = providerLookupService.getShrClientSystemProvider();
            encounterProviders.add(provider);
        }
        for (Provider encounterProvider : encounterProviders) {
            openmrsEncounter.addProvider(unknownRole, encounterProvider);
        }
    }

    private void addEncounterLocation(org.hl7.fhir.dstu3.model.Encounter fhirEncounter, Encounter openmrsEncounter) {
        Location facilityLocation = fhirEncounterMapper.getEncounterLocation(fhirEncounter);
        openmrsEncounter.setLocation(facilityLocation);
    }

    public void addEncounterType(org.hl7.fhir.dstu3.model.Encounter fhirEncounter, Encounter openmrsEncounter) {
        EncounterType encounterType = fhirEncounterMapper.getEncounterType(fhirEncounter);
        openmrsEncounter.setEncounterType(encounterType);
    }

    public VisitType getVisitType(ShrEncounterBundle shrEncounterBundle) {
        org.hl7.fhir.dstu3.model.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        List<VisitType> allVisitTypes = visitService.getAllVisitTypes();
        String visitName = getVistNameFromEncounterClass(fhirEncounter.getClass_().getCode());
        if (visitName != null) {
            VisitType encVisitType = identifyVisitTypeByName(allVisitTypes, visitName);
            if (encVisitType != null) {
                return encVisitType;
            }
            if (visitName.equals(IMP.toCode())) {
                return identifyVisitTypeByName(allVisitTypes, MRS_INPATIENT_VISIT_TYPE);
            }
        }
        return identifyVisitTypeByName(allVisitTypes, MRS_OUTPATIENT_VISIT_TYPE);
    }

    private String getVistNameFromEncounterClass(String encounterClass) {
        HashMap<String, String> encounterClassToVisitTypeNameMap = getStringStringHashMap();
        return encounterClassToVisitTypeNameMap.get(encounterClass);
    }

    private VisitType identifyVisitTypeByName(List<VisitType> allVisitTypes, String visitTypeName) {
        VisitType encVisitType = null;
        for (VisitType visitType : allVisitTypes) {
            if (visitType.getName().equalsIgnoreCase(visitTypeName)) {
                encVisitType = visitType;
                break;
            }
        }
        return encVisitType;
    }

    public Period getVisitPeriod(ShrEncounterBundle shrEncounterBundle) {
        org.hl7.fhir.dstu3.model.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        return fhirEncounter.getPeriod();
    }

    private HashMap<String, String> getStringStringHashMap() {
        HashMap<String, String> encounterClassToVisitTypeNameMap = new HashMap<>();
        encounterClassToVisitTypeNameMap.put(IMP.toCode(), MRSProperties.MRS_INPATIENT_VISIT_TYPE);
        encounterClassToVisitTypeNameMap.put(FLD.toCode(), MRSProperties.MRS_FIELD_VISIT_TYPE);
        encounterClassToVisitTypeNameMap.put(AMB.toCode(), MRSProperties.MRS_CARE_SETTING_FOR_OUTPATIENT);
        encounterClassToVisitTypeNameMap.put(HH.toCode(), MRSProperties.MRS_HOME_VISIT_TYPE);
        encounterClassToVisitTypeNameMap.put(EMER.toCode(), MRSProperties.MRS_EMERGENCY_VISIT_TYPE);
        return encounterClassToVisitTypeNameMap;
    }
}
