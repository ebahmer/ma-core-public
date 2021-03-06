/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 *
 */
package com.serotonin.m2m2;

import java.util.List;

import com.infiniteautomation.mango.io.serial.virtual.VirtualSerialPortConfig;
import com.infiniteautomation.mango.io.serial.virtual.VirtualSerialPortConfigResolver;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.m2m2.module.Module;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.rt.event.type.EventTypeResolver;
import com.serotonin.m2m2.util.MapWrap;
import com.serotonin.m2m2.util.MapWrapConverter;
import com.serotonin.m2m2.view.chart.BaseChartRenderer;
import com.serotonin.m2m2.view.chart.ChartRenderer;
import com.serotonin.m2m2.view.text.BaseTextRenderer;
import com.serotonin.m2m2.view.text.TextRenderer;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.mailingList.EmailRecipient;
import com.serotonin.m2m2.vo.mailingList.EmailRecipientResolver;
import com.serotonin.m2m2.web.mvc.spring.MangoRestSpringConfiguration;
import com.serotonin.provider.Providers;
import com.serotonin.provider.TimerProvider;

/**
 * Dummy implementation for Mango Lifecycle for use in testing, 
 *   override as necessary.
 *
 * @author Terry Packer
 */
public class MockMangoLifecycle implements IMangoLifecycle{

    protected boolean enableWebConsole;
    protected int webPort;
    protected List<Module> modules;
    /**
     * Create a default lifecycle with an H2 web console on port 9001 
     *   to view the in-memory database.
     */
    public MockMangoLifecycle(List<Module> modules) {
        this(modules, true, 9001);
    }
    
    public MockMangoLifecycle(List<Module> modules, boolean enableWebConsole, int webPort) {
        this.enableWebConsole = enableWebConsole;
        this.webPort = webPort;
        this.modules = modules;
    }
    /**
     * Startup a dummy Mango with a basic infrastructure
     */
    public void initialize() {
        
        Common.MA_HOME =  System.getProperty("ma.home"); 
        if(Common.MA_HOME == null)
            Common.MA_HOME = ".";
        
        //Add in modules
        for(Module module : modules)
            ModuleRegistry.addModule(module);
        
        Providers.add(IMangoLifecycle.class, this);
        
        //TODO Licensing Providers.add(ICoreLicense.class, new CoreLicenseDefinition());
        //TODO Licensing Providers.add(ITimedLicenseRegistrar.class, new TimedLicenseRegistrar());
        Common.free = false;
        
        //Startup a simulation timer provider
        Providers.add(TimerProvider.class, new SimulationTimerProvider());
        
        //Make sure that Common and other classes are properly loaded
        Common.envProps = new MockMangoProperties();
        
        MangoRestSpringConfiguration.initializeObjectMapper();
        
        Common.JSON_CONTEXT.addResolver(new EventTypeResolver(), EventType.class);
        Common.JSON_CONTEXT.addResolver(new BaseChartRenderer.Resolver(), ChartRenderer.class);
        Common.JSON_CONTEXT.addResolver(new BaseTextRenderer.Resolver(), TextRenderer.class);
        Common.JSON_CONTEXT.addResolver(new EmailRecipientResolver(), EmailRecipient.class);
        Common.JSON_CONTEXT.addResolver(new VirtualSerialPortConfigResolver(), VirtualSerialPortConfig.class);
        Common.JSON_CONTEXT.addConverter(new MapWrapConverter(), MapWrap.class);
        
        Common.eventManager = new MockEventManager();
        
        //TODO This must be done only once because we have a static
        // final referece to the PointValueDao in the PointValueCache class
        // and so if you try to restart the database it doesn't get the new connection
        // for each new test.
        //Start the Database so we can use Daos (Base Dao requires this)
        if(Common.databaseProxy == null) {
            Common.databaseProxy = new H2InMemoryDatabaseProxy(enableWebConsole, webPort);
            Common.databaseProxy.initialize(null);
        }
        
        //Only configure if not already configured
        if(Common.runtimeManager == null)
            Common.runtimeManager = new MockRuntimeManager();
        
        if(Common.serialPortManager == null)
            Common.serialPortManager = new MockSerialPortManager();
        
        //Ensure we start with the proper timer
        if(Common.backgroundProcessing == null)
            Common.backgroundProcessing = new MockBackgroundProcessing(); 
        Common.backgroundProcessing.initialize(false);
        
    }
    
    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#isTerminated()
     */
    @Override
    public boolean isTerminated() {
        return false;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#terminate()
     */
    @Override
    public void terminate() {
        H2InMemoryDatabaseProxy proxy = (H2InMemoryDatabaseProxy) Common.databaseProxy;
        try {
            proxy.clean();
        } catch (Exception e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#addStartupTask(java.lang.Runnable)
     */
    @Override
    public void addStartupTask(Runnable task) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#addShutdownTask(java.lang.Runnable)
     */
    @Override
    public void addShutdownTask(Runnable task) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#getLifecycleState()
     */
    @Override
    public int getLifecycleState() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#getStartupProgress()
     */
    @Override
    public float getStartupProgress() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#getShutdownProgress()
     */
    @Override
    public float getShutdownProgress() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#loadLic()
     */
    @Override
    public void loadLic() {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#dataPointLimit()
     */
    @Override
    public Integer dataPointLimit() {
        return Integer.MAX_VALUE;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#scheduleShutdown(long, boolean, com.serotonin.m2m2.vo.User)
     */
    @Override
    public Thread scheduleShutdown(long timeout, boolean b, User user) {

        return null;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#isRestarting()
     */
    @Override
    public boolean isRestarting() {

        return false;
    }

}
