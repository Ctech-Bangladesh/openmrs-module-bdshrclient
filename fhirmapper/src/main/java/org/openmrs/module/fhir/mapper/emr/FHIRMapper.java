package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.Period;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;

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

        addEncounterType(fhirEncounter, openmrsEncounter, systemProperties);
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

    public void addEncounterType(org.hl7.fhir.dstu3.model.Encounter fhirEncounter, Encounter openmrsEncounter, SystemProperties systemProperties) {
        EncounterType encounterType = fhirEncounterMapper.getEncounterType(fhirEncounter, systemProperties);
        openmrsEncounter.setEncounterType(encounterType);
    }

    public VisitType getVisitType(ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        org.hl7.fhir.dstu3.model.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        List<VisitType> allVisitTypes = visitService.getAllVisitTypes();
        String visitName = getVistNameFromEncounterClass(fhirEncounter.getClass_().getCode(), systemProperties);
        if (visitName != null) {
            VisitType encVisitType = identifyVisitTypeByName(allVisitTypes, visitName);
            if (encVisitType != null) {
                return encVisitType;
            }
        }
        return identifyVisitTypeByName(allVisitTypes, MRS_OUTPATIENT_VISIT_TYPE);
    }

    private String getVistNameFromEncounterClass(String encounterClass, SystemProperties systemProperties) {
        HashMap<String, String> encounterClassToVisitTypeMap = systemProperties.getEncounterClassToVisitTypeMap();
        return encounterClassToVisitTypeMap.get(encounterClass);
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

}
