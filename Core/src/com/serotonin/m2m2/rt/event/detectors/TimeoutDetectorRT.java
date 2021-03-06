/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.rt.event.detectors;

import java.util.Date;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.util.timeout.TimeoutClient;
import com.serotonin.m2m2.util.timeout.TimeoutTask;
import com.serotonin.m2m2.vo.event.detector.TimeoutDetectorVO;
import com.serotonin.timer.TimerTask;

/**
 * This class is a base class for detectors that need to schedule timeouts for their operation. Subclasses may use
 * schedules for timeouts that make them active, or that make them inactive.
 * 
 * @author Matthew Lohbihler
 */
abstract public class TimeoutDetectorRT<T extends TimeoutDetectorVO<T>> extends PointEventDetectorRT<T> {
    /**
	 * @param vo
	 */
	public TimeoutDetectorRT(T vo) {
		super(vo);
		timeoutClient = new TimeoutClient(){

			@Override
			public void scheduleTimeout(long fireTime) {
				scheduleTimeoutImpl(fireTime);
		        task = null;
			}

			/* (non-Javadoc)
			 * @see com.serotonin.m2m2.util.timeout.TimeoutClient#getTaskId()
			 */
			@Override
			public String getTaskId() {
				//Watch use of XIDs as they are only enforced to be unique with a source id
				//return "TED-" + this.vo.getXid() + "-" + this.vo.getSourceId();
				return "TED-" + Integer.toString(vo.hashCode());
			}
			
			@Override
			public String getThreadName() {
				return getThreadNameImpl();
			}
			
		};
	}

	/**
     * Internal configuration field. The millisecond version of the duration fields.
     */
    private long durationMS;

    /**
     * Internal configuration field. The human-readable description of the duration fields.
     */
    private TranslatableMessage durationDescription;

    /**
     * My Timeout Client
     */
    private TimeoutClient timeoutClient;
    
    /**
     * Internal configuration field. The unique name for this event producer to be used in the scheduler (if required).
     */
    private TimerTask task;

    @Override
    public void initialize() {
        durationMS = Common.getMillis(vo.getDurationType(), vo.getDuration());
        durationDescription = vo.getDurationDescription();

        super.initialize();
    }

    protected boolean isJobScheduled() {
        return task != null;
    }

    @Override
    public void terminate() {
        super.terminate();
        cancelTask();
    }

    protected TranslatableMessage getDurationDescription() {
        return durationDescription;
    }

    protected long getDurationMS() {
        return durationMS;
    }

    protected void scheduleJob(long timeout) {
        if (task != null)
            cancelTask();
        task = new TimeoutTask(new Date(timeout), this.timeoutClient);
    }

    protected void unscheduleJob() {
        cancelTask();
    }

    abstract protected void scheduleTimeoutImpl(long fireTime);

    /**
     * Get the name of the Thread for Tracking/Reporting
     * @return
     */
    abstract protected String getThreadNameImpl();
	
    synchronized private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
