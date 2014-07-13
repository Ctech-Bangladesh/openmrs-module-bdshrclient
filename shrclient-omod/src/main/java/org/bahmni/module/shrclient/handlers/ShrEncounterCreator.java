package org.bahmni.module.shrclient.handlers;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.bahmni.module.shrclient.mapper.EncounterMapper;
import org.bahmni.module.shrclient.util.*;
import org.bahmni.module.shrclient.util.Constants;
import org.hl7.fhir.instance.model.*;
import org.hl7.fhir.instance.model.Encounter;
import org.hl7.fhir.instance.model.Enumeration;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openmrs.*;
import org.openmrs.ConceptMap;
import org.openmrs.api.EncounterService;
import org.openmrs.api.UserService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShrEncounterCreator implements EventWorker {

    private static final Logger log = Logger.getLogger(ShrEncounterCreator.class);
    public static final String FHIR_CONDITION_CODE_DIAGNOSIS = "diagnosis";
    public static final String TERMINOLOGY_SERVER_CONCEPT_URL = "http://192.168.33.18/openmrs/ws/rest/v1/concept/";
    public static final String FHIR_CONDITION_CATEGORY_URL = "http://hl7.org/fhir/vs/condition-category";
    public static final String FHIR_CONDITION_SEVERITY_URL = "http://hl7.org/fhir/vs/condition-severity";
    public static final String SEVERITY_MODERATE = "Moderate";
    public static final String SEVERITY_SEVERE = "Severe";
    public static final String SNOMED_VALUE_MODERATE_SEVERTY = "6736007";
    public static final String SNOMED_VALUE_SEVERE_SEVERITY = "24484000";
    public static final String DIAGNOSIS_PRESUMED = "Presumed";
    public static final String DIAGNOSIS_CONFIRMED = "Confirmed";
    public static final String MRS_SEVERITY_PRIMARY = "Primary";
    public static final String MRS_SEVERITY_SECONDARY = "Secondary";


    private EncounterService encounterService;
    private EncounterMapper encounterMapper;
    private FhirRestClient fhirRestClient;
    private Map<String, String> severityCodes = new HashMap<String, String>();
    private final Map<String,Condition.ConditionStatus> diaConditionStatus = new HashMap<String, Condition.ConditionStatus>();
    private final Map<String,String> diaConditionSeverity = new HashMap<String, String>();
    private UserService userService;


    public ShrEncounterCreator(EncounterService encounterService, EncounterMapper encounterMapper, FhirRestClient fhirRestClient, UserService userService) {
        this.encounterService = encounterService;
        this.encounterMapper = encounterMapper;
        this.fhirRestClient = fhirRestClient;
        this.userService = userService;

        severityCodes.put(SEVERITY_MODERATE, SNOMED_VALUE_MODERATE_SEVERTY);
        severityCodes.put(SEVERITY_SEVERE, SNOMED_VALUE_SEVERE_SEVERITY);

        diaConditionStatus.put(DIAGNOSIS_PRESUMED, Condition.ConditionStatus.provisional);
        diaConditionStatus.put(DIAGNOSIS_CONFIRMED, Condition.ConditionStatus.confirmed);

        diaConditionSeverity.put(MRS_SEVERITY_PRIMARY, SEVERITY_MODERATE);
        diaConditionSeverity.put(MRS_SEVERITY_SECONDARY, SEVERITY_SEVERE);

    }

    @Override
    public void process(Event event) {
        log.debug("Event: [" + event + "]");
        try {
            String uuid = getUuid(event.getContent());
            org.openmrs.Encounter openMrsEncounter = encounterService.getEncounterByUuid(uuid);
            if (openMrsEncounter == null) {
                log.debug(String.format("No OpenMRS encounter exists with uuid: [%s].", uuid));
                return;
            }

            if (!shouldSyncEncounter(openMrsEncounter)) {
                return;
            }


            Encounter encounter = encounterMapper.map(openMrsEncounter);
            log.debug("Encounter: [ " + encounter + "]");
            final List<Condition> conditionList = getConditions(openMrsEncounter, encounter);
            Composition composition = createComposition(openMrsEncounter, encounter);
            addEncounterSection(encounter, composition);
            addConditionSections(conditionList, composition);
            PersonAttribute healthIdAttribute = openMrsEncounter.getPatient().getAttribute(org.bahmni.module.shrclient.util.Constants.HEALTH_ID_ATTRIBUTE);
            if (healthIdAttribute == null) {
                return;
            }

            String healthId = healthIdAttribute.getValue();
            if (StringUtils.isBlank(healthId)) {
                return;
            }

            AtomFeed atomFeed = new AtomFeed();
            addEntriesToDocument(atomFeed, composition, encounter, conditionList);
//            fhirRestClient.post("/encounter", composition);
            fhirRestClient.post(String.format("/patients/%s/encounters", healthId), atomFeed);

        } catch (Exception e) {
            log.error("Error while processing patient sync event.", e);
            throw new RuntimeException(e);
        }
    }

    private boolean shouldSyncEncounter(org.openmrs.Encounter openMrsEncounter) {
        User changedByUser = openMrsEncounter.getChangedBy();
        if (changedByUser == null) {
            changedByUser = openMrsEncounter.getCreator();
        }
        User shrClientSystemUser = userService.getUserByUsername(Constants.SHR_CLIENT_SYSTEM_NAME);
        return !shrClientSystemUser.getId().equals(changedByUser.getId());
    }

    private void addEntriesToDocument(AtomFeed atomFeed, Composition composition, Encounter encounter, List<Condition> conditionList) {
        atomFeed.setTitle("Encounter");
        atomFeed.setUpdated(composition.getDateSimple());
        atomFeed.setId(UUID.randomUUID().toString());

        AtomEntry compositionEntry = new AtomEntry();
        compositionEntry.setResource(composition);
        compositionEntry.setId(composition.getIdentifier().getValueSimple());
        compositionEntry.setTitle("Composition");
        atomFeed.addEntry(compositionEntry);

        AtomEntry encounterEntry = new AtomEntry();
        encounterEntry.setResource(encounter);
        encounterEntry.setId(encounter.getIndication().getReferenceSimple());
        encounterEntry.setTitle("Encounter");
        atomFeed.addEntry(encounterEntry);

        for (Condition condition : conditionList) {
            AtomEntry conditionEntry = new AtomEntry();
            conditionEntry.setId(condition.getIdentifier().get(0).getValueSimple());
            conditionEntry.setTitle("diagnosis");
            conditionEntry.setResource(condition);
            atomFeed.addEntry(conditionEntry);
        }
    }

    private void addConditionSections(List<Condition> conditionList, Composition composition) {
        for (Condition condition : conditionList) {
            List<Identifier> identifiers = condition.getIdentifier();
            String conditionUuid = identifiers.get(0).getValueSimple();
            ResourceReference conditionRef = new ResourceReference();
            conditionRef.setReferenceSimple(conditionUuid);
            conditionRef.setDisplaySimple("diagnosis");
            composition.addSection().setContent(conditionRef);
        }
    }

    private void addEncounterSection(Encounter encounter, Composition composition) {
        final Composition.SectionComponent encounterSection = composition.addSection();
        encounterSection.setContent(encounter.getIndication());
    }


//+--------+--------------+----------+--------------------------+------------+-------------+--------------------+
//| obs_id | obs_group_id | class    | name                     | concept_id | value_coded | name               |
//+--------+--------------+----------+--------------------------+------------+-------------+--------------------+
//|     84 |         NULL | ConvSet  | Visit Diagnoses          |         13 |        NULL | NULL               |
//|     85 |           84 | Question | Diagnosis order          |         19 |          21 | Primary/Secondary  |
//|     86 |           84 | Question | Diagnosis Certainty      |         16 |          18 | Confirmed/Presumed |
//|     87 |           84 | Question | Coded Diagnosis          |         15 |         181 | TestWithoutRefTerm |
//|     88 |           84 | Misc     | Bahmni Initial Diagnosis |         50 |        NULL | NULL               |
//|     89 |           84 | Misc     | Bahmni Diagnosis Status  |         49 |        NULL | NULL               |
//|     90 |           84 | Misc     | Bahmni Diagnosis Revised |         51 |           2 | False              |
//|     90 |           84 | Misc     | Bahmni Diagnosis Revised |         51 |           2 | No                 |
//+--------+--------------+----------+--------------------------+------------+-------------+--------------------+


    private List<Condition> getConditions(org.openmrs.Encounter openMrsEncounter, Encounter encounter) {
        Set<Obs> allObs = openMrsEncounter.getAllObs(true);
        List<Condition> diagnoses = new ArrayList<Condition>();
        for (Obs obs : allObs) {
            if (obs.getConcept().getName().getName().equalsIgnoreCase("Visit Diagnoses")) {
                diagnoses.add(createFHIRCondition(encounter, obs));
            }
        }
        return diagnoses;
    }

    private Condition createFHIRCondition(Encounter encounter, Obs obs) {
        Condition condition = new Condition();
        condition.setEncounter(encounter.getIndication());
        condition.setSubject(encounter.getSubject());
        condition.setAsserter(getParticipant(encounter));
        condition.setCategory(getDiagnosisCategory());

        final Set<Obs> obsMembers = obs.getGroupMembers(false);
        for (Obs member : obsMembers) {
            final String memberConceptName = member.getConcept().getName().getName();
            if (memberConceptName.equalsIgnoreCase("Coded Diagnosis")) {
                condition.setCode(getDiagnosisCode(member.getValueCoded()));
            }
            else if (memberConceptName.equalsIgnoreCase("Diagnosis Certainty")) {
                condition.setStatus(getConditionStatus(member));
            }
            else if (memberConceptName.equalsIgnoreCase("Diagnosis order")) {
                condition.setSeverity(getDiagnosisSeverity(member.getValueCoded()));
            }
        }

        DateTime onsetDate = new DateTime();
        onsetDate.setValue(new DateAndTime(obs.getObsDatetime()));
        condition.setOnset(onsetDate);

        Identifier identifier = condition.addIdentifier();
        identifier.setValueSimple(obs.getUuid());
        return condition;
    }

    private Enumeration<Condition.ConditionStatus> getConditionStatus(Obs member) {
        Concept diagnosisStatus = member.getValueCoded();
        Condition.ConditionStatus status = diaConditionStatus.get(diagnosisStatus.getName().getName());
        if (status != null) {
            return new Enumeration<Condition.ConditionStatus>(status);
        } else {
            return new Enumeration<Condition.ConditionStatus>(Condition.ConditionStatus.confirmed);
        }
    }

    private CodeableConcept getDiagnosisCode(Concept obsConcept) {
        CodeableConcept diagnosisCode = new CodeableConcept();
        Coding coding = diagnosisCode.addCoding();
        //TODO to change to reference term code
        ConceptCoding refCoding = getReferenceCode(obsConcept);
        coding.setCodeSimple(refCoding.code);
        coding.setSystemSimple(refCoding.source);
        coding.setDisplaySimple(obsConcept.getName().getName());
        return diagnosisCode;
    }

    private class ConceptCoding {
        String code;
        String source;
    }

    private ConceptCoding getReferenceCode(Concept obsConcept) {
        Collection<org.openmrs.ConceptMap> conceptMappings = obsConcept.getConceptMappings();
        for (ConceptMap mapping : conceptMappings) {
            //TODO right now returning the first matching term
            ConceptCoding coding = new ConceptCoding();
            ConceptReferenceTerm conceptReferenceTerm = mapping.getConceptReferenceTerm();
            coding.code = conceptReferenceTerm.getCode();
            coding.source = conceptReferenceTerm.getConceptSource().getName();
            return coding;
        }
        ConceptCoding defaultCoding = new ConceptCoding();
        defaultCoding.code = obsConcept.getUuid();
        //TODO: put in the right URL. To be mapped
        defaultCoding.source = TERMINOLOGY_SERVER_CONCEPT_URL + obsConcept.getUuid();
        return defaultCoding;
    }

    private CodeableConcept getDiagnosisSeverity(Concept valueCoded) {
        CodeableConcept conditionSeverity = new CodeableConcept();
        Coding coding = conditionSeverity.addCoding();
        //TODO map from bahmni severity order
        String severity = diaConditionSeverity.get(valueCoded.getName().getName());
        if (severity != null) {
            coding.setDisplaySimple(severity);
            coding.setCodeSimple(severityCodes.get(severity));
        } else {
            coding.setDisplaySimple(SEVERITY_MODERATE);
            coding.setCodeSimple(severityCodes.get(SEVERITY_MODERATE));
        }
        coding.setSystemSimple(FHIR_CONDITION_SEVERITY_URL);
        return conditionSeverity;
    }

    private CodeableConcept getDiagnosisCategory() {
        CodeableConcept conditionCategory = new CodeableConcept();
        Coding coding = conditionCategory.addCoding();
        coding.setCodeSimple(FHIR_CONDITION_CODE_DIAGNOSIS);
        coding.setSystemSimple(FHIR_CONDITION_CATEGORY_URL);
        coding.setDisplaySimple("diagnosis");
        return conditionCategory;
    }

    private ResourceReference getParticipant(Encounter encounter) {
        List<Encounter.EncounterParticipantComponent> participants = encounter.getParticipant();
        if ((participants != null) || participants.isEmpty()) {
            return participants.get(0).getIndividual();
        }
        return null;
    }

    private Composition createComposition(org.openmrs.Encounter openMrsEncounter, Encounter encounter) {
        DateAndTime encounterDateTime = new DateAndTime(openMrsEncounter.getEncounterDatetime());
        Composition composition = new Composition().setDateSimple(encounterDateTime);
        composition.setEncounter(encounter.getIndication());
        composition.setStatus(new Enumeration<Composition.CompositionStatus>(Composition.CompositionStatus.final_));
        composition.setIdentifier(new Identifier().setValueSimple("Encounter - " + openMrsEncounter.getUuid()));
        return composition;
    }

    String getUuid(String content) {
        String patientUuid = null;
        Pattern p = Pattern.compile("^\\/openmrs\\/ws\\/rest\\/v1\\/encounter\\/(.*)\\?v=.*");
        Matcher m = p.matcher(content);
        if (m.matches()) {
            patientUuid = m.group(1);
        }
        return patientUuid;
    }

    @Override
    public void cleanUp(Event event) {
    }
}
