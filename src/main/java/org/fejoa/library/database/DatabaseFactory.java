package org.fejoa.library.database;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DatabaseFactory {
    static private Map<String, IDatabaseInterface> databaseMap = new HashMap<>();

    static public IDatabaseInterface getDatabaseFor(String path, String branch) throws IOException {
        String hash = branch + "@" + path;
        if (databaseMap.containsKey(hash))
            return databaseMap.get(hash);
        IDatabaseInterface databaseInterface = new JGitInterface();
        databaseInterface.init(path, branch, true);
        databaseMap.put(hash, databaseInterface);
        return databaseInterface;
    }
}
