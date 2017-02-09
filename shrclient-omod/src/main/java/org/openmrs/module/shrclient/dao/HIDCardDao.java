package org.openmrs.module.shrclient.dao;


import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.fhir.OpenMRSConstants;
import org.openmrs.module.shrclient.model.AddressHierarchyEntryTranslation;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.util.AddressHelper;
import org.openmrs.module.shrclient.util.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.shrclient.model.Address.getAddressCodeForLevel;
import static org.openmrs.module.shrclient.util.AddressLevel.*;

public class HIDCardDao {
    private static final Logger logger = Logger.getLogger(HIDCardDao.class);

    private static Integer givenNameLocalAttributeId;
    private static Integer familyNameLocalAttributeId;
    private static Integer healthIdIssuedAttributeAttributeId;
    private static Integer nationalIdAttributeAttributeId;
    private static Integer healthIdIdentifierTypeId;
    private final String PAURASAVA_TO_EXCLUDE_CODE = "99";

    private Database database;
    private AddressHelper addressHelper;
    private AddressHierarchyEntryTranslationRepository entryTranslationRepository;

    public HIDCardDao(Database database, AddressHierarchyEntryTranslationRepository entryTranslationRepository) {
        this.database = database;
        this.entryTranslationRepository = entryTranslationRepository;
    }

