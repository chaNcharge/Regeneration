package me.fril.regeneration.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import me.fril.regeneration.debugger.IDebugChannel;

public class Scheduler {
	
	private final Map<TimerChannel, ScheduledTask> schedule = new ConcurrentHashMap<>();
	private final IDebugChannel debugChannel;
	
	private long currentTick = 0;
	
	public Scheduler(IDebugChannel debugChannel) {
		this.debugChannel = debugChannel;
	}
	
	public void tick() {
		currentTick++;
		debugChannel.update(currentTick);
		
		//iterator needed because we modify the collection while iterating through it
		Iterator<Entry<TimerChannel, ScheduledTask>> it = schedule.entrySet().iterator();
		while (it.hasNext()) {
			Entry<TimerChannel, ScheduledTask> en = it.next();
			
			if (en.getValue().scheduledTick == currentTick) {
				debugChannel.notifyExecution(en.getKey(), currentTick);
				
				en.getValue().run();
				it.remove();
			}
		}
		
	}
	
	
	
	public void scheduleInTicks(TimerChannel channel, long inTicks, Runnable callback) {
		if (schedule.get(channel) != null && schedule.get(channel).ticksLeft() >= 0)
			throw new IllegalStateException("Overwriting non-completed action on channel "+channel+" (old: "+schedule.get(channel)+", new would be in "+inTicks+")");
		
		if (inTicks >= 0) {
			ScheduledTask task = new ScheduledTask(channel, callback, currentTick+inTicks);
			schedule.put(channel, task);
			debugChannel.notifySchedule(channel, inTicks, task.scheduledTick);
		} else {
			schedule.remove(channel);
			debugChannel.notifyScheduleBlank(channel);
		}
	}
	
	public void scheduleInSeconds(TimerChannel channel, long inSeconds, Runnable callback) {
		scheduleInTicks(channel, inSeconds*20, callback);
	}
	
	
	
	public void cancel(TimerChannel channel) {
		if (schedule.containsKey(channel)) {
			debugChannel.notifyCancel(channel, schedule.get(channel).ticksLeft(), schedule.get(channel).scheduledTick);
			schedule.get(channel).cancel();
		} else {
			debugChannel.warn("Cancelling non-scheduled action on "+channel);
		}
	}
	
	public long getTicksLeft(TimerChannel channel) {
		return schedule.containsKey(channel) ? schedule.get(channel).ticksLeft() : -1;
	}
	
	public void reset() {
		currentTick = 0;
		schedule.clear();
		debugChannel.update(currentTick);
	}
	
	
	@Deprecated
	public Map<TimerChannel, ScheduledTask> getSchedule() {
		return schedule;
	}
	
	@Deprecated
	public long getCurrentTick() {
		return currentTick;
	}
	
	
	
	
	
	
	public class ScheduledTask implements Runnable {
		
		private final TimerChannel channel;
		private final Runnable callback;
		private final long scheduledTick;
		private boolean canceled;
		
		private ScheduledTask(TimerChannel channel, Runnable callback, long scheduledTick) {
			this.channel = channel;
			this.callback = callback;
			this.scheduledTick = scheduledTick;
		}
		
		@Override
		public void run() {
			if (canceled)
				throw new IllegalStateException("Running cancelled action: "+this);
			
			callback.run();
		}
		
		public void cancel() {
			canceled = true;
			
			if (scheduledTick - Scheduler.this.currentTick > 0) //still running
				Scheduler.this.schedule.remove(channel);
		}
		
		public long ticksLeft() {
			return canceled ? -1 : scheduledTick - Scheduler.this.currentTick;
		}
		
		
		@Deprecated
		public long scheduledTick() {
			return scheduledTick;
		}
		
		@Deprecated
		public boolean isCancelled() {
			return canceled;
		}
		
		
		@Override
		public String toString() {
			return "ScheduledTask[channel=" + channel + ", scheduledTick=" + scheduledTick + ", ticksLeft=" + ticksLeft() + ", canceled=" + canceled + "]";
		}
		
		
	}
	
}
