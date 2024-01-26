package org.openmrs.module.shrclient.model;

import java.io.Serializable;

public class FacilityCatchment implements Serializable{

    int locationId;
    String catchment;

    public FacilityCatchment(int locationId, String catchment) {
        this.locationId = locationId;
        this.catchment = catchment;
    }

    public String getCatchment() {
        return catchment;
    }

    public int getLocationId() {
        return locationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FacilityCatchment that = (FacilityCatchment) o;

        if (locationId != that.locationId) return false;
        if (!catchment.equals(that.catchment)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = locationId;
        result = 31 * result + catchment.hashCode();
        return result;
    }
}
