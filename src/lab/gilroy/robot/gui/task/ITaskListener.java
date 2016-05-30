package lab.gilroy.robot.gui.task;

public interface ITaskListener {

	/**
	 * Called when a task this listener is registered has an important update. The update depends on the task.
	 * @param task The task that has updated.
	 */
	public void taskCallback(IPlantTask task);

}
