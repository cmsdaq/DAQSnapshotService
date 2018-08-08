package utils;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class SnapshotDirectoryWatcher {

    private static final Logger logger = Logger.getLogger(SnapshotDirectoryWatcher.class);

    private final String snapshotRootDirectory;

    private WatchService yearWatcher;
    private WatchKey yearWatchKey;
    private Path yearDirectory;

    private WatchService monthWatcher;
    private WatchKey monthWatchKey;
    private Path monthDirectory;

    private WatchService dayWatcher;
    private WatchKey dayWatchKey;
    private Path dayDirectory;

    private WatchService hourWatcher;
    private WatchKey hourWatchKey;
    private Path hourDirectory;

    private WatchService snapshotWatcher;
    private WatchKey snapshotWatchKey;
    private Path snapshotDirectory;

    public SnapshotDirectoryWatcher(String snapshotRootDirectory) throws IOException {
        this.snapshotRootDirectory = snapshotRootDirectory;

        this.yearWatcher = FileSystems.getDefault().newWatchService();
        this.monthWatcher = FileSystems.getDefault().newWatchService();
        this.dayWatcher = FileSystems.getDefault().newWatchService();
        this.hourWatcher = FileSystems.getDefault().newWatchService();
        this.snapshotWatcher = FileSystems.getDefault().newWatchService();
    }

    public String getNewSnapshotPath() {
        String newSnapshotPath = null;

        if (this.yearDirectory != null) {
            WatchKey yearKey = this.yearWatcher.poll();
            if (yearKey != null) {
                this.monthDirectory = null;
                this.monthWatchKey.cancel();
                this.dayDirectory = null;
                this.dayWatchKey.cancel();
                this.hourDirectory = null;
                this.hourWatchKey.cancel();
                this.snapshotDirectory = null;
                this.snapshotWatchKey.cancel();

                String max = findLatestEntryInEvents(yearKey.pollEvents());
                if (max != null) {
                    this.monthDirectory = this.yearDirectory.resolve(max);
                    logger.info(String.format("Updating month watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.monthDirectory));
                    try {
                        this.monthWatchKey = this.monthDirectory.register(this.monthWatcher, ENTRY_CREATE);
                    } catch (IOException ioEx) {
                        this.monthDirectory = null;
                        logger.error(String.format("Error registering new month directory for '%s'.", this.snapshotRootDirectory), ioEx);
                    }
                }
                yearKey.reset();
            }
        }
        if (this.monthDirectory != null) {
            WatchKey monthKey = this.monthWatcher.poll();
            if (monthKey != null) {
                this.dayDirectory = null;
                this.dayWatchKey.cancel();
                this.hourDirectory = null;
                this.hourWatchKey.cancel();
                this.snapshotDirectory = null;
                this.snapshotWatchKey.cancel();
                String max = findLatestEntryInEvents(monthKey.pollEvents());
                if (max != null) {
                    this.dayDirectory = this.monthDirectory.resolve(max);
                    logger.info(String.format("Updating day watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.dayDirectory));
                    try {
                        this.dayWatchKey = this.dayDirectory.register(this.dayWatcher, ENTRY_CREATE);
                    } catch (IOException ioEx) {
                        this.dayDirectory = null;
                        logger.error(String.format("Error registering new day directory for '%s'.", this.snapshotRootDirectory), ioEx);
                    }
                }
                monthKey.reset();
            }
        }
        if (this.dayDirectory != null) {
            WatchKey dayKey = this.dayWatcher.poll();
            if (dayKey != null) {
                this.hourDirectory = null;
                this.hourWatchKey.cancel();
                this.snapshotDirectory = null;
                this.snapshotWatchKey.cancel();
                String max = findLatestEntryInEvents(dayKey.pollEvents());
                if (max != null) {
                    this.hourDirectory = this.dayDirectory.resolve(max);
                    logger.info(String.format("Updating hour watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.hourDirectory));
                    try {
                        this.hourWatchKey = this.hourDirectory.register(this.hourWatcher, ENTRY_CREATE);
                    } catch (IOException ioEx) {
                        this.hourDirectory = null;
                        logger.error(String.format("Error registering new hour directory for '%s'.", this.snapshotRootDirectory), ioEx);
                    }
                }
                dayKey.reset();
            }
        }
        if (this.hourDirectory != null) {
            WatchKey hourKey = this.hourWatcher.poll();
            if (hourKey != null) {
                this.snapshotDirectory = null;
                this.snapshotWatchKey.cancel();
                String max = findLatestEntryInEvents(hourKey.pollEvents());
                if (max != null) {
                    this.snapshotDirectory = this.hourDirectory.resolve(max);
                    logger.info(String.format("Updating snapshot watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.snapshotDirectory));
                    try {
                        this.snapshotWatchKey = this.snapshotDirectory.register(this.snapshotWatcher, ENTRY_CREATE);
                    } catch (IOException ioEx) {
                        this.snapshotDirectory = null;
                        logger.error(String.format("Error registering new snapshot directory for '%s'.", this.snapshotRootDirectory), ioEx);
                    }
                    newSnapshotPath = this.getSnapshotPathFromSnapshotDirectory();
                }
                hourKey.reset();
            }
        }
        if (this.snapshotDirectory != null) {
            WatchKey snapshotKey = this.snapshotWatcher.poll();
            if (snapshotKey != null) {
                String max = this.findLatestEntryInEvents(snapshotKey.pollEvents());
                if (max != null) {
                    newSnapshotPath = this.snapshotDirectory.resolve(max).toAbsolutePath().toString();
                }
                snapshotKey.reset();
            }
        }

        if (newSnapshotPath == null) {
            return this.updateWatchers();
        }

        return newSnapshotPath;
    }

    public void close() {
        logger.info(String.format("Closing watchers for '%s'.", this.snapshotRootDirectory));
        if (this.yearWatcher != null) {
            try {
                this.yearWatcher.close();
            } catch (IOException ioEx) {
                logger.error(String.format("Error closing year watcher for snapshot directory '%s'.", this.snapshotRootDirectory), ioEx);
            }
        }
        if (this.monthWatcher != null) {
            try {
                this.monthWatcher.close();
            } catch (IOException ioEx) {
                logger.error(String.format("Error closing month watcher for snapshot directory '%s'.", this.snapshotRootDirectory), ioEx);
            }
        }
        if (this.dayWatcher != null) {
            try {
                this.dayWatcher.close();
            } catch (IOException ioEx) {
                logger.error(String.format("Error closing day watcher for snapshot directory '%s'.", this.snapshotRootDirectory), ioEx);
            }
        }
        if (this.hourWatcher != null) {
            try {
                this.hourWatcher.close();
            } catch (IOException ioEx) {
                logger.error(String.format("Error closing hour watcher for snapshot directory '%s'.", this.snapshotRootDirectory), ioEx);
            }
        }
        if (this.snapshotWatcher != null) {
            try {
                this.snapshotWatcher.close();
            } catch (IOException ioEx) {
                logger.error(String.format("Error closing snapshot watcher for snapshot directory '%s'.", this.snapshotRootDirectory), ioEx);
            }
        }
    }

    private String findLatestEntryInEvents(List<WatchEvent<?>> events) {
        String max = null;
        List<String> fileNames = new ArrayList<>(events.size());

        for (WatchEvent<?> event : events) {
            WatchEvent.Kind<?> kind = event.kind();

            if (kind == OVERFLOW) {
                continue;
            }

            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path fileName = ev.context();

            fileNames.add(fileName.toString());
        }

        if (!fileNames.isEmpty()) {
            String[] fileNameArray = fileNames.toArray(new String[fileNames.size()]);
            int maxIndex = Helpers.getMax(fileNameArray);
            max = fileNameArray[maxIndex];
        }

        return max;
    }

    private String updateWatchers() {
        if (this.yearDirectory == null) {
            logger.info(String.format("Updating year watcher for '%s'.", this.snapshotRootDirectory));
            this.yearDirectory = Paths.get(this.snapshotRootDirectory);
            try {
                this.yearWatchKey = this.yearDirectory.register(this.yearWatcher, ENTRY_CREATE);
            } catch (IOException ioEx) {
                this.yearDirectory = null;
                logger.error(String.format("Error registering new year directory for '%s'.", this.snapshotRootDirectory), ioEx);
                return null;
            }
        }

        if (this.monthDirectory == null) {
            logger.info(String.format("Updating month watcher for '%s'.", this.snapshotRootDirectory));
            File yearDirectoryFile = this.yearDirectory.toFile();
            File[] yearDirectories = yearDirectoryFile.listFiles();
            if (yearDirectories == null || yearDirectories.length == 0) {
                logger.warn(String.format("Year directory empty or access denied: %s", this.yearDirectory.toString()));
                return null;
            }
            int maxYearDirectory = Helpers.getMax(yearDirectories);

            this.monthDirectory = yearDirectories[maxYearDirectory].toPath();
            logger.info(String.format("Updating month watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.monthDirectory));
            try {
                this.monthWatchKey = this.monthDirectory.register(this.monthWatcher, ENTRY_CREATE);
            } catch (IOException ioEx) {
                this.monthDirectory = null;
                logger.error(String.format("Error registering new month directory for '%s'.", this.snapshotRootDirectory), ioEx);
                return null;
            }
        }

        if (this.dayDirectory == null) {
            logger.info(String.format("Updating day watcher for '%s'.", this.snapshotRootDirectory));
            File monthDirectoryFile = this.monthDirectory.toFile();
            File[] monthDirectories = monthDirectoryFile.listFiles();
            if (monthDirectories == null || monthDirectories.length == 0) {
                logger.warn(String.format("Month directory empty or access denied: %s", this.monthDirectory.toString()));
                return null;
            }
            int maxMonthDirectory = Helpers.getMax(monthDirectories);

            this.dayDirectory = monthDirectories[maxMonthDirectory].toPath();
            logger.info(String.format("Updating day watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.dayDirectory));
            try {
                this.dayWatchKey = this.dayDirectory.register(this.dayWatcher, ENTRY_CREATE);
            } catch (IOException ioEx) {
                this.dayDirectory = null;
                logger.error(String.format("Error registering day year directory for '%s'.", this.snapshotRootDirectory), ioEx);
                return null;
            }
        }

        if (this.hourDirectory == null) {
            logger.info(String.format("Updating hour watcher for '%s'.", this.snapshotRootDirectory));
            File dayDirectoryFile = this.dayDirectory.toFile();
            File[] dayDirectories = dayDirectoryFile.listFiles();
            if (dayDirectories == null || dayDirectories.length == 0) {
                logger.warn(String.format("Day directory empty or access denied: %s", this.dayDirectory.toString()));
                return null;
            }
            int maxDayDirectory = Helpers.getMax(dayDirectories);

            this.hourDirectory = dayDirectories[maxDayDirectory].toPath();
            logger.info(String.format("Updating hour watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.hourDirectory));
            try {
                this.hourWatchKey = this.hourDirectory.register(this.hourWatcher, ENTRY_CREATE);
            } catch (IOException ioEx) {
                this.hourDirectory = null;
                logger.error(String.format("Error registering new hour directory for '%s'.", this.snapshotRootDirectory), ioEx);
                return null;
            }
        }

        if (this.snapshotDirectory == null) {
            logger.info(String.format("Updating snapshot watcher for '%s'.", this.snapshotRootDirectory));
            File hourDirectoryFile = this.hourDirectory.toFile();
            File[] hourDirectories = hourDirectoryFile.listFiles();
            if (hourDirectories == null || hourDirectories.length == 0) {
                logger.warn(String.format("Hour directory empty or access denied: %s", this.snapshotDirectory.toString()));
                return null;
            }
            int maxHourDirectory = Helpers.getMax(hourDirectories);

            this.snapshotDirectory = hourDirectories[maxHourDirectory].toPath();
            logger.info(String.format("Updating snapshot watcher for '%s' to '%s'.", this.snapshotRootDirectory, this.snapshotDirectory));
            try {
                this.snapshotWatchKey = this.snapshotDirectory.register(this.snapshotWatcher, ENTRY_CREATE);
            } catch (IOException ioEx) {
                this.snapshotDirectory = null;
                logger.error(String.format("Error registering new snapshot directory for '%s'.", this.snapshotRootDirectory), ioEx);
                return null;
            }

            return this.getSnapshotPathFromSnapshotDirectory();
        }

        return null;
    }

    private String getSnapshotPathFromSnapshotDirectory() {
        File snapshotDirectoryFile = this.snapshotDirectory.toFile();
        File[] snapshotFiles = snapshotDirectoryFile.listFiles();
        if (snapshotFiles == null || snapshotFiles.length == 0) {
            logger.warn(String.format("Snapshot directory empty or access denied: %s", this.hourDirectory.toString()));
            return null;
        }
        int maxSnapshotFile = Helpers.getMax(snapshotFiles);
        return snapshotFiles[maxSnapshotFile].getAbsolutePath();
    }

}
