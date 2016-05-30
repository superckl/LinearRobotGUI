package lab.gilroy.robot.gui;

public class Config {

	public enum PhotoMode{
		INTERVAL;
	}

	public static String port = "";
	public static final PhotoMode modeDefault = PhotoMode.INTERVAL;
	public static PhotoMode mode = Config.modeDefault;
	public static final String arduinoCallback = "done";
	public static final double distanceToPlantDefault = 29.4;
	public static double distanceToPlant = Config.distanceToPlantDefault;
	public static final long timePerPlantDefault = 224320L+11790L;
	public static long timePerPlant = Config.timePerPlantDefault;

}
