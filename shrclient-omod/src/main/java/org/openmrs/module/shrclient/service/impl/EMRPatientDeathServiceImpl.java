package org.openmrs.module.shrclient.service.impl;

import org.apache.log4j.Logger;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.Status;
import org.openmrs.module.shrclient.service.EMRPatientDeathService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;

@Service
public class EMRPatientDeathServiceImpl implements EMRPatientDeathService {

    private static final Logger logger = Logger.getLogger(EMRPatientDeathServiceImpl.class);

    private ObsService obsService;
    private ConceptService conceptService;
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Autowired
    public EMRPatientDeathServiceImpl(ObsService obsService, ConceptService conceptService, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.obsService = obsService;
        this.conceptService = conceptService;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    @Override
    public Concept getCauseOfDeath(org.openmrs.Patient emrPatient) {
        String causeOfDeathConceptId = globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH);
        Concept unspecifiedCauseOfDeathConcept = causeOfDeathConceptId != null ? conceptService.getConcept(Integer.parseInt(causeOfDeathConceptId)) : conceptService.getConceptByName(TR_CONCEPT_CAUSE_OF_DEATH);
        String unspecifiedCauseOfDeathConceptId = globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_CONCEPT_CAUSE_OF_DEATH);
        Concept causeOfDeathConcept = unspecifiedCauseOfDeathConceptId != null ? conceptService.getConcept(Integer.parseInt(unspecifiedCauseOfDeathConceptId)) : conceptService.getConceptByName(TR_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH);
        String error = null;
        if (emrPatient.getDead() && unspecifiedCauseOfDeathConcept == null) {
            error = String.format("Global Property %s is not set & Concept with name %s is not found", GLOBAL_PROPERTY_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH, TR_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH);
            logger.error(error);
            throw new RuntimeException(error);
        }
        if (emrPatient.getDead() && causeOfDeathConcept == null) {
            error = String.format("Global Property %s is not set & Concept with name %s is not found", GLOBAL_PROPERTY_CONCEPT_CAUSE_OF_DEATH, TR_CONCEPT_CAUSE_OF_DEATH);
            logger.error(error);
            throw new RuntimeException(error);
        }
        if (emrPatient.getDead() && emrPatient.getCauseOfDeath() != null && emrPatient.getCauseOfDeath() != unspecifiedCauseOfDeathConcept) {
            return emrPatient.getCauseOfDeath();
        }
        Concept causeOfDeath = unspecifiedCauseOfDeathConcept;
        if (emrPatient.getId() != null) {
            List<Obs> obsForCauseOfDeath = obsService.getObservationsByPersonAndConcept(emrPatient, causeOfDeathConcept);
            if ((obsForCauseOfDeath != null) && !obsForCauseOfDeath.isEmpty()) {
                for (Obs obs : obsForCauseOfDeath) {
                    if (!obs.getVoided()) {
                        causeOfDeath = obs.getValueCoded();
                    }
                }
            }
        }
        return causeOfDeath;
    }
}
