package org.openmrs.module.fhir.mapper.model;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;

import java.util.HashSet;
import java.util.Set;

public class EmrEncounter {
    private Encounter encounter;
    private Set<Order> orders;
    private Set<Obs> obsList;

    public EmrEncounter(Encounter encounter) {
        this.encounter = encounter;
        this.orders = new HashSet<>();
        this.obsList = new HashSet<>();
    }

    public void addObs(Obs obs) {
        this.obsList.add(obs);
    }

    public void addOrder(Order order) {
        this.orders.add(order);
    }

    public Encounter getEncounter() {
        return encounter;
    }

    public Set<Order> getOrders() {
        return orders;
    }

    public Set<Obs> getObs() {
        return obsList;
    }

    public Set<Obs> getTopLevelObs() {
        Set<Obs> topLevelObs = new HashSet<>();
        for (Obs o : getObs()) {
            if (o.getObsGroup() == null) {
                topLevelObs.add(o);
            }
        }
        return topLevelObs;
    }

    public void removeObs(Obs obs) {
        this.obsList.remove(obs);
    }
}
