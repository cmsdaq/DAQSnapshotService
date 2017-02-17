package servlets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import rcms.utilities.daqaggregator.data.DAQ;
import rcms.utilities.daqaggregator.persistence.PersistenceFormat;
import rcms.utilities.daqaggregator.persistence.StructureSerializer;
import utils.APIPersistorManager;
import utils.DAQSetup;
import utils.SetupManager;

/**
 * Request snapshots API
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 * @author Maciej Gladki (maciej.szymon.gladki@cern.ch) 
 *
 */

@WebServlet("/getsnapshot")
public class SnapshotAPI extends HttpServlet {
	

	/**
	 * 
	 */
	private static final long serialVersionUID = -4707199804021139531L;

	private static final Logger logger = Logger.getLogger(SnapshotAPI.class);

	ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		SetupManager setupManager  = (SetupManager)getServletContext().getAttribute("setupManager");
		
		String time = request.getParameter("time");
		String setup = request.getParameter("setup");
		
		logger.debug("Requested snapshot date: " + time);
		Date timeDate = objectMapper.readValue(time, Date.class);
		logger.debug("Parsed requested snapshot date: " + timeDate);
		String json = "";
		try {
			DAQSetup daqSetup = setupManager.getSetupByName(setup);
			
			if (daqSetup != null){
				
				//differentiate between time request and get latest snapshot when time param is empty
				
				APIPersistorManager persistorManager = new APIPersistorManager(daqSetup.getSnapshotPath());

				DAQ result = persistorManager.findSnapshot(timeDate);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();

				StructureSerializer ss = new StructureSerializer();
				ss.serialize(result, baos, PersistenceFormat.JSONREFPREFIXEDUGLY);

				json = baos.toString(java.nio.charset.StandardCharsets.UTF_8.toString());

				logger.debug("Found snapshot with timestamp: " + new Date(result.getLastUpdate()));
				logger.debug("Snapshot fragment: " + json.substring(0, 1000));
			}else{
				logger.warn("Request without DAQ setup specified received");
				throw new RuntimeException();
			}
		} catch (RuntimeException e) {
			logger.warn("Requested snapshot with date: " + time + " and setup: "+ setup +" could not be found");
			Map<String, String> result = new HashMap<>();
			result.put("message", "Could not find snapshot");
			json = objectMapper.writeValueAsString(result);
		}

		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Methods", "GET");
		response.addHeader("Access-Control-Allow-Headers",
				"X-PINGOTHER, Origin, X-Requested-With, Content-Type, Accept");
		response.addHeader("Access-Control-Max-Age", "1728000");

		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(json);

	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}
}