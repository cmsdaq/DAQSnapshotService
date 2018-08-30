package websocket;

import org.apache.log4j.Logger;
import utils.DAQSetup;
import utils.SetupManager;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@WebListener
@ServerEndpoint("/WebSocket")
public class WebSocketServer implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(WebSocketServer.class);

    private static ExecutorService executorService;

    private static final Map<String, Set<String>> setupSubscriptions = new ConcurrentHashMap<>(64, 1, 2);

    private static final Map<String, Session> sessionById = new ConcurrentHashMap<>(64, 1, 5);

    private static final Map<String, String> messageBySessionId = new ConcurrentHashMap<>(64, 1, 5);

    private static final BlockingQueue<String> pendingDeliveries = new LinkedBlockingQueue<>();
    private static final Set<String> pendingDeliveriesSet = new HashSet<>();

    private static final int notificationThreads = 4;

    private static SetupManager setupManager = null;

    public static void setSetupManager(SetupManager setupManager) {
        WebSocketServer.setupManager = setupManager;
    }

    public static void notifyClients(String setup, String snapshot) {
        synchronized (setupSubscriptions) {
            Set<String> subscriptions = setupSubscriptions.get(setup);
            if (subscriptions == null) {
                return;
            }
            for (String subscription : subscriptions) {
                synchronized (pendingDeliveriesSet) {
                    messageBySessionId.put(subscription, snapshot);
                    if (!pendingDeliveriesSet.contains(subscription)) {
                        pendingDeliveriesSet.add(subscription);
                        pendingDeliveries.add(subscription);
                    }
                }
            }
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        logger.info("Starting web socket server.");
        if (executorService != null) {
            shutdownExecutorService();
        }
        executorService = Executors.newFixedThreadPool(notificationThreads);
        sessionById.clear();
        setupSubscriptions.clear();
        messageBySessionId.clear();
        pendingDeliveries.clear();

        for (int i = 0; i < notificationThreads; i++) {
            executorService.submit(new NotificationThread());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        logger.info("Stopping web socket server.");
        this.shutdownExecutorService();
    }

    private void shutdownExecutorService() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException intEx) {
            logger.warn("Interrupted while waiting for executor service to shut down.", intEx);
        }
        executorService = null;
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        session.setMaxIdleTimeout(5 * 60 * 1000);

        List<String> setups = session.getRequestParameterMap().get("setup");
        if (setups == null || setups.isEmpty()) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                        "No setup specified."));
            } catch (IOException ioEx) {
                logger.warn("Error rejecting session due to missing setup parameter.", ioEx);
            }
            return;
        }

        String sessionId = session.getId();
        sessionById.put(sessionId, session);
        String setupName = setups.get(0);

        if (setupManager == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT,
                        "The WebSocket server is not yet ready to process subscriptions."));
            } catch (IOException ioEx) {
                logger.warn("Error rejecting session due to missing setup manager.", ioEx);
            }
            return;
        }

        DAQSetup setup = setupManager.getSetupByName(setupName);
        if (setup == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                        "Unknown setup specified."));
            } catch (IOException ioEx) {
                logger.warn("Error rejecting session due to invalid setup.", ioEx);
            }
            return;
        }

        this.subscribeToSetup(setups.get(0), sessionId);
        String snapshot = setup.getLatestSnapshot();
        notifyClients(setupName, snapshot);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
    }

    @OnClose
    public void onClose(Session session) {
        closeSession(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.warn(String.format("WebSocket error for session %s.", session.getId()), error);
        closeSession(session);
    }

    private void subscribeToSetup(String setup, String sessionId) {
        synchronized (setupSubscriptions) {
            Set<String> subscriptions = setupSubscriptions.computeIfAbsent(setup, k -> new HashSet<>());
            subscriptions.add(sessionId);
        }
    }

    private void closeSession(Session session) {
        String sessionId = session.getId();
        sessionById.remove(sessionId);
        messageBySessionId.remove(sessionId);
        synchronized (setupSubscriptions) {
            for (Map.Entry<String, Set<String>> subscription : setupSubscriptions.entrySet()) {
                subscription.getValue().remove(sessionId);
            }
        }
    }

    private class NotificationThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                String sessionId;
                try {
                    sessionId = pendingDeliveries.take();
                } catch (InterruptedException intEx) {
                    Thread.currentThread().interrupt();
                    continue;
                }
                String message;
                synchronized (pendingDeliveriesSet) {
                    message = messageBySessionId.get(sessionId);
                    if (!pendingDeliveriesSet.remove(sessionId)) {
                        return;
                    }
                }
                if (message == null) {
                    continue;
                }
                Session session = sessionById.get(sessionId);
                if (session == null) {
                    continue;
                }
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException ioEx) {
                    logger.warn("Error sending message to WebSocket client.", ioEx);
                }
            }
        }

    }


}
