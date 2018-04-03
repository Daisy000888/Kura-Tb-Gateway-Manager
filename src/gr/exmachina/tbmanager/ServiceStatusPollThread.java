package gr.exmachina.tbmanager;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ComponentConfiguration;

/**
 * Checks if tb-gateway thread is alive every x ms and updates kura setting
 * so it represents the current tb-gateway status at all times
 * 
 * @author ExMachina
 *
 */
public class ServiceStatusPollThread implements Runnable
{

    private boolean doPoll = false;
    Thread m_thread;

    /**
     * Constructor
     */
    public ServiceStatusPollThread()
    {
        m_thread = new Thread(this);
    }

    public void start()
    {
        doPoll = true;
        m_thread.start();
    }

    public void stop()
    {
        doPoll = false;
    }

    /**
     * Polling thread
     */
    @Override
    public void run()
    {
        while (doPoll)
        {
            try
            {
                Thread.sleep(TbManager.THREAD_ALIVE_CHECK_INTERVAL);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            try
            {
                // Get current service status
                boolean serviceStatus = TbManager.getServiceStatus();

                // Get current config status
                ComponentConfiguration componentConfig = TbManager.getConfigService()
                        .getComponentConfiguration(TbManager.APP_ID);
                Map<String, Object> props = componentConfig.getConfigurationProperties();

                // Update only if changed
                if (Boolean.valueOf(props.get(TbManager.PROP_TBGW_SERVICE_ACTIVE).toString()) != serviceStatus)
                {
                    Map<String, Object> new_conf = new HashMap<String, Object>();
                    new_conf.put("tbgw.service_active", serviceStatus);

                    TbManager.setConfigUpdatedByThread(true);

                    TbManager.getConfigService().updateConfiguration(TbManager.APP_ID, new_conf);
                }
            }
            catch (KuraException e)
            {
                e.printStackTrace();
            }
        }

    }

}
