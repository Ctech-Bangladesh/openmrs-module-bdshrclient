package org.openmrs.module.shrclient.dao;


import org.apache.log4j.Logger;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.OpenMRSConstants;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.util.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HIDCardDao {
    private static final Logger logger = Logger.getLogger(HIDCardDao.class);
    private Database database;
    private static Integer givenNameLocalAttributeId;
    private static Integer familyNameLocalAttributeId;
    private static Integer healthIdIssuedAttributeAttributeId;
    private static Integer healthIdIdentifierTypeId;

    public HIDCardDao(Database database) {
        this.database = database;
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
                    statement.setInt(3, userId);
                    statement.setInt(4, getHealthIdIdentifierTypeId());
                    statement.setTimestamp(5, new Timestamp(from.getTime()));
                    statement.setTimestamp(6, new Timestamp(to.getTime()));
                    statement.setInt(7, getHealthIdIssuedStatusAttributeId());
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
        HealthIdCard.HIDCardAddress hidCardAddress = healthIdCard.addAddress();
        hidCardAddress.setAddress1(resultSet.getString("address1"));
        hidCardAddress.setAddress2(resultSet.getString("address2"));
        hidCardAddress.setAddress3(resultSet.getString("address3"));
        hidCardAddress.setAddress4(resultSet.getString("address4"));
        hidCardAddress.setAddress5(resultSet.getString("address5"));
        hidCardAddress.setCountyDistrict(resultSet.getString("county_district"));
        hidCardAddress.setStateProvince(resultSet.getString("state_province"));
        return healthIdCard;
    }

    private String getQueryStatement() {
        return "SELECT pn.given_name, pn.family_name, p.gender, p.birthdate, p.date_created, pi.identifier, pa.address1, pa.address2, pa.address3, pa.address4, pa.address5, pa.county_district, pa.state_province, pt1.value AS given_name_local, pt2.value AS family_name_local " +
                "FROM person_name pn, patient_identifier pi, person_address pa, person p " +
                "LEFT JOIN person_attribute pt1 ON (p.person_id = pt1.person_id AND pt1.person_attribute_type_id = ? AND pt1.voided = 0) " +
                "LEFT JOIN person_attribute pt2 ON (p.person_id = pt2.person_id AND pt2.person_attribute_type_id = ? AND pt2.voided = 0) " +
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

    private static final Integer getHealthIdIdentifierTypeId() {
        if (healthIdIdentifierTypeId == null) {
            healthIdIdentifierTypeId = Context.getPatientService().getPatientIdentifierTypeByName(OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE).getPatientIdentifierTypeId();
        }
        return healthIdIdentifierTypeId;
    }
}
