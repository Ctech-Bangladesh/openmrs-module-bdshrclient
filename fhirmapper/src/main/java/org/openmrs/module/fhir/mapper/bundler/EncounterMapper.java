package org.openmrs.module.fhir.mapper.bundler;


import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.codesystems.V3ActCode;
import org.hl7.fhir.exceptions.FHIRException;
import org.openmrs.EncounterProvider;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.module.fhir.FHIRProperties;
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
        setClass(openMrsEncounter, encounter, systemProperties);
        setPatientReference(healthId, encounter, systemProperties);
        setParticipant(openMrsEncounter, encounter);
        encounter.setServiceProvider(getServiceProvider(openMrsEncounter, systemProperties));
        setIdentifiers(encounter, openMrsEncounter, systemProperties);
        setType(encounter, openMrsEncounter, systemProperties);
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

    private void setType(Encounter encounter, org.openmrs.Encounter openMrsEncounter, SystemProperties systemProperties) {
        CodeableConcept encounterType = encounter.addType();
        Coding coding = encounterType.addCoding();
        coding.setSystem(systemProperties.createValueSetUrlFor(MRSProperties.TR_VALUESET_FHIR_ENCOUNTER_TYPE));

        String mrsEncounterType = openMrsEncounter.getEncounterType().getName();
        String fhirEncounterType = systemProperties.getMrsToFHIREncounterTypeMap().get(mrsEncounterType);
        if (StringUtils.isEmpty(fhirEncounterType))
            fhirEncounterType = mrsEncounterType;
        coding.setCode(fhirEncounterType);
        coding.setDisplay(fhirEncounterType);

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

    private void setClass(org.openmrs.Encounter openMrsEncounter, Encounter encounter, SystemProperties systemProperties) {
        String visitType = openMrsEncounter.getVisit().getVisitType().getName();
        String encounterClass = systemProperties.getVisitTypeToEncounterClassMap().get(visitType);
        if (StringUtils.isBlank(encounterClass))
            encounterClass = FHIRProperties.DEFAULT_ENCOUNTER_CLASS;

        Coding coding = getCodingForClass(encounterClass);
        encounter.setClass_(coding);
    }

    private Coding getCodingForClass(String encounterClass) {
        Coding coding = new Coding();
        try {
            V3ActCode v3ActCode = V3ActCode.fromCode(encounterClass);
            coding.setSystem(v3ActCode.getSystem());
            coding.setDisplay(v3ActCode.getDisplay());
            coding.setCode(v3ActCode.toCode());
        } catch (FHIRException e) {
            logger.error("Unable to map encounter class for visit");
            e.printStackTrace();
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
