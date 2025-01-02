package org.openmrs.module.shrclient.util;

import java.sql.ResultSet;
import org.apache.log4j.Logger;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_SHR_SYSTEM_USER_TAG;

@Component
public class SystemUserService {
    private UserService userService;
    private Database database;
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    private Logger logger = Logger.getLogger(SystemUserService.class);

    public SystemUserService(UserService userService, Database database, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.userService = userService;
        this.database = database;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    @Autowired
    public SystemUserService(Database database, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.database = database;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    public User getOpenMRSShrSystemUser() {
        Integer shrSystemUserId = Integer.parseInt(globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_SYSTEM_USER_TAG));
        return getUserService().getUser(shrSystemUserId);
    }

    public UserService getUserService() {
        if (userService == null) {
            userService = Context.getUserService();
        }
        return userService;
    }

    public boolean isUpdatedByOpenMRSShrSystemUser(BaseOpenmrsData openMrsEntity) {
        User changedByUser = openMrsEntity.getChangedBy();
        if (changedByUser == null) {
            changedByUser = openMrsEntity.getCreator();
        }
        return isOpenMRSSystemUser(changedByUser);
    }

    public boolean isOpenMRSSystemUser(User changedByUser) {
        User openMrsShrSystemUser = getOpenMRSShrSystemUser();
        return openMrsShrSystemUser.getId().equals(changedByUser.getId());
    }

    public void setOpenmrsShrSystemUserAsCreator(Encounter encounter) {
        updateUser("encounter", "encounter_id", encounter);
    }

    public void setOpenmrsShrSystemUserAsCreator(Visit visit) {
        updateUser("visit", "visit_id", visit);
    }

    public void setOpenmrsShrSystemUserAsCreator(Patient patient) {
        updateUser("patient", "patient_id", patient);
    }

    private void updateUser(final String tableName, final String idColumnName, final BaseOpenmrsData openMrsEntity) {
        database.executeInTransaction(new Database.TxWork<Object>() {
            @Override
            public Object execute(Connection connection) {
                PreparedStatement statement = null;
                String userColumnName = null;
                if (openMrsEntity.getChangedBy() != null) {
                    userColumnName = "changed_by";
                } else {
                    userColumnName = "creator";
                }
                String query = "update " + tableName + " set " + userColumnName + " = ? where " + idColumnName + " = ?;";
                try {
                    statement = connection.prepareStatement(query);
                    statement.setInt(1, getOpenMRSShrSystemUser().getUserId());
                    statement.setInt(2, openMrsEntity.getId());
                    statement.executeUpdate();
                } catch (Exception e) {
                    throw new RuntimeException("Error occurred while updating " + tableName, e);
                } finally {
                    try {
                        if (statement != null) statement.close();
                    } catch (SQLException e) {
                        logger.warn("Could not close db statement or resultset", e);
                    }
                }
                return null;
            }
        });
    }

    public HealthIdCard getPatientHIDByPatientId(final String patientId) {
        return database.executeInTransaction(connection -> {
            HealthIdCard healthIdCard = new HealthIdCard();
//                List<HealthIdCard> healthIdCards = new ArrayList<>();
            try {
                String query = getHIDQuery();
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, patientId);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    healthIdCard.setHid(resultSet.getString(1));
                }
            } catch (SQLException e) {
                logger.error(String.format("Error while fetching Health-Id Card details for person %s", patientId));
            }
            if (healthIdCard.getHid() == null) {
                return null;
            }
            return healthIdCard;
        });
    }

    private String getHIDQuery(){
        return "SELECT identifier from patient_identifier pi"
            + " inner join patient_identifier_type pt on pt.patient_identifier_type_id = pi.identifier_type"
            + " where pt.name='Health Id' and patient_id=?";
    }
}