    public List<HealthIdCard> getAllCardsByUserWithinDateRange(final int userId, final java.util.Date from, final Date to) {
        return database.executeInTransaction(new Database.TxWork<List<HealthIdCard>>() {
            @Override
            public List<HealthIdCard> execute(Connection connection) {
                List<HealthIdCard> healthIdCards = new ArrayList<>();
                try {
                    PreparedStatement statement = connection.prepareStatement(getQueryStatement());
                    statement.setInt(1, getGivenNameLocalAttributeId());
                    statement.setInt(2, getFamilyNameLocalAttributeId());
                    statement.setInt(3, getNationalIdAttributeId());
                    statement.setInt(4, userId);
                    statement.setInt(5, getHealthIdIdentifierTypeId());
                    statement.setTimestamp(6, new Timestamp(from.getTime()));
                    statement.setTimestamp(7, new Timestamp(to.getTime()));
                    statement.setInt(8, getHealthIdIssuedStatusAttributeId());
                    ResultSet resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        healthIdCards.add(createHealthIdCard(resultSet));
                    }
                } catch (SQLException e) {
                    logger.error("Error while fetching Health-Id Card details", e);
                }
                return healthIdCards;
            }
        });
    }

    private HealthIdCard createHealthIdCard(ResultSet resultSet) throws SQLException {
        HealthIdCard healthIdCard = new HealthIdCard();
        healthIdCard.setGivenName(resultSet.getString("given_name"));
        healthIdCard.setFamilyName(resultSet.getString("family_name"));
        healthIdCard.setGivenNameLocal(resultSet.getString("given_name_local"));
        healthIdCard.setFamilyNameLocal(resultSet.getString("family_name_local"));
        healthIdCard.setDob(resultSet.getDate("birthdate"));
        healthIdCard.setIssuedDate(resultSet.getDate("date_created"));
        healthIdCard.setGender(resultSet.getString("gender"));
        healthIdCard.setHid(resultSet.getString("identifier"));
        healthIdCard.setNid(resultSet.getString("national_id"));

        createAddressForHIDCard(resultSet, healthIdCard);
        return healthIdCard;
    }

    private void createAddressForHIDCard(ResultSet resultSet, HealthIdCard healthIdCard) throws SQLException {
        HealthIdCard.HIDCardAddress hidCardAddress = healthIdCard.addAddress();
        if (addressHelper == null) {
            addressHelper = new AddressHelper();
        }

        String division = resultSet.getString("state_province");
        AddressHierarchyEntry divisionEntry = addressHelper.getAddressEntry(Division, division, null);
        hidCardAddress.setStateProvince(getLocalOrEnglishName(divisionEntry));

        String district = resultSet.getString("county_district");
        AddressHierarchyEntry districtEntry = addressHelper.getAddressEntry(Zilla, district, divisionEntry);
        hidCardAddress.setCountyDistrict(getLocalOrEnglishName(districtEntry));

        String upazilla = resultSet.getString("address5");
        AddressHierarchyEntry upazillaEntry = addressHelper.getAddressEntry(Upazilla, upazilla, districtEntry);
        hidCardAddress.setAddress5(getLocalOrEnglishName(upazillaEntry));

        String paurasava = resultSet.getString("address4");
        AddressHierarchyEntry paurasavaEntry = addressHelper.getAddressEntry(Paurasava, paurasava, upazillaEntry);
        if (paurasavaEntry != null) {
            if (!PAURASAVA_TO_EXCLUDE_CODE.equals(getAddressCodeForLevel(paurasavaEntry.getUserGeneratedId(), Paurasava.getLevelNumber()))) {
                String paurasavaName = getLocalOrEnglishName(paurasavaEntry);
                if (StringUtils.isNotBlank(paurasavaName)) {
                    hidCardAddress.setAddress4(paurasavaName);
                }
            }
        }

        String unionOrWard = resultSet.getString("address3");
        AddressHierarchyEntry unionEntry = addressHelper.getAddressEntry(UnionOrWard, unionOrWard, paurasavaEntry);
        String unionName = getLocalOrEnglishName(unionEntry);
        if (StringUtils.isNotBlank(unionName)) {
            hidCardAddress.setAddress3(unionName);
        }

        String ruralWard = resultSet.getString("address2");
        AddressHierarchyEntry ruralWardEntry = addressHelper.getAddressEntry(RuralWard, ruralWard, unionEntry);
        String ruralWardName = getLocalOrEnglishName(ruralWardEntry);
        if (StringUtils.isNotBlank(ruralWardName)) {
            hidCardAddress.setAddress2(ruralWardName);
        }

        hidCardAddress.setAddress1(resultSet.getString("address1"));
    }

    private String getLocalOrEnglishName(AddressHierarchyEntry entryForAddress) {
        if (null == entryForAddress) return null;
        AddressHierarchyEntryTranslation entryTranslation = entryTranslationRepository.get(entryForAddress.getId());
        if (null != entryTranslation && StringUtils.isNotBlank(entryTranslation.getLocalName())) {
            return entryTranslation.getLocalName();
        } else {
            return entryForAddress.getName();
        }
    }

    private String getQueryStatement() {
        return "SELECT pn.given_name, pn.family_name, p.gender, p.birthdate, p.date_created, pi.identifier, pa.address1, pa.address2, pa.address3, pa.address4, pa.address5, pa.county_district, pa.state_province, pt1.value AS given_name_local, pt2.value AS family_name_local, pt3.value as national_id " +
                "FROM person_name pn, patient_identifier pi, person_address pa, person p " +
                "LEFT JOIN person_attribute pt1 ON (p.person_id = pt1.person_id AND pt1.person_attribute_type_id = ? AND pt1.voided = 0) " +
                "LEFT JOIN person_attribute pt2 ON (p.person_id = pt2.person_id AND pt2.person_attribute_type_id = ? AND pt2.voided = 0) " +
                "LEFT JOIN person_attribute pt3 ON (p.person_id = pt3.person_id AND pt3.person_attribute_type_id = ? AND pt3.voided = 0) " +
                "WHERE p.person_id=pn.person_id AND p.person_id = pa.person_id AND p.person_id = pi.patient_id " +
                "AND p.voided = 0 AND pi.voided = 0 AND pn.voided = 0 AND pa.voided = 0 " +
                "AND pn.preferred = 1 AND pa.preferred = 1 " +
                "AND p.creator = ? AND pi.identifier_type = ? " +
                "AND p.date_created >= ? AND p.date_created <= ? " +
                "AND p.person_id NOT IN " +
                "(SELECT ptt.person_id FROM person_attribute ptt WHERE ptt.value='true' AND ptt.person_attribute_type_id = ?);";
    }

    private static final Integer getGivenNameLocalAttributeId() {
        if (givenNameLocalAttributeId == null) {
            givenNameLocalAttributeId = Context.getPersonService().getPersonAttributeTypeByName(OpenMRSConstants.GIVEN_NAME_LOCAL_ATTRIBUTE_TYPE).getPersonAttributeTypeId();
        }
        return givenNameLocalAttributeId;
    }

    private static final int getFamilyNameLocalAttributeId() {
        if (familyNameLocalAttributeId == null) {
            familyNameLocalAttributeId = Context.getPersonService().getPersonAttributeTypeByName(OpenMRSConstants.FAMILY_NAME_LOCAL_ATTRIBUTE_TYPE).getPersonAttributeTypeId();
        }
        return familyNameLocalAttributeId;
    }

    private static final int getHealthIdIssuedStatusAttributeId() {
        if (healthIdIssuedAttributeAttributeId == null) {
            healthIdIssuedAttributeAttributeId = Context.getPersonService().getPersonAttributeTypeByName(OpenMRSConstants.HID_CARD_ISSUED_ATTRIBUTE_TYPE).getPersonAttributeTypeId();
        }
        return healthIdIssuedAttributeAttributeId;
    }
 private static final int getNationalIdAttributeId() {
        if (nationalIdAttributeAttributeId == null) {
            nationalIdAttributeAttributeId = Context.getPersonService().getPersonAttributeTypeByName(OpenMRSConstants.NATIONAL_ID_ATTRIBUTE_TYPE).getPersonAttributeTypeId();
        }
        return nationalIdAttributeAttributeId;
    }


    private static final Integer getHealthIdIdentifierTypeId() {
        if (healthIdIdentifierTypeId == null) {
            healthIdIdentifierTypeId = Context.getPatientService().getPatientIdentifierTypeByName(OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE).getPatientIdentifierTypeId();
        }
        return healthIdIdentifierTypeId;
    }
}
