/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.rt;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.serotonin.log.LogStopWatch;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.MangoTestBase;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.AlarmLevels;
import com.serotonin.m2m2.rt.event.EventInstance;
import com.serotonin.m2m2.rt.event.UserEventCache;
import com.serotonin.m2m2.rt.event.type.DataPointEventType;
import com.serotonin.m2m2.rt.event.type.EventType;

/**
 * A benchmarking test to validate changes to the UserEventCache
 * 
 * 
 * @author Terry Packer
 *
 */
public class UserEventCacheTest extends MangoTestBase{
	
	//Settings
	static final int EVENT_COUNT = 10000;
	static final int USER_COUNT = 10;
	
	
	//Runtime 
	AtomicInteger runningThreads = new AtomicInteger(0);
	Object monitor = new Object();
	UserEventCache cache;
	//Signal to user threads that they should stop too
	AtomicBoolean generatorRunning = new AtomicBoolean(true);
	
    @Before
    @Override
    public void before() {
        super.before();
    }

	/**
	 * To simulate Mango we will have 1 thread generating events 
	 * and occasionally purging them while several other threads read their user's events out.
	 */
    //@Test(timeout = 30000)
	public void benchmark(){
		
		this.cache = new UserEventCache(15 * 60000,  60000);

		//Setup EventThread
		EventGeneratorThread egt = new EventGeneratorThread(this);
		
		//Setup User Threads
		List<UserThread> userThreads = new ArrayList<UserThread>();
		for(int i=0; i<USER_COUNT; i++){
			userThreads.add(new UserThread(i, this));
		}
		
		LogStopWatch timer = new LogStopWatch();
		
		//Start User Threads
		for(UserThread ut : userThreads)
			ut.start();
		
		//Start Event Thread
		egt.start();
		
		try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
		
		while(runningThreads.intValue() > 0){
			synchronized(monitor){
				try {
					monitor.wait();
				} catch (InterruptedException e) {
					fail(e.getMessage());
				}
			}
		}
		timer.stop("");
	}
	
	class EventGeneratorThread extends Thread{
		
		private UserEventCacheTest parent;
		
		public EventGeneratorThread(UserEventCacheTest parent){
			super("Event Generator");
			this.parent = parent;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			parent.runningThreads.incrementAndGet();
			
			EventType eventType = new DataPointEventType(1, 1);
            TranslatableMessage message = new TranslatableMessage("common.default", "not a real alarm");
            Map<String, Object> context = new HashMap<String,Object>();
			
            List<EventInstance> allEvents = new ArrayList<EventInstance>();
			//Raise Events
			for(int i=0; i<EVENT_COUNT; i++){
				EventInstance e = new EventInstance(
						eventType,
						Common.timer.currentTimeMillis(), 
						true,
						AlarmLevels.CRITICAL,
						message,
						context);
				//Important
				e.setId(i);
				allEvents.add(e);
				for(int userId=0; userId<USER_COUNT; userId++)
					parent.cache.addEvent(userId, e);
			}
			
			//Return them to normal
			for(EventInstance e : allEvents){
				e.setAcknowledgedByUserId(1);
				e.setAcknowledgedTimestamp(Common.timer.currentTimeMillis());
				for(int userId=0; userId<USER_COUNT; userId++)
					parent.cache.updateEvent(userId, e);
			}
			
			//Randomly remove some, simulate acknowledge
			for(EventInstance e : allEvents){
				if(Math.random() > 0.5d)
					for(int userId=0; userId<USER_COUNT; userId++)
						parent.cache.updateEvent(userId, e);
			}
			
			
			synchronized(parent.monitor){
				parent.runningThreads.decrementAndGet();
				parent.monitor.notify();
				parent.generatorRunning.set(false);
			}
			
		}
	}
	
	class UserThread extends Thread{
		
		private UserEventCacheTest parent;
		private Integer userId;
		
		public UserThread(Integer userId, UserEventCacheTest parent){
			super("User Thread " + userId);
			this.parent = parent;
			this.userId = userId;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			parent.runningThreads.incrementAndGet();
			
			while(parent.generatorRunning.get() == true){
				
				//Get our events events
				parent.cache.getAllEvents(userId);
				
				//Purge some 
				parent.cache.purgeEventsBefore(Common.timer.currentTimeMillis() - 100);
			}
			
			//Purge all events
			parent.cache.purgeAllEvents();

			
			synchronized(parent.monitor){
				parent.runningThreads.decrementAndGet();
				parent.monitor.notify();
			}
			
		}
	}

}
