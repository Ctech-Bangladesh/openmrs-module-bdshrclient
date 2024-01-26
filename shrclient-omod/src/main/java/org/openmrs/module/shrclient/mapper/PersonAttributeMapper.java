package org.openmrs.module.shrclient.mapper;


import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;

public class PersonAttributeMapper {

    private PersonService personService;

    public PersonAttributeMapper(PersonService personService) {
        this.personService = personService;
    }

    public PersonAttributeMapper(){

    }

    public String getAttributeValue(org.openmrs.Patient openMrsPatient, String attributeName) {
        PersonAttribute attribute = openMrsPatient.getAttribute(attributeName);
        return attribute != null ? attribute.getValue() : null;
    }

    public PersonAttribute getAttribute(String attributeTypeName, String attributeValue){
        PersonAttribute attribute = new PersonAttribute();
        PersonAttributeType attributeType = getPersonService().getPersonAttributeTypeByName(attributeTypeName);
        if (attributeType != null) {
            attribute.setAttributeType(attributeType);
            attribute.setValue(attributeValue);
            return attribute;
        } else {
            return null;
        }
    }

    private PersonService getPersonService() {
        if(personService == null){
            personService = Context.getPersonService();
        }
        return personService;
    }
}
