package com.serotonin.timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * The simulation timer is a single threaded timer under the temporal control of the next and fastForward methods. Tasks
 * are run in the same thread as the timer, so they will seem to complete instantly. Running them in an executor has the
 * opposite effect of making them appear to take an awful long time to complete.
 * 
 * @author Matthew Lohbihler
 */
public class SimulationTimer extends AbstractTimer {
    private final List<TimerTask> queue = new ArrayList<TimerTask>();
    private boolean cancelled;
    private ExecutorService executorService;
    private SimulationTimeSource timeSource = new SimulationTimeSource();
    
    @Override
    public boolean isInitialized() {
        return true;
    }

    public void setStartTime(long startTime) {
        timeSource.setTime(startTime);
    }

    public void next() {
        fastForwardTo(timeSource.currentTimeMillis() + 1);
    }

    public void fastForwardTo(long time) {
        while (!queue.isEmpty() && queue.get(0).trigger.nextExecutionTime <= time) {
            TimerTask task = queue.get(0);

            timeSource.setTime(task.trigger.nextExecutionTime);

            if (task.state == TimerTask.CANCELLED) {
                synchronized(this) {
                    queue.remove(0);
                }
            }else {
                long next = task.trigger.calculateNextExecutionTime();
                if (next <= 0) { // Non-repeating, remove
                    synchronized(this) {
                        queue.remove(0);
                    }
                    task.state = TimerTask.EXECUTED;
                }
                else {
                    // Repeating task, reschedule
                    task.trigger.nextExecutionTime = next;
                    synchronized(this) {
                        updateQueue();
                    }
                }

                task.runTask(task.trigger.mostRecentExecutionTime());
                
            }
        }

        timeSource.setTime(time);
    }


    @Override
    protected void scheduleImpl(TimerTask task) {
        if (cancelled)
            throw new IllegalStateException("Timer already cancelled.");

        if (task.state == TimerTask.CANCELLED || task.state == TimerTask.EXECUTED)
            throw new IllegalStateException("Task already executed or cancelled");

        if (task.state == TimerTask.VIRGIN) {
            long time = task.trigger.getFirstExecutionTime();
            if (time < 0)
                throw new IllegalArgumentException("Illegal execution time.");

            task.trigger.nextExecutionTime = time;
            task.state = TimerTask.SCHEDULED;
        }
        synchronized(this) {
            queue.add(task);
            updateQueue();
        }
    }

    private void updateQueue() {
        Collections.sort(queue, new Comparator<TimerTask>() {
            @Override
            public int compare(TimerTask t1, TimerTask t2) {
                long diff = t1.trigger.nextExecutionTime - t2.trigger.nextExecutionTime;
                if (diff < 0)
                    return -1;
                if (diff == 0)
                    return 0;
                return 1;
            }
        });
    }

    @Override
    public List<TimerTask> cancel() {
        cancelled = true;
        List<TimerTask> tasks = getTasks();
        queue.clear();
        return tasks;
    }

    @Override
    public int purge() {
        int result = 0;

        for (int i = queue.size()-1; i >= 0; i--) {
            if (queue.get(i).state == TimerTask.CANCELLED) {
                queue.remove(i);
                result++;
            }
        }

        return result;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public List<TimerTask> getTasks() {
        return new ArrayList<TimerTask>(queue);
    }

    @Override
    public long currentTimeMillis() {
        return timeSource.currentTimeMillis();
    }

	/* (non-Javadoc)
	 * @see com.serotonin.timer.AbstractTimer#execute(com.serotonin.timer.OrderedRunnable, long)
	 */
	@Override
	public void execute(Task command) {
	    new Thread() {
	        /* (non-Javadoc)
	         * @see java.lang.Thread#run()
	         */
	        @Override
	        public void run() {
	            command.runTask(timeSource.currentTimeMillis());
	        }
	    }.start();
	}

    /* (non-Javadoc)
     * @see com.serotonin.timer.AbstractTimer#init()
     */
    @Override
    public void init() {
        this.executorService = new OrderedThreadPoolExecutor(0, 1000, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), false, timeSource);
    }

    /* (non-Javadoc)
     * @see com.serotonin.timer.AbstractTimer#init(java.util.concurrent.ExecutorService)
     */
    @Override
    public void init(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /* (non-Javadoc)
     * @see com.serotonin.timer.AbstractTimer#init(com.serotonin.timer.TimerThread)
     */
    @Override
    public void init(TimerThread timer) {
        
    }

    /* (non-Javadoc)
     * @see com.serotonin.timer.AbstractTimer#getExecutorService()
     */
    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }
    
    /* (non-Javadoc)
     * @see com.serotonin.timer.AbstractTimer#getTimeSource()
     */
    @Override
    public TimeSource getTimeSource() {
        return timeSource;
    }
}
