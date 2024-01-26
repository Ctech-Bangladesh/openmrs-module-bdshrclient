package org.openmrs.module.shrclient.service.impl;

import org.openmrs.*;
import org.openmrs.api.OrderService;
import org.openmrs.api.PersonService;
import org.openmrs.api.VisitService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.service.EMRPatientMergeService;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.serialization.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service("hieEmrPatientMergeService")
public class EMRPatientMergeServiceImpl implements EMRPatientMergeService {

    private OrderService orderService;
    private VisitService visitService;
    private EMRPatientService emrPatientService;
    private PersonService personService;
    private IdMappingRepository idMappingRepository;

    @Autowired
    public EMRPatientMergeServiceImpl(PersonService personService,
                                      OrderService orderService,
                                      EMRPatientService emrPatientService, IdMappingRepository idMappingRepository, VisitService visitService) {
        this.orderService = orderService;
        this.personService = personService;
        this.emrPatientService = emrPatientService;
        this.idMappingRepository = idMappingRepository;
        this.visitService = visitService;
    }


    @Override
    public List<String> mergePatients(String toBeRetainedHealthId, List<String> toBeRetiredHealthIds) {
        List<String> mergedHealthIds = new ArrayList<>();
        for (String toBeRetiredHealthId : toBeRetiredHealthIds) {
            try {
                Patient toBeRetainedPatient = emrPatientService.getEMRPatientByHealthId(toBeRetainedHealthId);
                Patient toBeRetiredPatient = emrPatientService.getEMRPatientByHealthId(toBeRetiredHealthId);

                if (toBeRetiredPatient == null || toBeRetiredPatient.getVoided()) continue;

                merge(toBeRetainedHealthId, toBeRetiredHealthId, toBeRetainedPatient, toBeRetiredPatient);
                mergedHealthIds.add(toBeRetiredHealthId);
            } catch (SerializationException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return mergedHealthIds;
    }

    @Override
    public void mergePatients(String toBeRetainedHealthId, String toBeRetiredHealthId) throws SerializationException {
        Patient toBeRetainedPatient = emrPatientService.getEMRPatientByHealthId(toBeRetainedHealthId);
        Patient toBeRetiredPatient = emrPatientService.getEMRPatientByHealthId(toBeRetiredHealthId);

        if(toBeRetiredPatient == null) return;

        merge(toBeRetainedHealthId, toBeRetiredHealthId, toBeRetainedPatient, toBeRetiredPatient);
    }

    private void merge(String toBeRetainedHealthId, String toBeRetiredHealthId, Patient toBeRetainedPatient, Patient toBeRetiredPatient) throws SerializationException {
        retainOneActiveVisit(toBeRetiredPatient, toBeRetainedPatient);
        voidAttributes(toBeRetiredPatient);
        voidIdentifiers(toBeRetiredPatient);
        List<Order> ordersOfRetiredPatientVoidedOnMerge = voidUnvoidedOrders(toBeRetiredPatient);

        emrPatientService.mergePatients(toBeRetainedPatient, toBeRetiredPatient);
        idMappingRepository.replaceHealthId(toBeRetiredHealthId, toBeRetainedHealthId);

        String voidReason = String.format("Merged from patient #%s", toBeRetiredPatient.getId());
        unVoidRequiredOrders(ordersOfRetiredPatientVoidedOnMerge);
        voidUnpreferredNames(toBeRetainedPatient.getNames(), voidReason);
        voidUnpreferredAddress(toBeRetainedPatient.getAddresses(), voidReason);
        personService.savePerson(toBeRetainedPatient.getPerson());
    }

    private void voidUnpreferredAddress(Set<PersonAddress> addresses, String voidReason) {
        for (PersonAddress address : addresses) {
            if (!address.getPreferred()) {
                address.setVoided(true);
                address.setVoidReason(voidReason);
            }
        }
    }

    private void voidUnpreferredNames(Set<PersonName> names, String voidReason) {
        for (PersonName name : names) {
            if (!name.getPreferred())
                name.setVoided(true);
            name.setVoidReason(voidReason);
        }
    }


    private List<Order> voidUnvoidedOrders(Patient patient) {
        List<Order> voidedOrders = new ArrayList<>();
        List<Order> orders = orderService.getAllOrdersByPatient(patient);
        for (Order order : orders) {
            if (!order.getVoided()) {
                order.setVoided(true);
                voidedOrders.add(order);

            }
        }
        return voidedOrders;
    }

    private void unVoidRequiredOrders(List<Order> voidedOrdersList) {
        for (Order order : voidedOrdersList) {
            order.setVoided(false);
        }

    }

    private void voidIdentifiers(Patient patient) {
        Set<PatientIdentifier> identifiers = patient.getIdentifiers();
        for (PatientIdentifier identifier : identifiers) {
            identifier.setVoided(true);
        }
    }

    private void voidAttributes(Patient patient) {
        Set<PersonAttribute> attributes = patient.getAttributes();
        for (PersonAttribute attribute : new HashSet<>(attributes)) {
            attribute.setVoided(true);
        }
    }

    private void retainOneActiveVisit(Patient patient, Patient toBeRetainedPatient) {
        List<Visit> unVoidedActiveVisitsOfRetainedPatient = visitService.getActiveVisitsByPatient(toBeRetainedPatient);
        if (unVoidedActiveVisitsOfRetainedPatient.size() > 0) {
            Date stopTime = new Date();
            List<Visit> activeVisitsOfRetiredPatient = visitService.getActiveVisitsByPatient(patient);
            for (Visit visit : activeVisitsOfRetiredPatient) {
                visit.setStopDatetime(stopTime);
            }
        }
    }
}

