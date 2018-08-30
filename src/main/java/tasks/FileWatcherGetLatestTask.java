package tasks;

import org.apache.log4j.Logger;
import utils.DAQSetup;
import utils.Helpers;
import utils.SetupManager;
import utils.SnapshotDirectoryWatcher;

import java.io.IOException;
import java.util.*;

public class FileWatcherGetLatestTask implements Runnable {

    private static final Logger logger = Logger.getLogger(FileWatcherGetLatestTask.class);

    private final SetupManager setupManager;

    public FileWatcherGetLatestTask(SetupManager setupManager) {
        this.setupManager = setupManager;
    }

    private Map<String, String> setupNameBySnapshotPath = new HashMap<>(20);

    private Map<String, SnapshotDirectoryWatcher> watcherByPath = new HashMap<>(20);

    private Set<String> watchedDirectories = new HashSet<>(0);

    @Override
    public void run() {
        List<DAQSetup> setups = this.setupManager.getAvailableSetups();

        boolean watchedDirectoriesChanged = false;

        Set<String> watchPaths = new HashSet<>(setups.size());
        for (DAQSetup setup : setups) {
            String setupSnapshotPath = setup.getSnapshotPath();

            watchPaths.add(setupSnapshotPath);
            if (!watchedDirectories.contains(setupSnapshotPath)) {
                watchedDirectoriesChanged = true;
            }
        }

        if (watchedDirectoriesChanged) {
            // remove outdated watchers
            for (String watchedDirectory : this.watchedDirectories) {
                if (!watchPaths.contains(watchedDirectory)) {
                    SnapshotDirectoryWatcher outdatedWatcher = this.watcherByPath.get(watchedDirectory);
                    this.watcherByPath.remove(watchedDirectory);
                    this.setupNameBySnapshotPath.remove(watchedDirectory);
                    outdatedWatcher.close();
                }
            }
            // add new watchers
            for (String watchPath : watchPaths) {
                if (!this.watchedDirectories.contains(watchPath)) {
                    try {
                        SnapshotDirectoryWatcher newWatcher = new SnapshotDirectoryWatcher(watchPath);
                        this.watcherByPath.put(watchPath, newWatcher);
                    } catch (IOException ioEx) {
                        logger.error(String.format("Problem creating new watcher for '%s'.", watchPath), ioEx);
                    }
                }
            }
            this.watchedDirectories = watchPaths;

            for (DAQSetup setup : setups) {
                String setupName = setup.getName();
                String setupSnapshotPath = setup.getSnapshotPath();

                this.setupNameBySnapshotPath.put(setupName, setupSnapshotPath);
            }
        }

        Map<String, String> latestSnapshots = new HashMap<>(setups.size());
        for (DAQSetup setup : setups) {
            String setupName = setup.getName();
            String setupSnapshotPath = setup.getSnapshotPath();

            String newSnapshotPath = this.watcherByPath.get(setupSnapshotPath).getNewSnapshotPath();
            if (newSnapshotPath == null) {
                continue;
            }
            String snapshotContent = Helpers.deserializeSnapshot(newSnapshotPath);

            logger.info(String.format("Found new snapshot for '%s': '%s'", setupName, newSnapshotPath));
            latestSnapshots.put(setupName, snapshotContent);
        }

        this.setupManager.updateLatestSnapshot(latestSnapshots);
    }

}
