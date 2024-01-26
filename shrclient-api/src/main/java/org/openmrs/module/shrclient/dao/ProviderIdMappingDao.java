package org.openmrs.module.shrclient.dao;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.DatabaseConstants;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.ProviderIdMapping;
import org.openmrs.module.shrclient.util.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component("ProviderIdMappingDao")
public class ProviderIdMappingDao extends IdMappingDao {
    @Autowired
    public ProviderIdMappingDao(Database database) {
        super(database, Logger.getLogger(ProviderIdMappingDao.class));
    }

    @Override
    public String getMappingTable() {
        return DatabaseConstants.PROVIDER_ID_MAPPING_TABLE;
    }

    @Override
    public PreparedStatement getInsertIdMappingStatement(Connection connection, IdMapping idMapping) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(getInsertMappingSql());
        statement.setString(1, idMapping.getInternalId());
        statement.setString(2, idMapping.getExternalId());
        statement.setString(3, idMapping.getUri());
        statement.setTimestamp(4, idMapping.getCreatedAt());

        return statement;
    }

    @Override
    public PreparedStatement getUpdateIdMappingStatement(Connection connection, IdMapping idMapping) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(getUpdateMappingSql());
        statement.setString(1, idMapping.getInternalId());
        statement.setString(2, idMapping.getExternalId());
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
    public ProviderIdMapping buildIdMapping(ResultSet resultSet) throws SQLException {
        return new ProviderIdMapping(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3));
    }

    @Override
    public String getFetchByExternalIdSql() {
        return String.format("select distinct map.internal_id, map.external_id, map.uri, map.created_at " +
                "from %s map where map.external_id=?", getMappingTable());
    }

    @Override
    public String getFetchByInternalIdSql() {
        return String.format("select distinct map.internal_id, map.external_id, map.uri, map.created_at from %s map where map.internal_id=?", getMappingTable());
    }

    @Override
    public String getFetchByHealthIdSql() {
        return String.format("select map.internal_id, map.external_id, map.uri, map.created_at from %s map where map.uri like ?", getMappingTable());
    }

    private String getInsertMappingSql() {
        return String.format("insert into %s (internal_id, external_id, uri, created_at) values (?,?,?,?)", getMappingTable());
    }

    private String getUpdateMappingSql() {
        return String.format("update %s set internal_id = ? where external_id = ?", getMappingTable());
    }

    private String getMappingExistsQuery() {
        return String.format("select distinct map.internal_id from %s map where map.internal_id=? and map.external_id=?", getMappingTable());
    }
}