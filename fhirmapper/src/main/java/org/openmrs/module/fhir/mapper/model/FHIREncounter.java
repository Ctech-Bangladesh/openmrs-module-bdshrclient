package org.openmrs.module.fhir.mapper.model;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Reference;

import java.util.ArrayList;
import java.util.List;

public class FHIREncounter {
    private Encounter encounter;

    public FHIREncounter(Encounter encounter) {
        this.encounter = encounter;
    }

    public Encounter getEncounter() {
        return encounter;
    }

    public List<Reference> getParticipantReferences() {
        List<Reference> participants = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(encounter.getParticipant())) {
            for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
                participants.add(participant.getIndividual());
            }
        }
        return participants;
    }

    public Reference getFirstParticipantReference() {
        return CollectionUtils.isNotEmpty(encounter.getParticipant()) ? encounter.getParticipantFirstRep().getIndividual() : null;
    }

    public String getId() {
        return encounter.getId();
    }

    public Reference getPatient() {
        return encounter.getSubject();
    }

    public Reference getServiceProvider() {
        return encounter.getServiceProvider();
    }

    public List<Identifier> getIdentifier() {
        return encounter.getIdentifier();
    }
}
