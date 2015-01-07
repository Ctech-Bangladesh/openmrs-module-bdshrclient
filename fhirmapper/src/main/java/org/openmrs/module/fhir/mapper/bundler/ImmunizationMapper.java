package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.instance.model.Boolean;
import org.hl7.fhir.instance.model.*;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.ObservationType;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_CONCEPT_VACCINATION_REFUSED;
import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_CONCEPT_VACCINE;
import static org.openmrs.module.fhir.utils.FHIRFeedHelper.addFHIRCoding;

@Component
public class ImmunizationMapper implements EmrObsResourceHandler {

    @Autowired
    private IdMappingsRepository idMappingsRepository;
    @Autowired
    private ConceptService conceptService;

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation obs = new CompoundObservation(observation);
        return obs.isOfType(ObservationType.IMMUNIZATION);
    }

    @Override
    public List<FHIRResource> map(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> resources = new ArrayList<>();

        Resource resource = mapObservation(obs, fhirEncounter);

        FHIRResource immunizationResource = new FHIRResource(null, null, resource);
        resources.add(immunizationResource);

        return resources;
    }

    private Resource mapObservation(Obs obs, Encounter fhirEncounter) {
        Immunization immunization = new Immunization();
        immunization.setSubject(fhirEncounter.getSubject());
        Set<Obs> groupMembers = obs.getGroupMembers();

        Obs vaccineObs = getObsForConcept(MRS_CONCEPT_VACCINE, groupMembers);
        List<Drug> drugs = conceptService.getDrugsByConcept(vaccineObs.getValueCoded());
        if (CollectionUtils.isEmpty(drugs)) {
            return null;
        }
        immunization.setVaccineType(getVaccineType(drugs));
        immunization.setRefusedIndicator(getRefusedIndicator(groupMembers));

        return immunization;
    }

    private CodeableConcept getVaccineType(List<Drug> drugs) {
        Drug drugsByConcept = drugs.get(0);
        IdMapping idMapping = idMappingsRepository.findByInternalId(drugsByConcept.getUuid());
        CodeableConcept codeableConcept = new CodeableConcept();
        if (idMapping != null) {
            addFHIRCoding(codeableConcept, idMapping.getExternalId(), idMapping.getUri(), drugsByConcept.getDisplayName());
        } else {
            Coding coding = codeableConcept.addCoding();
            coding.setDisplaySimple(drugsByConcept.getDisplayName());
        }
        return codeableConcept;
    }

    private Boolean getRefusedIndicator(Set<Obs> groupMembers) {
        Obs vaccinationRefused = getObsForConcept(MRS_CONCEPT_VACCINATION_REFUSED, groupMembers);
        Boolean refusedIndicator = new Boolean();
        refusedIndicator.setValue(vaccinationRefused.getValueAsBoolean());
        return refusedIndicator;
    }

    private Obs getObsForConcept(String conceptName, Set<Obs> groupMembers) {
        for (Obs groupMember : groupMembers) {
            if (conceptName.equals(groupMember.getConcept().getName().getName())) {
                return groupMember;
            }
        }

        return null;
    }
}
