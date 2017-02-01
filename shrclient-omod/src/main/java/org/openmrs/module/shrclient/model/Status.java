package org.openmrs.module.shrclient.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openmrs.module.fhir.utils.DateUtil;

import java.util.Date;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status {
    @JsonProperty("type")
    private Character type;

    @JsonProperty("date_of_death")
    private String dateOfDeath;

    public Character getType() {
        return type;
    }

    public void setType(Character type) {
        this.type = type;
    }

    public Date getDateOfDeath() {
        return dateOfDeath == null ? null : DateUtil.parseDate(dateOfDeath);
    }

    public void setDateOfDeath(Date dateOfDeath) {
        this.dateOfDeath = dateOfDeath == null ? null : DateUtil.toDateString(dateOfDeath, DateUtil.ISO_8601_DATE_IN_SECS_FORMAT2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Status)) return false;

        Status that = (Status) o;

        if (dateOfDeath == null) {
            if (that.dateOfDeath != null) return false;
        } else {
            if (!DateUtil.isEqualTo(getDateOfDeath(), that.getDateOfDeath())) return false;
        }
        if (type != null ? !type.equals(that.type) : that.type != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (dateOfDeath != null ? dateOfDeath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Status{");
        sb.append("status=").append(type);
        sb.append(", dateOfDeath='").append(dateOfDeath).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
