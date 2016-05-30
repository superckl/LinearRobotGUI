package lab.gilroy.robot.gui;

import java.util.concurrent.atomic.AtomicBoolean;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import lab.gilroy.robot.gui.task.IPlantTask;


public class RobotController implements SerialPortEventListener{

	private final GuiController gui;
	private final SerialPort port;
	private final AtomicBoolean runningCommand = new AtomicBoolean(false);
	private IPlantTask runningTask;

	public RobotController(final GuiController controller, final String portName) {
		this.gui = controller;
		SerialPort port = null;
		try {
			port = new SerialPort(Config.port);
			if(port.openPort()){
				port.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				port.addEventListener(this);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		this.port = port;
	}

	public boolean isConnected(){
		return this.port != null && this.port.isOpened();
	}

	public void close(){
		try {
			synchronized (this.port) {
				if (this.port != null)
					this.port.closePort();
			}
		} catch (final SerialPortException e) {
			e.printStackTrace();
		}
	}

	private StringBuilder buffer = new StringBuilder(5);

	@Override
	public void serialEvent(final SerialPortEvent serialPortEvent) {
		if(serialPortEvent.isRXCHAR())
			try {
				synchronized (this.port) {
					final String in = this.port.readString().trim();
					this.buffer.append(in);
					System.out.println(this.buffer.toString());
					if(this.buffer.length() > 0 && this.buffer.charAt(this.buffer.length()-1) == '|'){
						this.buffer.deleteCharAt(this.buffer.length()-1);
						final String read = this.buffer.toString();
						if(read.equals(Config.arduinoCallback))
							this.runningCommand.set(false);
						if(this.runningTask != null)
							this.runningTask.responseReceived(read);
						this.buffer = new StringBuilder(5);
					}
				}
			} catch (final SerialPortException e) {
				e.printStackTrace();
			}

	}

	public void startTask(final IPlantTask task){
		new Thread(task).start();
		this.runningTask = task;
		this.runningTask.registerListener(this.gui);
	}

	public boolean goHome(){
		synchronized (this.port) {
			try {
				this.port.writeString("home");
				this.runningCommand.set(true);
				return true;
			} catch (final SerialPortException e) {
				e.printStackTrace();
				return false;
			}
		}
	}

	public boolean move(final double distance){
		synchronized (this.port) {
			try {
				this.port.writeString("m"+Double.toString(distance));
				this.runningCommand.set(true);
				return true;
			} catch (final SerialPortException e) {
				e.printStackTrace();
				return false;
			}
		}
	}

	public boolean takePicture(){
		synchronized (this.port) {
			try {
				this.port.writeString("takePic");
				this.runningCommand.set(true);
				return true;
			} catch (final SerialPortException e) {
				e.printStackTrace();
				return false;
			}
		}
	}

	public boolean isCommandRunning(){
		return this.runningCommand.get();
	}

	public IPlantTask getCurrentTask(){
		return this.runningTask;
	}

}
