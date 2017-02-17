package utils;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

import org.apache.log4j.Logger;


/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */
 
@WebListener
public class ContextListener implements javax.servlet.ServletContextListener{

	private ScheduledExecutorService scheduler;
	
	private static final Logger logger = Logger.getLogger(ContextListener.class);

	@Override
	public void contextInitialized(ServletContextEvent event) {
		
	
		String webAppConfigurationPath = "/usr/mvougiou/monitoring_pro/server_config/daqview-react-server.properties";
		
		try{
		
		//load server props at startup (be careful: these are NOT the DAQAggregator properties)
		Properties properties = Helpers.loadProps(webAppConfigurationPath);
		
		
		//global variables (to be loaded from config file of the webapp)
		String daqAggregatorConfigFilesDirPath = properties.getProperty("daqAggregatorConfigFilesDirPath");

		String daqAggregatorPidLogFile = properties.getProperty("daqAggregatorPidLogFile");
		
		String linkToDaqviewApache = properties.getProperty("linkToDaqviewApache");
		
				
		//must also pass properties file containing masked setups, as these can change during one server execution, therefore file will be checked periodically
		SetupManager setupManager = new SetupManager(daqAggregatorConfigFilesDirPath,daqAggregatorPidLogFile);
		

		//register to global scope
		event.getServletContext().setAttribute("setupManager", setupManager);
		event.getServletContext().setAttribute("linktodaqview", linkToDaqviewApache);

		
		scheduler = Executors.newSingleThreadScheduledExecutor();
		
		//schedule setup detection task (once every 10s)
		scheduler.scheduleAtFixedRate(new SetupDetectionTask(setupManager), 5, 10, TimeUnit.SECONDS);
		
		//schedule du command at quite less frequent intervals

		}catch(RuntimeException e){
			logger.error("Failed to execute server startup as expected");
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		scheduler.shutdownNow();
	}


}
