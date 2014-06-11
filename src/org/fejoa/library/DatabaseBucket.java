package org.fejoa.library;

import org.fejoa.library.database.IDatabaseInterface;
import org.fejoa.library.database.JGitInterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseBucket {
    private List<IDatabaseInterface> databaseList = new ArrayList<>();
    static private DatabaseBucket instance;

    private DatabaseBucket() {

    }

    static private void init() {
        instance = new DatabaseBucket();
    }

    static public IDatabaseInterface get(String path, String branch) throws IOException {
        if (instance == null)
            init();
        return instance.getPrivate(path, branch);
    }

    private IDatabaseInterface getPrivate(String path, String branch) throws IOException {
        for (IDatabaseInterface database : databaseList) {
            if (database.getPath().equals(path) && database.getBranch().equals(branch))
                return database;
        }
        // not found create one
        JGitInterface database = new JGitInterface();
        database.init(path, branch, true);

        databaseList.add(database);
        return database;
    }
}
