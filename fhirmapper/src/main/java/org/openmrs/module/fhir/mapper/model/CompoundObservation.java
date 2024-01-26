package org.openmrs.module.fhir.mapper.model;

import org.openmrs.Concept;
import org.openmrs.Obs;

import java.util.ArrayList;
import java.util.List;

public class CompoundObservation {
    private Obs observation;

    public CompoundObservation(Obs observation) {
        this.observation = observation;
    }

    public boolean isOfType(ObservationType type) {
        return observation.getConcept().getName().getName().equalsIgnoreCase(type.getDisplayName());
    }

    public Obs getMemberObsForConceptName(String conceptName) {
        for (Obs groupMember : this.observation.getGroupMembers()) {
            if (conceptName.equals(groupMember.getConcept().getName().getName())) {
                return groupMember;
            }
        }
        return null;
    }

    public Obs getMemberObsForConcept(Concept concept) {
        for (Obs groupMember : this.observation.getGroupMembers()) {
            if (concept.equals(groupMember.getConcept())) {
                return groupMember;
            }
        }
        return null;
    }

    public List<Obs> getAllMemberObsForConcept(Concept concept) {
        List<Obs> memberObsList = new ArrayList<>();
        for (Obs groupMember : this.observation.getGroupMembers()) {
            if (concept.equals(groupMember.getConcept())) {
                memberObsList.add(groupMember);
            }
        }
        return memberObsList;
    }


    public List<Obs> getAllMemberObsForConceptName(String conceptName) {
        List<Obs> memberObsList = new ArrayList<>();
        for (Obs groupMember : this.observation.getGroupMembers()) {
            if (conceptName.equals(groupMember.getConcept().getName().getName())) {
                memberObsList.add(groupMember);
            }
        }
        return memberObsList;
    }

    public String getUuid() {
        return observation.getUuid();
    }

    public Obs getRawObservation() {
        return observation;
    }
}
