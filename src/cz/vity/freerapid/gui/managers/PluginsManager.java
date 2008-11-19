package cz.vity.freerapid.gui.managers;

import cz.vity.freerapid.model.PluginMetaData;
import cz.vity.freerapid.plugimpl.PluginContextImpl;
import cz.vity.freerapid.plugins.exceptions.NotSupportedDownloadServiceException;
import cz.vity.freerapid.plugins.webclient.PluginContext;
import cz.vity.freerapid.plugins.webclient.ShareDownloadService;
import cz.vity.freerapid.utilities.LogUtils;
import cz.vity.freerapid.utilities.Utils;
import org.java.plugin.ObjectFactory;
import org.java.plugin.Plugin;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.standard.ShadingPathResolver;
import org.java.plugin.standard.StandardPluginLocation;
import org.java.plugin.util.ExtendedProperties;
import org.jdesktop.application.Application;
import org.jdesktop.application.ApplicationContext;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Vity
 */
public class PluginsManager {
    private final static Logger logger = Logger.getLogger(PluginsManager.class.getName());

    //private Map<String, ShareDownloadService> loadedPlugins = new Hashtable<String, ShareDownloadService>();
    //  private Map<String, Pattern> supportedURLs = new HashMap<String, Pattern>();

    private Map<String, PluginMetaData> supportedPlugins = new HashMap<String, PluginMetaData>();

    private final Object lock = new Object();

    private final ApplicationContext context;
    private PluginManager pluginManager;


    public PluginsManager(ApplicationContext context) {
        this.context = context;
        this.context.getApplication().addExitListener(new Application.ExitListener() {
            public boolean canExit(EventObject event) {
                return true;
            }

            public void willExit(EventObject event) {
                //pluginManager.isPluginActivated()
                try {
                    pluginManager.shutdown();
                    //TODO neco co smaze soubory z tempu? framework na to sere!
                } catch (Exception e) {
                    //ignore
                }
            }
        });
        loadPlugins();
    }


    private void loadPlugins() {

        logger.info("Init Plugins Manager");
//        final ExtendedProperties config = new ExtendedProperties(Utils.loadProperties("jpf.properties", true));
        final ObjectFactory objectFactory = ObjectFactory.newInstance();
        final ShadingPathResolver resolver = new ShadingPathResolver();
        try {
            resolver.configure(new ExtendedProperties());
        } catch (Exception e) {
            LogUtils.processException(logger, e);
        }
        //    pluginManager = objectFactory.createManager(objectFactory.createRegistry(), resolver);
        pluginManager = objectFactory.createManager(objectFactory.createRegistry(), resolver);

        final File pluginsDir = new File(Utils.getAppPath(), "plugins");
        logger.info("Plugins dir: " + pluginsDir.getAbsolutePath());

        File[] plugins = pluginsDir.listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".frp");
            }

        });

        try {
            if (plugins == null)
                throw new IllegalStateException("Plugins directory does not exists");
            final int length = plugins.length;
            final PluginManager.PluginLocation[] loc = new PluginManager.PluginLocation[length];

            for (int i = 0; i < length; i++) {

                try {
                    final String path = fileToUrl(plugins[i]).toExternalForm();
                    logger.info("Plugins path:" + path);
                    final URL context = new URL("jar:" + path + "!/");
                    final URL manifest = new URL("jar:" + path + "!/plugin.xml");

                    //loc[i] = StandardPluginLocation.create(plugins[i]);
                    loc[i] = new StandardPluginLocation(context, manifest);
                } catch (MalformedURLException e) {
                    LogUtils.processException(logger, e);
                }

                //loc[i] = StandardPluginLocation.create(plugins[i]);
                //logger.info("Plugin location: " + loc);
            }

            pluginManager.publishPlugins(loc);

            final Collection<PluginDescriptor> pluginDescriptorCollection = pluginManager.getRegistry().getPluginDescriptors();
            for (PluginDescriptor pluginDescriptor : pluginDescriptorCollection) {
                final String id = pluginDescriptor.getId();
                if (supportedPlugins.containsKey(id)) {
                    supportedPlugins.get(id).setPluginDescriptor(pluginDescriptor);
                } else {
                    supportedPlugins.put(id, new PluginMetaData(pluginDescriptor));
                }
            }

        } catch (Exception e) {
            LogUtils.processException(logger, e);
            context.getApplication().exit();
        }
//        final FileFactoryShareServiceImpl factoryShareService = new FileFactoryShareServiceImpl();
//        loadedPlugins.put(factoryShareService.getName(), factoryShareService);
    }

    private static URL fileToUrl(final File plugin) throws MalformedURLException {
        return plugin.toURI().toURL();
    }

    /**
     * Overuje, zda je dane URL podporovane mezi pluginy
     *
     * @param url
     * @return vraci v pripade, ze nejaky plugin podporuje dane URL, jinak false
     */
    public boolean isSupported(final URL url) {
        final String s = url.toExternalForm();
        for (PluginMetaData pluginMetaData : supportedPlugins.values()) {
            if (pluginMetaData.isSupported(s))
                return true;
        }
        return false;
    }

    /**
     * Vraci ID sluzby podle daneho URL
     *
     * @param url
     * @return
     * @throws NotSupportedDownloadServiceException
     *
     */
    public String getServiceIDForURL(URL url) throws NotSupportedDownloadServiceException {
        final Set<Map.Entry<String, PluginMetaData>> entries = this.supportedPlugins.entrySet();
        final String s = url.toExternalForm();
        for (Map.Entry<String, PluginMetaData> entry : entries) {
            if (entry.getValue().isSupported(s))
                return entry.getKey();
        }
        throw new NotSupportedDownloadServiceException();
    }

    /**
     * Vraci samotny plugin z registry podle jeho ID.
     * Provadi jeho dynamickou alokaci.
     *
     * @param id ID pluginu
     * @return nacteny plugin - tato hodnota neni nikdy null
     * @throws NotSupportedDownloadServiceException
     *          pokud doslo k chybe pri ziskani pluginu podle daneho ID
     */
    public ShareDownloadService getPluginInstance(final String id) throws NotSupportedDownloadServiceException {

        synchronized (lock) {
            Plugin p;
            try {
                logger.info("Loading plugin with ID=" + id);
                p = pluginManager.getPlugin(id);
            } catch (Exception e) {
                LogUtils.processException(logger, e);
                throw new NotSupportedDownloadServiceException(id);
            }
            if (!(p instanceof ShareDownloadService))
                throw new NotSupportedDownloadServiceException(id);
            final ShareDownloadService plugin = (ShareDownloadService) p;
            if (plugin.getPluginContext() == null)
                plugin.setPluginContext(createPluginContext());
            logger.info("Plugin with ID=" + id + " was loaded");
            return plugin;
        }
    }

    private PluginContext createPluginContext() {
        return PluginContextImpl.create(null, null);
    }

    public List<PluginMetaData> getSupportedPlugins() {
        return new ArrayList<PluginMetaData>(this.supportedPlugins.values());
    }
}
