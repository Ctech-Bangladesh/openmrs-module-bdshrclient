package org.openmrs.module.shrclient.service.impl;

import ca.uhn.fhir.model.dstu2.composite.PeriodDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import com.sun.syndication.feed.atom.Category;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.mapper.emr.FHIRMapper;
import org.openmrs.module.fhir.mapper.model.Confidentiality;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.advice.SHREncounterEventService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.EncounterIdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.service.*;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;
import org.openmrs.serialization.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.openmrs.module.fhir.mapper.model.Confidentiality.getConfidentiality;

@Service("hieEmrEncounterService")
public class EMREncounterServiceImpl implements EMREncounterService {

    private static final Logger logger = Logger.getLogger(EMREncounterServiceImpl.class);
    private EMRPatientService emrPatientService;
    private IdMappingRepository idMappingRepository;
    private PropertiesReader propertiesReader;
    private SystemUserService systemUserService;
    private VisitService visitService;
    private FHIRMapper fhirMapper;
    private OrderService orderService;
    private EMRPatientDeathService patientDeathService;
    private EMRPatientMergeService emrPatientMergeService;
    private VisitLookupService visitLookupService;
    private SHREncounterEventService shrEncounterEventService;
    private EncounterService encounterService;

    @Autowired
    public EMREncounterServiceImpl(@Qualifier("hieEmrPatientService") EMRPatientService emrPatientService, IdMappingRepository idMappingRepository,
                                   PropertiesReader propertiesReader, SystemUserService systemUserService,
                                   VisitService visitService, FHIRMapper fhirMapper, OrderService orderService,
                                   EMRPatientDeathService patientDeathService, EMRPatientMergeService emrPatientMergeService,
                                   VisitLookupService visitLookupService, SHREncounterEventService shrEncounterEventService, EncounterService encounterService) {
        this.emrPatientService = emrPatientService;
        this.idMappingRepository = idMappingRepository;
        this.propertiesReader = propertiesReader;
        this.systemUserService = systemUserService;
        this.visitService = visitService;
        this.fhirMapper = fhirMapper;
        this.orderService = orderService;
        this.patientDeathService = patientDeathService;
        this.emrPatientMergeService = emrPatientMergeService;
        this.visitLookupService = visitLookupService;
        this.shrEncounterEventService = shrEncounterEventService;
        this.encounterService = encounterService;
    }

