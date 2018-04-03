package gr.exmachina.tbmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.configuration.ConfigurationService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configurable Kura bundle to start/stop an install tb-gateway service.
 * 
 * @author ExMachina
 *
 */
public class TbManager implements ConfigurableComponent
{

    public static final Logger m_logger = LoggerFactory.getLogger(TbManager.class);

    /** Config set in kura ui */
    private Map<String, Object> m_properties;

    /** Thingsboard gw service name */
    public static final String TBGW_SERVICE_NAME = "tb-gateway";

    /** Config property names */
    public static final String PROP_TBGW_SERVICE_ACTIVE = "tbgw.service_active";

    /** Current app id */
    public static final String APP_ID = "gr.exmachina.tbmanager.TbManager";

    /** Config service object */
    private static ConfigurationService m_configService;

    /** Checks if service is alive and updates config */
    private static ServiceStatusPollThread m_serviceStatusPollThread;

    /** Check if service alive interval (ms) */
    public static final int THREAD_ALIVE_CHECK_INTERVAL = 3000;

    /** Set when thread updates config which causes next updated() call to be ignored */
    private static boolean m_ConfigUpdatedByThread = false;

    /**
     * Called by kura when bundle is loaded
     * 
     * @param componentContext
     */
    protected void activate(ComponentContext componentContext)
    {
        m_logger.info("Bundle " + APP_ID + " has started!");
    }

    /**
     * Called by kura when bundle is loaded with config
     * 
     * @param componentContext
     * @param properties
     */
    protected void activate(ComponentContext componentContext, Map<String, Object> properties)
    {
        m_logger.info("Bundle " + APP_ID + " has started with config!");
        updated(properties);
    }

    /**
     * Called by kura when bundle is unloaded
     */
    protected void deactivate(ComponentContext componentContext)
    {
        m_logger.info("Bundle " + APP_ID + " has stopped!");
        m_serviceStatusPollThread.stop();
    }

    /**
     * Called by Kura when bundle config is updated from web ui
     * 
     * @param properties
     */
    public void updated(Map<String, Object> properties)
    {
        // Output all props
        m_properties = properties;
        if (properties != null && !properties.isEmpty())
        {
            Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
            while (it.hasNext())
            {
                Entry<String, Object> entry = it.next();
                m_logger.info("New property - " + entry.getKey() + " = " + entry.getValue() + " of type "
                        + entry.getValue().getClass().toString());
            }
        }

        if (getConfigUpdatedByThread())
        {
            setConfigUpdatedByThread(false);
            return;
        }

        //
        // Start/stop service according to conf
        //
        Boolean confServiceActive = (Boolean) properties.get(PROP_TBGW_SERVICE_ACTIVE);

        if (confServiceActive != null)
        {
            // Get current status, if status hasn't changed don't update
            if (getServiceStatus() != confServiceActive)
                setServiceStatus(confServiceActive);
        }

    }

    /**
     * Start stop tbgw service
     * 
     * @param active New status
     * @return True if change was successful
     */
    private static boolean setServiceStatus(boolean isActive)
    {
        String action = isActive ? "start" : "stop";
        Process proc;
        try
        {
            proc = Runtime.getRuntime().exec("service " + TBGW_SERVICE_NAME + " " + action);

            proc.waitFor();

            for (int i = 0; i < 3; i++)
            {
                if (getServiceStatus() == isActive)
                    break;
                Thread.sleep(500);
            }

            proc.destroy();
        }
        catch (Exception e)
        {
            m_logger.info("Could not set gw service status.");
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Check if tb-gw service still active
     */
    public static boolean getServiceStatus()
    {
        Process proc;
        String line;
        boolean isActive = false;

        try
        {
            proc = Runtime.getRuntime().exec("service tb-gateway status");

            BufferedReader buff = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            while ((line = buff.readLine()) != null)
            {
                if (line.matches(".*Active: active.*"))
                {
                    isActive = true;
                    break;
                }
            }
            proc.waitFor();

            proc.destroy();
        }
        catch (Exception e)
        {
            m_logger.info("Could not check gw status.");
            e.printStackTrace();

            return isActive;
        }

        return isActive;
    }

    /** Called by Kura to set the ConfigurationService */
    public void setConfigService(ConfigurationService configService)
    {
        m_logger.info("Setting the config service.");
        m_configService = configService;

        // As soon as we get the configService, we start polling thread
        m_serviceStatusPollThread = new ServiceStatusPollThread();
        m_serviceStatusPollThread.start();
    }

    /** Called by Kura to unset the ConfigurationService */
    public void unsetConfigService(ConfigurationService configService)
    {
        m_logger.info("Unsetting the config service.");
        m_configService = null;
    }

    /** Accessor */
    public static ConfigurationService getConfigService()
    {
        return m_configService;
    }

    /** Accessor */
    public static void setConfigUpdatedByThread(boolean val)
    {
        m_ConfigUpdatedByThread = val;
    }

    /** Accessor */
    public static boolean getConfigUpdatedByThread()
    {
        return m_ConfigUpdatedByThread;
    }
}