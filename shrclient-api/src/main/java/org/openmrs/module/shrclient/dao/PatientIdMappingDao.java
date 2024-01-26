package org.openmrs.module.shrclient.dao;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.DatabaseConstants;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.PatientIdMapping;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import static org.openmrs.module.fhir.utils.DateUtil.getDateFromTimestamp;

@Component("patientIdMappingDao")
public class PatientIdMappingDao extends IdMappingDao {

    @Autowired
    public PatientIdMappingDao(Database database) {
        super(database, Logger.getLogger(PatientIdMappingDao.class));
    }

    @Override
    public String getMappingTable() {
        return DatabaseConstants.PATIENT_ID_MAPPING_TABLE;
    }

    @Override
    public PreparedStatement getInsertIdMappingStatement(Connection connection, IdMapping idMapping) throws SQLException {
        PatientIdMapping patientIdMapping = (PatientIdMapping) idMapping;
        PreparedStatement statement = connection.prepareStatement(getInsertMappingSql());
        statement.setString(1, patientIdMapping.getInternalId());
        statement.setString(2, patientIdMapping.getExternalId());
        statement.setString(3, patientIdMapping.getUri());
        statement.setTimestamp(4, patientIdMapping.getCreatedAt());
        statement.setTimestamp(5, patientIdMapping.getLastSyncTimestamp());
        statement.setTimestamp(6, patientIdMapping.getServerUpdateTimestamp());

        return statement;
    }

    @Override
    public PreparedStatement getUpdateIdMappingStatement(Connection connection, IdMapping idMapping) throws SQLException {
        PatientIdMapping patientIdMapping = (PatientIdMapping) idMapping;
        PreparedStatement statement = connection.prepareStatement(getUpdateMappingSql());
        statement.setTimestamp(1, patientIdMapping.getLastSyncTimestamp());
        statement.setTimestamp(2, patientIdMapping.getServerUpdateTimestamp());
        statement.setString(3, patientIdMapping.getInternalId());

        return statement;
    }

    @Override
    public PreparedStatement getCheckMappingExistsStatement(Connection connection, IdMapping idMapping) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(getMappingExistsQuery());
        statement.setString(1, idMapping.getInternalId());
        statement.setString(2, idMapping.getExternalId());
        return statement;
    }

    @Override
    public PatientIdMapping buildIdMapping(ResultSet resultSet) throws SQLException {
        return new PatientIdMapping(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3),
                getDateFromTimestamp(resultSet.getTimestamp(4)), new Date(resultSet.getTimestamp(5).getTime())
                , getDateFromTimestamp(resultSet.getTimestamp(6)));
    }

    @Override
    public String getFetchByExternalIdSql() {
        return String.format("select distinct map.internal_id, map.external_id, map.uri, map.created_at, map.last_sync_datetime, map.server_update_datetime " +
                "from %s map where map.external_id=?", getMappingTable());
    }

    @Override
    public String getFetchByInternalIdSql() {
        return String.format("select distinct map.internal_id, map.external_id, map.uri, map.created_at, map.last_sync_datetime, map.server_update_datetime from %s map where map.internal_id=?", getMappingTable());
    }

    @Override
    public String getFetchByHealthIdSql() {
        return String.format("select map.internal_id, map.external_id, map.uri, map.created_at, map.last_sync_datetime, map.server_update_datetime from %s map where map.uri like ?", getMappingTable());
    }


    private String getInsertMappingSql() {
        return String.format("insert into %s (internal_id, external_id, uri, created_at, last_sync_datetime, server_update_datetime) values (?,?,?,?,?,?)", getMappingTable());
    }

    private String getUpdateMappingSql() {
        return String.format("update %s set last_sync_datetime = ?, server_update_datetime = ? where internal_id = ?", getMappingTable());
    }

    private String getMappingExistsQuery() {
        return String.format("select distinct map.internal_id from %s map where map.internal_id=? and map.external_id=?", getMappingTable());
    }
}
