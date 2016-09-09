package lab.gilroy.robot.gui.task;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lab.gilroy.robot.gui.Config;
import lab.gilroy.robot.gui.RobotController;

public class TaskIntervalPhotos extends IPlantTask{

	private final RobotController controller;
	private final int plants;
	private final long time;
	private final long startDelay;
	private final long delayBetween;
	private long startTime = -1L;
	private final AtomicInteger currentPlant = new AtomicInteger(0);
	private boolean moved = false;
	private boolean doneExecuting;
	private final AtomicReference<String> status = new AtomicReference<>("Not Started");
	private long startMoveTime;

	private final Object dummy = new Object();

	public TaskIntervalPhotos(final RobotController controller, final long timems, final int plants, final long delay, final long startDelay) {
		this.controller = controller;
		this.time = timems;
		this.plants = plants;
		this.delayBetween = delay;
		this.startDelay = startDelay;
	}

	@Override
	public void responseReceived(final String response) {
		super.responseReceived(response);
		if(this.doneExecuting)
			return;
		if(response.equals(Config.arduinoCallback))
			this.run();
	}

	@Override
	public void run() {
		if(this.doneExecuting)
			return;
		if(this.controller == null || !this.controller.isConnected())
			throw new IllegalStateException("No connection to Arduino!");
		if(this.startTime == -1L){
			this.startTime = System.currentTimeMillis();
			try {
				this.updateStatus("Waiting to start...");
				synchronized (this.dummy) {
					if (this.startDelay != 0)
						this.dummy.wait(this.startDelay);
				}
				this.updateStatus("Going home...");
				this.startMoveTime = System.currentTimeMillis();
				this.controller.goHome();
				this.moved = true;
				return;
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(System.currentTimeMillis()-this.startTime >= this.time){
			this.doneExecuting = true;
			this.updateStatus("Finished");
			return;
		}
		if(this.moved){
			if(this.delayBetween > 0)
				try {
					this.updateStatus("Waiting to take picture...");
					synchronized (this.dummy) {
						final long moveTime = (System.currentTimeMillis()-this.startMoveTime);
						if(this.delayBetween - moveTime > 0)
							this.dummy.wait(this.delayBetween - moveTime);
					}
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
			this.updateStatus("Taking picture...");
			this.controller.takePicture();
			this.moved = false;
		}else if(this.plants > 1 && this.currentPlant.get() == this.plants - 1){
			this.updateStatus("Going home...");
			this.controller.goHome();
			this.startMoveTime = System.currentTimeMillis();
			this.moved = true;
			this.currentPlant.set(0);
		}else if(this.plants > 1){
			this.updateStatus("Moving to next plant...");
			this.controller.move(Config.distanceToPlant);
			this.startMoveTime = System.currentTimeMillis();
			this.moved = true;
			this.currentPlant.getAndIncrement();
		}
	}

	@Override
	public String getStatus() {
		return this.status.get();
	}

	public void updateStatus(final String status){
		this.status.set(status);;
		super.notifyListeners();
	}

	@Override
	public boolean isFinished() {
		return this.doneExecuting;
	}

	@Override
	public int getCurrentPlant() {
		return this.currentPlant.get();
	}

	@Override
	public int getNumPlants() {
		return this.plants;
	}

}
