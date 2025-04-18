/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import java.util.ArrayList;
import org.h2.api.ErrorCode;
import org.h2.command.CommandInterface;
import org.h2.engine.Database;
import org.h2.engine.RightOwner;
import org.h2.engine.SessionLocal;
import org.h2.message.DbException;
import org.h2.schema.Schema;

/**
 * This class represents the statement
 * CREATE SCHEMA
 */
public class CreateSchema extends DefineCommand {

    private String schemaName;
    private String authorization;
    private boolean ifNotExists;
    private ArrayList<String> tableEngineParams;

    public CreateSchema(SessionLocal session) {
        super(session);
    }

    public void setIfNotExists(boolean ifNotExists) {
        this.ifNotExists = ifNotExists;
    }

    @Override
    public long update() {
        session.getUser().checkSchemaAdmin();
        Database db = getDatabase();
        RightOwner owner = db.findUserOrRole(authorization);
        if (owner == null) {
            throw DbException.get(ErrorCode.USER_OR_ROLE_NOT_FOUND_1, authorization);
        }
        if (db.findSchema(schemaName) != null) {
            if (ifNotExists) {
                return 0;
            }
            throw DbException.get(ErrorCode.SCHEMA_ALREADY_EXISTS_1, schemaName);
        }
        int id = getObjectId();
        Schema schema = new Schema(db, id, schemaName, owner, false);
        schema.setTableEngineParams(tableEngineParams);
        db.addDatabaseObject(session, schema);
        return 0;
    }

    public void setSchemaName(String name) {
        this.schemaName = name;
    }

    public void setAuthorization(String userName) {
        this.authorization = userName;
    }

    public void setTableEngineParams(ArrayList<String> tableEngineParams) {
        this.tableEngineParams = tableEngineParams;
    }

    @Override
    public int getType() {
        return CommandInterface.CREATE_SCHEMA;
    }

}
