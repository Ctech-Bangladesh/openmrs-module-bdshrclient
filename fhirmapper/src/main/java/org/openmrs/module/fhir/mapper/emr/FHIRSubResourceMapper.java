package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.openmrs.module.fhir.MRSProperties.ENCOUNTER_UPDATE_VOID_REASON;

@Component
public class FHIRSubResourceMapper {

    private List<FHIRResourceMapper> fhirResourceMappers;

    @Autowired
    public FHIRSubResourceMapper(List<FHIRResourceMapper> fhirResourceMappers) {
        this.fhirResourceMappers = fhirResourceMappers;
    }

    public void map(Encounter openMrsEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        EmrEncounter emrEncounter = new EmrEncounter(openMrsEncounter);
        List<IResource> topLevelResources = FHIRBundleHelper.identifyTopLevelResources(shrEncounterBundle.getBundle());
        for (IResource resource : topLevelResources) {
            for (FHIRResourceMapper fhirResourceMapper : fhirResourceMappers) {
                if (fhirResourceMapper.canHandle(resource)) {
                    fhirResourceMapper.map(resource, emrEncounter, shrEncounterBundle, systemProperties);
                }
            }
        }
        voidExistingObs(emrEncounter.getEncounter());
        addNewObsAndOrderToOpenMrsEncounter(emrEncounter);
    }

    private void addNewObsAndOrderToOpenMrsEncounter(EmrEncounter emrEncounter) {
        Encounter openmrsEncounter = emrEncounter.getEncounter();
        for (Order order : emrEncounter.getOrders()) {
            openmrsEncounter.addOrder(order);
        }
        for (Obs obs : emrEncounter.getObs()) {
            openmrsEncounter.addObs(obs);
        }
    }

    private void voidExistingObs(Encounter emrEncounter) {
        Set<Obs> topLevelObs = emrEncounter.getObsAtTopLevel(false);
        voidObsAndGroupMembers(topLevelObs);
    }

    private void voidObsAndGroupMembers(Set<Obs> obs) {
        for (Obs topLevelOb : obs) {
            Set<Obs> groupMembers = topLevelOb.getGroupMembers();
            if (!topLevelOb.getVoided()) {
                topLevelOb.setVoided(true);
                topLevelOb.setVoidReason(ENCOUNTER_UPDATE_VOID_REASON);
            }
            if (CollectionUtils.isNotEmpty(groupMembers)) {
                voidObsAndGroupMembers(groupMembers);
            }
        }
    }
}