    @Override
    public void createOrUpdateEncounters(Patient emrPatient, List<EncounterEvent> encounterEvents) {
        ArrayList<EncounterEvent> failedEncounters = new ArrayList<>();
        for (EncounterEvent encounterEvent : encounterEvents) {
            try {
                createOrUpdateEncounter(emrPatient, encounterEvent);
            } catch (Exception e) {
                failedEncounters.add(encounterEvent);
            }
        }
        for (EncounterEvent failedEncounterEvent : failedEncounters) {
            try {
                createOrUpdateEncounter(emrPatient, failedEncounterEvent);
            } catch (Exception e) {
                //TODO do proper handling, write to log API?
                logger.error("error Occurred while trying to process Encounter from SHR.", e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void createOrUpdateEncounter(Patient emrPatient, EncounterEvent encounterEvent) throws Exception {
        String shrEncounterId = encounterEvent.getEncounterId();
        String healthId = encounterEvent.getHealthId();
        Bundle bundle = encounterEvent.getBundle();
        logger.debug(String.format("Processing Encounter feed from SHR for patient[%s] with Encounter ID[%s]", healthId, shrEncounterId));
        EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(encounterEvent.getEncounterId(), IdMappingType.ENCOUNTER);
        mergeIfHealthIdsDonotMatch(encounterIdMapping, encounterEvent);
        if (!shouldProcessEvent(encounterEvent, encounterIdMapping)) return;
        SystemProperties systemProperties = new SystemProperties(
                propertiesReader.getFrProperties(),
                propertiesReader.getTrProperties(),
                propertiesReader.getPrProperties(),
                propertiesReader.getFacilityInstanceProperties(),
                propertiesReader.getMciProperties(),
                propertiesReader.getShrProperties());

        ShrEncounterBundle shrEncounterBundle = new ShrEncounterBundle(bundle, healthId, shrEncounterId);
        org.openmrs.Encounter newEmrEncounter = fhirMapper.map(emrPatient, shrEncounterBundle, systemProperties);

        VisitType visitType = fhirMapper.getVisitType(shrEncounterBundle);
        PeriodDt visitPeriod = fhirMapper.getVisitPeriod(shrEncounterBundle);
        Visit visit = visitLookupService.findOrInitializeVisit(emrPatient, newEmrEncounter.getEncounterDatetime(), visitType, newEmrEncounter.getLocation(), visitPeriod.getStart(), visitPeriod.getEnd());

        visit.addEncounter(newEmrEncounter);
        encounterService.saveEncounter(newEmrEncounter);
        visitService.saveVisit(visit);
        //identify location, provider(s), visit
        saveOrders(newEmrEncounter);
        Date encounterUpdatedDate = getEncounterUpdatedDate(encounterEvent);
        addEncounterToIdMapping(newEmrEncounter, shrEncounterId, healthId, systemProperties, encounterUpdatedDate);
        shrEncounterEventService.raiseShrEncounterDownloadEvent(newEmrEncounter);
        systemUserService.setOpenmrsShrSystemUserAsCreator(newEmrEncounter);
        systemUserService.setOpenmrsShrSystemUserAsCreator(newEmrEncounter.getVisit());
        savePatientDeathInfo(emrPatient);
    }

    private void addEncounterToIdMapping(Encounter newEmrEncounter, String shrEncounterId, String healthId, SystemProperties systemProperties, Date encounterUpdatedDate) {
        String internalUuid = newEmrEncounter.getUuid();
        HashMap<String, String> encounterUrlReferenceIds = new HashMap<>();
        encounterUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, healthId);
        encounterUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrEncounterId);
        String shrEncounterUrl = new EntityReference().build(Encounter.class, systemProperties, encounterUrlReferenceIds);
        EncounterIdMapping encounterIdMapping = new EncounterIdMapping(internalUuid, shrEncounterId, shrEncounterUrl, new Date(), new Date(), encounterUpdatedDate);
        idMappingRepository.saveOrUpdateIdMapping(encounterIdMapping);
    }

    private void saveOrders(Encounter newEmrEncounter) {
        List<Order> ordersList = sortOrdersOnDateActivated(newEmrEncounter);
        for (Order order : ordersList) {
            if (isNewOrder(order)) {
                orderService.saveRetrospectiveOrder(order, null);
            }
        }
    }

    private List<Order> sortOrdersOnDateActivated(Encounter newEmrEncounter) {
        List<Order> ordersList = new ArrayList<>(newEmrEncounter.getOrders());
        Collections.sort(ordersList, new Comparator<Order>() {
            @Override
            public int compare(Order o1, Order o2) {
                return o1.getDateActivated().compareTo(o2.getDateActivated());
            }
        });
        return ordersList;
    }

    private boolean isNewOrder(Order order) {
        return order.getOrderId() == null;
    }

    private boolean shouldProcessEvent(EncounterEvent encounterEvent, EncounterIdMapping encounterIdMapping) {
        if (hasUpdatedEncounterInTheFeed(encounterEvent)) return false;
        Date encounterUpdatedDate = getEncounterUpdatedDate(encounterEvent);
        if (isUpdateAlreadyProcessed(encounterIdMapping, encounterUpdatedDate)) return false;
        return getEncounterConfidentiality(encounterEvent.getBundle()).ordinal() <= Confidentiality.Normal.ordinal();
    }

    private void mergeIfHealthIdsDonotMatch(EncounterIdMapping encounterIdMapping, EncounterEvent encounterEvent) {
        if (encounterIdMapping != null && !encounterIdMapping.getHealthId().equals(encounterEvent.getHealthId())) {
            try {
                emrPatientMergeService.mergePatients(encounterEvent.getHealthId(), encounterIdMapping.getHealthId());
            } catch (SerializationException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isUpdateAlreadyProcessed(EncounterIdMapping encounterIdMapping, Date encounterUpdatedDate) {
        if (encounterIdMapping == null) return false;
        Date serverUpdateDateTime = encounterIdMapping.getServerUpdateDateTime();
        if (serverUpdateDateTime == null) return true;
        return encounterUpdatedDate.before(serverUpdateDateTime) || encounterUpdatedDate.equals(serverUpdateDateTime);
    }

    private Date getEncounterUpdatedDate(EncounterEvent encounterEvent) {
        Category encounterUpdatedCategory = encounterEvent.getEncounterUpdatedCategory();
        String encounterUpdatedDate = StringUtils.substringAfter(encounterUpdatedCategory.getTerm(), ":");
        return DateUtil.parseDate(encounterUpdatedDate);
    }

    private boolean hasUpdatedEncounterInTheFeed(EncounterEvent encounterEvent) {
        return encounterEvent.getLatestUpdateEventCategory() != null;
    }

    private Confidentiality getEncounterConfidentiality(Bundle bundle) {
        Composition composition = bundle.getAllPopulatedChildElementsOfType(Composition.class).get(0);
        String confidentialityCode = composition.getConfidentiality();
        if (null == confidentialityCode) {
            return Confidentiality.Normal;
        }
        return getConfidentiality(confidentialityCode);
    }

    private void savePatientDeathInfo(org.openmrs.Patient emrPatient) {
        if (emrPatient.isDead()) {
            emrPatient.setCauseOfDeath(patientDeathService.getCauseOfDeath(emrPatient));
            emrPatientService.savePatient(emrPatient);
            systemUserService.setOpenmrsShrSystemUserAsCreator(emrPatient);
        }
    }
}
