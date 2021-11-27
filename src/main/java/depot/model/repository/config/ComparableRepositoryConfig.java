package depot.model.repository.config;

import depot.model.repository.path.RepositoryPathNode;
import depot.model.repository.sync.RepositoryCompareEntry;
import depot.model.repository.sync.RepositoryCompareResult;

import java.util.HashMap;

public interface ComparableRepositoryConfig extends BaseRepositoryConfig {

    HashMap<RepositoryPathNode, RepositoryCompareEntry.EntryInfo> collectEntryInfo(
            RepositoryPathNode pathNode, RepositoryCompareResult.CollectListener collectListener) throws Exception;
}
