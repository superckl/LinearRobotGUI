package lab.gilroy.robot.gui.task;

import java.util.ArrayList;
import java.util.List;

public abstract class IPlantTask implements Runnable{

	protected final List<ITaskListener> listeners = new ArrayList<>();

	/**
	 * Registers a new listener to receive state updates.
	 * @param listener The listener to register.
	 */
	public void registerListener(final ITaskListener listener){
		this.listeners.add(listener);
	}

	protected void notifyListeners(){
		for(final ITaskListener listener:this.listeners)
			listener.taskCallback(this);
	}

	/**
	 * Called when a response is received from the connected Arduino.
	 * @param response The response received.
	 */
	public void responseReceived(final String response){};

	public abstract String getStatus();

	public abstract boolean isFinished();

	public abstract int getCurrentPlant();

	public abstract int getNumPlants();

}
