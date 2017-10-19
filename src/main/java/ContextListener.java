
import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import tasks.DiskUsageTask;
import tasks.GetLatestTask;
import tasks.SetupDetectionTask;
import utils.Helpers;
import utils.SetupManager;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */

@WebListener
public class ContextListener implements javax.servlet.ServletContextListener {

	private ScheduledExecutorService scheduler;

	private static final Logger logger = Logger.getLogger(ContextListener.class);

	@Override
	public void contextInitialized(ServletContextEvent event) {

		logger.info("Initializing DAQSnapshotService application...");
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.INFO);
		logger.info("Logging level: " + rootLogger.getLevel().toString());

		String webAppConfigurationPath = null;
		try {
			webAppConfigurationPath = System.getenv("SNAPSHOT_CONF");
			if (webAppConfigurationPath == null) {
				throw new RuntimeException(
						"No configuration file supplied with environment variable SNAPSHOT_CONF. e.g. use export SNAPSHOT_CONF=/cmsnfshome0/nfshome0/daqpro/daqexpert/pro/daqview-react-server/daqview-react-server-conf/DAQSnapshotService-server.properties");
			}
			logger.info("Loading properties from environment variable location");

			// For production application, please alter the path below
			// String webAppConfigurationPath =
			// "/cmsnfshome0/nfshome0/daqpro/daqexpert/pro/daqview-react-server/daqview-react-server-conf/DAQSnapshotService-server.properties";
			// "/cmsnfshome0/nfshome0/daqpro/daqexpert/dev/daqview-react-server/daqview-react-server-conf/DAQSnapshotService-server.properties";

			// load server props at startup (be careful: these are NOT the DAQAggregator properties)
			Properties properties = Helpers.loadProps(webAppConfigurationPath);
			logger.info("Loaded server properties from file: " + webAppConfigurationPath);
			event.getServletContext().setAttribute("properties", properties); // registering to global scope

			// Primary manager of setups
			SetupManager setupManager = new SetupManager(properties);
			logger.info("Initialized setupManager object");
			event.getServletContext().setAttribute("setupManager", setupManager); // registering to global scope

			// scheduler for periodical tasks, such
			scheduler = Executors.newScheduledThreadPool(4);
			logger.info("Initialized scheduler");

			// schedule setup detection task (once every 10s)
			int delaySd = 5; // seconds
			int periodSd = 10; // seconds
			scheduler.scheduleAtFixedRate(new SetupDetectionTask(setupManager), delaySd, periodSd, TimeUnit.SECONDS);
			logger.info("Scheduled setup detection task: first detection will be launched after " + delaySd
					+ "s and every " + periodSd + " afterwards");

			// schedule du command at quite less frequent intervals
			int delayDu = 30; // seconds
			int periodDu = 270000000; // seconds
			scheduler.scheduleAtFixedRate(new DiskUsageTask(setupManager), delayDu, periodDu, TimeUnit.SECONDS);
			logger.info("Scheduled disk usage task: first detection will be launched after " + delayDu + "s and every "
					+ periodDu + " afterwards");

			// schedule latest snapshot discovery, to store latest snapshot for a setup without needing prompt from a
			// request
			int delaySn = 10000; // milliseconds
			int periodSn = 200; // milliseconds
			scheduler.scheduleAtFixedRate(new GetLatestTask(setupManager), delaySn, periodSn, TimeUnit.MILLISECONDS);
			logger.info("Scheduled latest snapshots discovery task: first detection will be launched after " + delaySn
					+ "ms and every " + periodSn + " afterwards");

		} catch (RuntimeException e) {
			logger.error("Failed to execute server startup as expected");
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		logger.info("Destroying context...");
		scheduler.shutdownNow();
		logger.info("Scheduler shut down");
	}

}
