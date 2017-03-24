package servlets;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import javax.servlet.annotation.WebServlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import utils.SetupManager;

@WebServlet("/managesetup")
public class SetupLauncherAPI extends HttpServlet {


	/**
	 * 
	 */
	private static final long serialVersionUID = -7077465496085876703L;
	
	private static final Logger logger = Logger.getLogger(SetupLauncherAPI.class);

	@Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
		Date tic = new Date();
		
		SetupManager setupManager  = (SetupManager)getServletContext().getAttribute("setupManager");
		
		String source = request.getRemoteHost();
		
		String setup = "";
		String action = "";
		
		
		for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()){
			
			setup = e.getKey().replace("click_", "");
			action = e.getValue()[0];
			
			logger.info("Received button command (src="+source+") to "+action+" "+setup+" setup");
			break; // only one parameter with one value expected
		}
		
        if (action.equalsIgnoreCase("start")) {
           setupManager.startSetupByName(setup, new Date());
        } else {
           setupManager.stopSetupByName(setup);
        } 

        response.sendRedirect(getServletContext().getContextPath()+"/daqviews.jsp");
        
        Date toc = new Date();
		logger.debug("Serving launcher API request took "+(toc.getTime()-tic.getTime())+" milliseconds");
    }
	
}
