package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.AtomFeed;
import org.hl7.fhir.instance.model.DiagnosticReport;
import org.hl7.fhir.instance.model.Observation;
import org.hl7.fhir.instance.model.Resource;
import org.hl7.fhir.instance.model.ResourceReference;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.utils.FHIRFeedHelper;
import org.openmrs.module.fhir.utils.OMRSHelper;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_CONCEPT_CLASS_LAB_SET;
import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_CONCEPT_NAME_LAB_NOTES;

@Component
public class FHIRDiagnosticReportMapper implements FHIRResource {
    @Autowired
    private OMRSHelper omrsHelper;
    @Autowired
    private FHIRObservationsMapper observationsMapper;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private IdMappingsRepository idMappingsRepository;

    @Override
    public boolean canHandle(Resource resource) {
        return resource instanceof DiagnosticReport;
    }

    @Override
    public void map(AtomFeed feed, Resource resource, Patient emrPatient, Encounter newEmrEncounter, Map<String, List<String>> processedList) {
        DiagnosticReport diagnosticReport = (DiagnosticReport) resource;
        if (processedList.containsKey(diagnosticReport.getIdentifier().getValueSimple()))
            return;
        Concept concept = omrsHelper.findConcept(diagnosticReport.getName().getCoding());
        Order order = getOrder(diagnosticReport, concept);
        if (order == null) {
            return;
        }
        Obs topLevelObs = buildObs(concept, order);

        Obs secondLevelObs = buildObs(concept, order);
        topLevelObs.addGroupMember(secondLevelObs);

        Obs resultObs = buildResultObs(feed, newEmrEncounter, processedList, diagnosticReport, concept);
        resultObs.setOrder(order);
        secondLevelObs.addGroupMember(resultObs);

        Obs notesObs = addNotes(diagnosticReport, order);
        secondLevelObs.addGroupMember(notesObs);

        if (order.getConcept().getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET)) {
            Obs panelObs = findObsByOrder(newEmrEncounter, order);
            if (panelObs == null) {
                panelObs = buildObs(order.getConcept(), order);
            }
            panelObs.addGroupMember(topLevelObs);
            newEmrEncounter.addObs(panelObs);
        }
        newEmrEncounter.addObs(topLevelObs);
        processedList.put(diagnosticReport.getIdentifier().getValueSimple(), Arrays.asList(topLevelObs.getUuid()));
    }

    private Order getOrder(DiagnosticReport diagnosticReport, Concept concept) {
        List<ResourceReference> requestDetail = diagnosticReport.getRequestDetail();
        for (ResourceReference reference : requestDetail) {
            String encounterUrl = reference.getReferenceSimple();
            Pattern p = Pattern.compile("patients\\/(.*)\\/encounters\\/(.*)");
            Matcher matcher = p.matcher(encounterUrl);
            if (matcher.matches()) {
                String shrEncounterId = matcher.group(2);
                if (!shrEncounterId.isEmpty()) {
                    IdMapping orderEncounterIdMapping = idMappingsRepository.findByExternalId(shrEncounterId);
                    Encounter orderEncounter = encounterService.getEncounterByUuid(orderEncounterIdMapping.getInternalId());
                    return findOrderFromEncounter(orderEncounter.getOrders(), concept);
                }
            }
        }
        return null;
    }

    private Order findOrderFromEncounter(Set<Order> orders, Concept concept) {
        for (Order order : orders) {
            Concept orderConcept = order.getConcept();
            if (orderConcept.equals(concept)) {
                return order;
            } else if (orderConcept.getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET)) {
                for (Concept setMember : orderConcept.getSetMembers()) {
                    if (setMember.equals(concept)) {
                        return order;
                    }
                }
            }
        }
        return null;
    }

    private Obs addNotes(DiagnosticReport diagnosticReport, Order order) {
        String conclusion = diagnosticReport.getConclusionSimple();
        if (StringUtils.isNotBlank(conclusion)) {
            Concept labNotesConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_LAB_NOTES);
            Obs notesObs = buildObs(labNotesConcept, order);
            notesObs.setValueText(conclusion);
            return notesObs;
        }
        return null;
    }

    private Obs buildResultObs(AtomFeed feed, Encounter newEmrEncounter, Map<String, List<String>> processedList, DiagnosticReport diagnosticReport, Concept concept) {
        Observation observationResource = (Observation) FHIRFeedHelper.findResourceByReference(feed, diagnosticReport.getResult().get(0));
        if (processedList.containsKey(observationResource.getIdentifier().getValueSimple())) {
            List<String> uuids = processedList.get(observationResource.getIdentifier().getValueSimple());
            for (String uuid : uuids) {
                Obs obs = findObsByUUid(newEmrEncounter, uuid);
                if (obs.getConcept().equals(concept)) {
                    return obs;
                }
            }
        }
        return observationsMapper.mapObs(feed, newEmrEncounter, observationResource, processedList);
    }

    private Obs findObsByUUid(Encounter newEmrEncounter, String obsUuid) {
        for (Obs obs : newEmrEncounter.getAllObs()) {
            if (obs.getUuid().equals(obsUuid))
                return obs;
        }
        return null;
    }

    private Obs findObsByOrder(Encounter encounter, Order order) {
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (obs.getOrder().equals(order) && obs.getConcept().equals(order.getConcept())) {
                return obs;
            }
        }
        return null;
    }

    //TODO : Add order
    private Obs buildObs(Concept concept, Order order) {
        Obs obs = new Obs();
        obs.setConcept(concept);
        obs.setOrder(order);
        return obs;
    }
}
