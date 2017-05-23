package org.openmrs.module.fhir.mapper.bundler;


import org.apache.log4j.Logger;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.EncounterProvider;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.utils.OMRSLocationService;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Set;

import static org.hl7.fhir.dstu3.model.codesystems.V3ActCode.*;

@Component
public class EncounterMapper {

    @Autowired
    private OMRSLocationService omrsLocationService;
    @Autowired
    private ProviderLookupService providerLookupService;

    private Logger logger = Logger.getLogger(EncounterMapper.class);

    public FHIREncounter map(org.openmrs.Encounter openMrsEncounter, String healthId, SystemProperties systemProperties) {
        Encounter encounter = new Encounter();
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        setClass(openMrsEncounter, encounter);
        setPatientReference(healthId, encounter, systemProperties);
        setParticipant(openMrsEncounter, encounter);
        encounter.setServiceProvider(getServiceProvider(openMrsEncounter, systemProperties));
        setIdentifiers(encounter, openMrsEncounter, systemProperties);
        setType(encounter, openMrsEncounter);
        setPeriod(encounter, openMrsEncounter);
        return new FHIREncounter(encounter);
    }

    private void setPeriod(Encounter encounter, org.openmrs.Encounter openMrsEncounter) {
        Visit encounterVisit = openMrsEncounter.getVisit();
        Period visitPeriod = new Period();
        visitPeriod.setStart(encounterVisit.getStartDatetime(), TemporalPrecisionEnum.MILLI);
        visitPeriod.setEnd(encounterVisit.getStopDatetime(), TemporalPrecisionEnum.MILLI);
        encounter.setPeriod(visitPeriod);
    }

    private void setType(Encounter encounter, org.openmrs.Encounter openMrsEncounter) {
        encounter.addType().setText(openMrsEncounter.getEncounterType().getName());
    }

    private void setIdentifiers(Encounter encounter, org.openmrs.Encounter openMrsEncounter, SystemProperties systemProperties) {
        String id = new EntityReference().build(Resource.class, systemProperties, openMrsEncounter.getUuid());
        encounter.setId(id);
        encounter.addIdentifier().setValue(id);
    }

    public Reference getServiceProvider(org.openmrs.Encounter openMrsEncounter, SystemProperties systemProperties) {
        boolean isHIEFacility = omrsLocationService.isLocationHIEFacility(openMrsEncounter.getLocation());
        String serviceProviderId = null;
        serviceProviderId = isHIEFacility ? omrsLocationService.getLocationHIEIdentifier(openMrsEncounter.getLocation()) : systemProperties.getFacilityId();
        return new Reference().setReference(
                getReference(Location.class, systemProperties, serviceProviderId));
    }

    private void setClass(org.openmrs.Encounter openMrsEncounter, Encounter encounter) {
        //todo : need to be discussed for now let's map it in code like inpatient => IPD, outpatient => OPD
        String visitType = openMrsEncounter.getVisit().getVisitType().getName().toLowerCase();
        Coding coding = getClassFromVisitType(visitType);
        encounter.setClass_(coding);
    }

    private Coding getClassFromVisitType(String visitType) {
        Coding coding = new Coding();
        coding.setSystem(AMB.getSystem());
        if (visitType.equalsIgnoreCase(MRSProperties.MRS_INPATIENT_VISIT_TYPE) || visitType.equalsIgnoreCase(MRSProperties.MRS_IDP_VISIT_TYPE)) {
            coding.setCode(IMP.toCode());
            coding.setDisplay(IMP.getDisplay());
        } else if (visitType.equalsIgnoreCase(MRSProperties.MRS_EMERGENCY_VISIT_TYPE)) {
            coding.setCode(EMER.toCode());
            coding.setDisplay(EMER.getDisplay());
        } else if (visitType.equalsIgnoreCase(MRSProperties.MRS_FIELD_VISIT_TYPE)) {
            coding.setCode(FLD.toCode());
            coding.setDisplay(FLD.getDisplay());
        } else if (visitType.equalsIgnoreCase(MRSProperties.MRS_HOME_VISIT_TYPE)) {
            coding.setCode(HH.toCode());
            coding.setDisplay(HH.getDisplay());
        } else {
            coding.setCode(AMB.toCode());
            coding.setDisplay(AMB.getDisplay());
        }
        return coding;
    }

    private void setPatientReference(String healthId, Encounter encounter, SystemProperties systemProperties) {
        if (null != healthId) {
            Reference subject = new Reference()
                    .setDisplay(healthId)
                    .setReference(getReference(org.openmrs.Patient.class, systemProperties, healthId));
            encounter.setSubject(subject);
        } else {
            throw new RuntimeException("The patient has not been synced yet");
        }
    }

    private String getReference(Type openmrsType, SystemProperties systemProperties, String id) {
        return new EntityReference().build(openmrsType, systemProperties, id);
    }

    private void setParticipant(org.openmrs.Encounter openMrsEncounter, Encounter encounter) {
        final Set<EncounterProvider> encounterProviders = openMrsEncounter.getEncounterProviders();
        for (EncounterProvider encounterProvider : encounterProviders) {
            Provider provider = encounterProvider.getProvider();
            String providerUrl = providerLookupService.getProviderRegistryUrl(provider);
            if (providerUrl == null)
                continue;
            Encounter.EncounterParticipantComponent participant = encounter.addParticipant();
            participant.setIndividual(new Reference().setReference(providerUrl));
        }
    }
}
