package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.dstu2.composite.PeriodDt;
import ca.uhn.fhir.model.dstu2.valueset.EncounterClassEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.ShrClientCallback;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.MRS_IN_PATIENT_VISIT_TYPE;
import static org.openmrs.module.fhir.MRSProperties.MRS_OUT_PATIENT_VISIT_TYPE;

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
        ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        Encounter openmrsEncounter = fhirEncounterMapper.map(emrPatient, shrEncounterBundle, systemProperties);

        addEncounterType(fhirEncounter, openmrsEncounter);
        addEncounterLocation(fhirEncounter, openmrsEncounter);
        addEncounterProviders(fhirEncounter, openmrsEncounter);

        fhirSubResourceMapper.map(openmrsEncounter, shrEncounterBundle, systemProperties);
        return openmrsEncounter;
    }

    private void addEncounterProviders(ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter, Encounter openmrsEncounter) {
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

    private void addEncounterLocation(ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter, Encounter openmrsEncounter) {
        Location facilityLocation = fhirEncounterMapper.getEncounterLocation(fhirEncounter);
        openmrsEncounter.setLocation(facilityLocation);
    }

    public void addEncounterType(ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter, Encounter openmrsEncounter) {
        EncounterType encounterType = fhirEncounterMapper.getEncounterType(fhirEncounter);
        openmrsEncounter.setEncounterType(encounterType);
    }

    public VisitType getVisitType(ShrEncounterBundle shrEncounterBundle) {
        ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        String encounterClass = fhirEncounter.getClassElement();
        List<VisitType> allVisitTypes = visitService.getAllVisitTypes();
        if (encounterClass != null) {
            VisitType encVisitType = identifyVisitTypeByName(allVisitTypes, encounterClass);
            if (encVisitType != null) {
                return encVisitType;
            }

            if (encounterClass.equals(EncounterClassEnum.INPATIENT.getCode())) {
                return identifyVisitTypeByName(allVisitTypes, MRS_IN_PATIENT_VISIT_TYPE);
            }
        }
        return identifyVisitTypeByName(allVisitTypes, MRS_OUT_PATIENT_VISIT_TYPE);
    }

        private VisitType identifyVisitTypeByName (List < VisitType > allVisitTypes, String visitTypeName){
            VisitType encVisitType = null;
            for (VisitType visitType : allVisitTypes) {
                if (visitType.getName().equalsIgnoreCase(visitTypeName)) {
                    encVisitType = visitType;
                    break;
                }
            }
            return encVisitType;
    }

    public PeriodDt getVisitPeriod(ShrEncounterBundle shrEncounterBundle) {
        ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        return fhirEncounter.getPeriod();
    }
}
