package lab.gilroy.robot.gui;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import jssc.SerialPortList;
import lab.gilroy.robot.gui.Config.PhotoMode;
import lab.gilroy.robot.gui.task.IPlantTask;
import lab.gilroy.robot.gui.task.ITaskListener;
import lab.gilroy.robot.gui.task.TaskIntervalPhotos;

public class GuiController extends Application implements ITaskListener{

	private Label connectingLabel;
	private Label statusLabel;
	private GridPane imageGrid;
	private RobotController controller;
	private Stage mainStage;
	private Image plantImage;
	private Image plantGreyImage;
	private Image plantOutlineImage;

	private final List<ImageView> plantImages = new ArrayList<>();
	private final List<ImageView> plantGreyImages = new ArrayList<>();
	private final List<ImageView> plantOutlineImages = new ArrayList<>();

	private static Log log = new Log();
	public static Logger logger = new Logger(GuiController.log, "");


	public static void main(final String ... args){
		Application.launch(args);
	}

	@Override
	public void start(final Stage stage) throws Exception {
		//Construct Log
		final LogView logView = new LogView(GuiController.logger);
		logView.setPrefWidth(400);

		final ChoiceBox<Level> filterLevel = new ChoiceBox<>(
				FXCollections.observableArrayList(
						Level.values()
						)
				);
		filterLevel.getSelectionModel().select(Level.DEBUG);
		logView.filterLevelProperty().bind(
				filterLevel.getSelectionModel().selectedItemProperty()
				);

		final ToggleButton showTimestamp = new ToggleButton("Show Timestamp");
		logView.showTimeStampProperty().bind(showTimestamp.selectedProperty());

		final ToggleButton tail = new ToggleButton("Tail");
		logView.tailProperty().bind(tail.selectedProperty());

		final ToggleButton pause = new ToggleButton("Pause");
		logView.pausedProperty().bind(pause.selectedProperty());

		final Slider rate = new Slider(0.1, 60, 60);
		logView.refreshRateProperty().bind(rate.valueProperty());
		final Label rateLabel = new Label();
		rateLabel.textProperty().bind(Bindings.format("Update: %.2f fps", rate.valueProperty()));
		rateLabel.setStyle("-fx-font-family: monospace;");
		final VBox rateLayout = new VBox(rate, rateLabel);
		rateLayout.setAlignment(Pos.CENTER);

		final HBox controls = new HBox(
				10,
				filterLevel,
				showTimestamp,
				tail,
				pause,
				rateLayout
				);
		controls.setMinHeight(Region.USE_PREF_SIZE);

		final VBox layout = new VBox(
				10,
				controls,
				logView
				);
		VBox.setVgrow(logView, Priority.ALWAYS);

		final Scene logScene = new Scene(layout);
		logScene.getStylesheets().add(
				this.getClass().getResource("/log-view.css").toExternalForm()
				);
		final Stage logStage = new Stage();
		logStage.setScene(logScene);
		logStage.show();
		//Construct main GUI
		GuiController.logger.info("Constructing GUI...");
		stage.setTitle("Gilroy Lab Linear Robot Controller");
		this.plantImage = new Image("/plant.png", 100, 100, true, true);
		this.plantGreyImage = new Image("/plant_grey.png", 100, 100, true, true);
		this.plantOutlineImage = new Image("/plant_outline.png", 100, 100, true, true);
		for(int i = 0; i < 6; i++){
			this.plantImages.add(new ImageView(this.plantImage));
			this.plantGreyImages.add(new ImageView(this.plantGreyImage));
			this.plantOutlineImages.add(new ImageView(this.plantOutlineImage));
		}
		stage.getIcons().add(this.plantImage);
		final Scene scene = new Scene(new VBox(), 500, 120);
		final VBox box = new VBox();
		box.setAlignment(Pos.CENTER);
		box.setSpacing(10D);
		box.setPadding(new Insets(0, 10, 0, 10));
		final Label label = new Label("Please ensure the correct port is set in the Settings before attempting a connection.");
		final Button connect = new Button("Connect to Arduino");
		this.connectingLabel = new Label("Connecting...");
		this.connectingLabel.setVisible(false);
		connect.setOnAction((final ActionEvent e) -> {
			this.connectingLabel.setText("Connecting...");
			this.connectingLabel.setTextFill(Color.BLACK);
			this.connectingLabel.setVisible(true);
			final RobotController cont = new RobotController(this, "COM"+Config.port);
			if(cont.isConnected()){
				System.out.println("connected");
				this.connectingLabel.setText("Connected");
				this.connectingLabel.setVisible(true);
				this.controller = cont;
				//TODO various stages
				final Stage newStage = this.constructIntervalGUI();
				newStage.show();
				this.mainStage.close();
				this.mainStage = newStage;
			}else{
				this.connectingLabel.setText("Failed. Ensure the correct port is selected and nothing else is using it.");
				this.connectingLabel.setTextFill(Color.RED);
				this.connectingLabel.setVisible(true);
			}
		});
		box.getChildren().addAll(label, connect, this.connectingLabel);
		((VBox) scene.getRoot()).getChildren().addAll(this.constructMenu(), box);
		stage.setScene(scene);
		stage.show();
		this.mainStage = stage;
	}

	private MenuBar constructMenu(){
		GuiController.logger.debug("Constructing menu...");
		final MenuBar menu = new MenuBar();
		final Menu file = new Menu("File");
		final MenuItem item1 = new MenuItem("Settings");
		item1.setOnAction((final ActionEvent e) -> {this.constructSettingsGUI().show();});
		final MenuItem item2 = new MenuItem("Calculator");
		item2.setOnAction((final ActionEvent e) -> {this.constructCalculatorGUI().show();});
		//TODO Temporary implementation
		final MenuItem item3 = new MenuItem("Reset GUI");
		item3.setOnAction((final ActionEvent e) -> {
			final Stage stage = this.constructIntervalGUI();
			stage.show();
			this.mainStage.close();
			this.mainStage = stage;});
		if(this.controller == null)
			item3.setDisable(true);
		file.getItems().addAll(item1, item2, item3);
		menu.getMenus().addAll(file);

		return menu;
	}

	private Stage constructCalculatorGUI(){
		GuiController.logger.debug("Constructing calc gui...");
		final Stage calc = new Stage();
		calc.setTitle("Calculator");
		calc.getIcons().add(this.plantImage);
		calc.setScene(new Scene(new VBox(), 450, 250));
		final GridPane grid = new GridPane();
		grid.setPadding(new Insets(10, 10, 10, 10));
		grid.setVgap(5);
		grid.setHgap(5);
		grid.setAlignment(Pos.CENTER_LEFT);

		return calc;
	}

	private Stage constructSettingsGUI(){
		GuiController.logger.debug("Constructing setting gui...");
		final Stage settings = new Stage();
		settings.setTitle("Settings");
		settings.getIcons().add(this.plantImage);
		settings.setScene(new Scene(new VBox(), 450, 250));
		final GridPane grid = new GridPane();
		grid.setPadding(new Insets(10, 10, 10, 10));
		grid.setVgap(5);
		grid.setHgap(5);
		grid.setAlignment(Pos.CENTER_LEFT);

		final Label label1 = new Label("Port:");
		final ObservableList<String> options = FXCollections.observableArrayList(SerialPortList.getPortNames());
		final ComboBox<String> comboBox = new ComboBox<String>(options);
		comboBox.setOnAction((final ActionEvent e) -> {
			Config.port = ((ComboBox<String>)e.getSource()).getValue();
		});
		comboBox.setValue(Config.port);
		comboBox.setMaxWidth(Double.MAX_VALUE);
		final Label label2 = new Label("(e.g. COM9)");

		final Label label3 = new Label("Mode:");
		final ObservableList<PhotoMode> options2 = FXCollections.observableArrayList(Config.PhotoMode.values());
		final ComboBox<PhotoMode> comboBox2 = new ComboBox<PhotoMode>(options2);
		comboBox2.setOnAction((final ActionEvent e) -> {
			Config.mode = ((ComboBox<PhotoMode>)e.getSource()).getValue();
		});
		comboBox2.setValue(Config.mode);
		comboBox2.setMaxWidth(Double.MAX_VALUE);
		final Label label4 = new Label("(Photo timing mode.)");

		final Label label5 = new Label("Plant Dist:");
		final TextField text = new TextField(Double.toString(Config.distanceToPlant));
		text.textProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*(.\\d*)?"))
				text.setText(oldValue);
			else{
				final double val = Double.parseDouble(newValue);
				Config.distanceToPlant = val;
			}
		});
		text.setMaxWidth(Double.MAX_VALUE);
		final Label label6 = new Label("(Distance between plants.)");

		grid.add(label1, 0, 0);
		grid.add(comboBox, 1, 0);
		grid.add(label2, 2, 0);
		grid.add(label3, 0, 1);
		grid.add(comboBox2, 1, 1);
		grid.add(label4, 2, 1);
		grid.add(label5, 0, 2);
		grid.add(text, 1, 2);
		grid.add(label6, 2, 2);

		((VBox) settings.getScene().getRoot()).getChildren().addAll(this.constructMenu(), grid);
		return settings;
	}

	public Stage constructIntervalGUI(){
		GuiController.logger.debug("Constructing interval gui");
		final Stage intervalStage = new Stage();
		intervalStage.getIcons().add(this.plantImage);
		intervalStage.setTitle("Time Interval Photos");
		intervalStage.setScene(new Scene(new VBox(), 800, 400));

		final GridPane mainGrid = new GridPane();
		mainGrid.setPadding(new Insets(10, 10, 10, 10));
		mainGrid.setVgap(5);
		mainGrid.setHgap(5);

		final GridPane imageGrid = new GridPane();
		this.imageGrid = imageGrid;
		imageGrid.setAlignment(Pos.CENTER);
		imageGrid.setPadding(new Insets(10, 10, 10, 10));
		imageGrid.setVgap(5);
		imageGrid.setHgap(10);

		for(int i = 0; i < 6; i++)
			imageGrid.add(this.plantImages.get(i), i, 0);

		final Button startButton = new Button("Start");

		final Label label1 = new Label("Plants:");
		final TextField field1 = new TextField();
		field1.setText("6");
		field1.textProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*"))
				field1.setText(oldValue);
			else if(!newValue.isEmpty()){
				final int value = Integer.parseInt(newValue);
				if(value > 6 || value == 0)
					field1.setText(oldValue);
				else{
					imageGrid.getChildren().clear();
					for(int i = 0; i < 6; i++)
						if(i < value)
							imageGrid.add(GuiController.this.plantImages.get(i), i, 0);
						else
							imageGrid.add(GuiController.this.plantGreyImages.get(i), i, 0);
				}
			}
		});

		final Label label2 = new Label("Duration:");
		final TextField field2 = new TextField();
		field2.textProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*"))
				field2.setText(oldValue);
		});
		final ObservableList<TimeUnit> options2 = FXCollections.observableArrayList(TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS);
		final ComboBox<TimeUnit> comboBox2 = new ComboBox<TimeUnit>(options2);
		comboBox2.setValue(TimeUnit.MINUTES);

		final Label label3 = new Label("Start Delay:");
		final TextField field3 = new TextField("0");
		field3.textProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*"))
				field3.setText(oldValue);
		});
		final ObservableList<TimeUnit> options3 = FXCollections.observableArrayList(TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS);
		final ComboBox<TimeUnit> comboBox3 = new ComboBox<TimeUnit>(options3);
		comboBox3.setValue(TimeUnit.MINUTES);

		final Label label4 = new Label("Plant Delay:");
		final TextField field4 = new TextField("0");
		field4.textProperty().addListener((ChangeListener<String>) (observable, oldValue, newValue) -> {
			if (!newValue.matches("\\d*"))
				field4.setText(oldValue);
		});
		final ObservableList<TimeUnit> options4 = FXCollections.observableArrayList(TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS);
		final ComboBox<TimeUnit> comboBox4 = new ComboBox<TimeUnit>(options4);
		comboBox4.setValue(TimeUnit.MINUTES);

		startButton.setOnAction((final ActionEvent e) ->{
			if(field4.getText().isEmpty() || field3.getText().isEmpty() || field2.getText().isEmpty() || field1.getText().isEmpty())
				return; //TODO should disable button;
			this.controller.startTask(new TaskIntervalPhotos(this.controller, comboBox2.getValue().toMillis(Long.parseLong(field2.getText())),
					Integer.parseInt(field1.getText()), comboBox4.getValue().toMillis(Integer.parseInt(field4.getText())),
					comboBox3.getValue().toMillis(Integer.parseInt(field3.getText()))));
			field1.setDisable(true);
			field2.setDisable(true);
			field3.setDisable(true);
			field4.setDisable(true);
			comboBox2.setDisable(true);
			comboBox3.setDisable(true);
			comboBox4.setDisable(true);
			startButton.setDisable(true);
		});

		this.statusLabel = new Label();

		final Button sortButton = new Button("Sort");
		sortButton.setPadding(new Insets(5));
		sortButton.setOnAction((final ActionEvent e) -> {
			if(field1.getText().isEmpty())
				return; //TODO should disable button;
			final File selectedDirectory = new DirectoryChooser().showDialog(intervalStage);
			if(selectedDirectory != null){
				final File[] files = selectedDirectory.listFiles((FilenameFilter) (dir, name) -> name.endsWith(".CR2"));
				final int numPlants = Integer.parseInt(field1.getText());
				for(int i = 1; i-1 < numPlants; i++){
					final File dir = new File(selectedDirectory, "plant"+i+"/");
					dir.mkdir();
				}
				int i = 1;
				for(final File file:files){
					try {
						System.out.println(file.getName()+"-"+i);
						Files.move(file.toPath(), Paths.get(new File(selectedDirectory, "plant"+i+"/").getAbsolutePath(), file.getName()), StandardCopyOption.REPLACE_EXISTING);
					} catch (final Exception e1) {
						e1.printStackTrace();
					}
					i %= numPlants;
					i++;
				}
			}
		});

		final Button estimateButton = new Button("Estimate");
		estimateButton.setOnAction((final ActionEvent e) -> {
			if(field4.getText().isEmpty() || field3.getText().isEmpty() || field2.getText().isEmpty() || field1.getText().isEmpty())
				return; //TODO should disable button;
			final long plantDelay = comboBox4.getValue().toMillis(Integer.parseInt(field4.getText()));
			if(plantDelay > 0 && plantDelay < Config.timePerPlant){
				this.statusLabel.setText("WARNING: Plant delay is less than estimated move time!");
				this.statusLabel.setTextFill(Color.RED);
				return;
			}
			final long perPlant = Integer.parseInt(field1.getText()) * Config.timePerPlant + plantDelay;
			final long duration = comboBox2.getValue().toMillis(Integer.parseInt(field2.getText()));
			final long startDelay = comboBox3.getValue().toMillis(Integer.parseInt(field3.getText()));
			if(perPlant > duration - startDelay){
				this.statusLabel.setText("WARNING: Duration is too short to complete imaging sequence!");
				this.statusLabel.setTextFill(Color.RED);
			}else{
				final int iterations = (int) ((duration-perPlant-startDelay) / (perPlant * 2D)) + 1;
				this.statusLabel.setText("Estimate: "+iterations+" image sequence iteration(s) will be completed.");
				this.statusLabel.setTextFill(Color.GREEN);
			}
		});

		mainGrid.add(label1, 0, 2);
		mainGrid.add(label2, 0, 3);
		mainGrid.add(label3, 0, 4);
		mainGrid.add(label4, 0, 5);
		mainGrid.add(field1, 1, 2);
		mainGrid.add(field2, 1, 3);
		mainGrid.add(field3, 1, 4);
		mainGrid.add(field4, 1, 5);
		mainGrid.add(comboBox2, 2, 3);
		mainGrid.add(comboBox3, 2, 4);
		mainGrid.add(comboBox4, 2, 5);
		mainGrid.add(startButton, 0, 6);
		mainGrid.add(estimateButton, 1, 6);
		mainGrid.add(sortButton, 2, 6);

		((VBox) intervalStage.getScene().getRoot()).getChildren().addAll(this.constructMenu(), imageGrid, mainGrid, this.statusLabel/*, sortButton*/);
		return intervalStage;
	}

	@Override
	public void stop() throws Exception{
		super.stop();
		if(this.controller != null)
			this.controller.close();
	}

	@Override
	public void taskCallback(final IPlantTask task) {
		Platform.runLater(() -> {
			if(this.statusLabel != null){
				this.statusLabel.setText(task.getStatus());
				this.statusLabel.setTextFill(Color.BLACK);
			}
			if(this.imageGrid != null){
				this.imageGrid.getChildren().clear();
				final int value = task.getNumPlants();
				final int current = task.getCurrentPlant();
				for(int i = 0; i < 6; i++)
					if(i == current)
						this.imageGrid.add(GuiController.this.plantOutlineImages.get(i), i, 0);
					else if(i < value)
						this.imageGrid.add(GuiController.this.plantImages.get(i), i, 0);
					else
						this.imageGrid.add(GuiController.this.plantGreyImages.get(i), i, 0);
			}
		});
	}

}

class Log {
	private static final int MAX_LOG_ENTRIES = 1_000_000;

	private final BlockingDeque<LogRecord> log = new LinkedBlockingDeque<>(Log.MAX_LOG_ENTRIES);

	public void drainTo(final Collection<? super LogRecord> collection) {
		this.log.drainTo(collection);
	}

	public void offer(final LogRecord record) {
		this.log.offer(record);
	}
}

class Logger {
	private final Log log;
	private final String context;

	public Logger(final Log log, final String context) {
		this.log = log;
		this.context = context;
	}

	public void log(final LogRecord record) {
		this.log.offer(record);
	}

	public void debug(final String msg) {
		this.log(new LogRecord(Level.DEBUG, this.context, msg));
	}

	public void info(final String msg) {
		this.log(new LogRecord(Level.INFO, this.context, msg));
	}

	public void warn(final String msg) {
		this.log(new LogRecord(Level.WARN, this.context, msg));
	}

	public void error(final String msg) {
		this.log(new LogRecord(Level.ERROR, this.context, msg));
	}

	public Log getLog() {
		return this.log;
	}
}

enum Level { DEBUG, INFO, WARN, ERROR }

class LogRecord {
	private final Date   timestamp;
	private final Level  level;
	private final String context;
	private final String message;

	public LogRecord(final Level level, final String context, final String message) {
		this.timestamp = new Date();
		this.level     = level;
		this.context   = context;
		this.message   = message;
	}

	public Date getTimestamp() {
		return this.timestamp;
	}

	public Level getLevel() {
		return this.level;
	}

	public String getContext() {
		return this.context;
	}

	public String getMessage() {
		return this.message;
	}
}

class LogView extends ListView<LogRecord> {
	private static final int MAX_ENTRIES = 10_000;

	private final static PseudoClass debug = PseudoClass.getPseudoClass("debug");
	private final static PseudoClass info  = PseudoClass.getPseudoClass("info");
	private final static PseudoClass warn  = PseudoClass.getPseudoClass("warn");
	private final static PseudoClass error = PseudoClass.getPseudoClass("error");

	private final static SimpleDateFormat timestampFormatter = new SimpleDateFormat("HH:mm:ss.SSS");

	private final BooleanProperty       showTimestamp = new SimpleBooleanProperty(false);
	private final ObjectProperty<Level> filterLevel   = new SimpleObjectProperty<>(null);
	private final BooleanProperty       tail          = new SimpleBooleanProperty(false);
	private final BooleanProperty       paused        = new SimpleBooleanProperty(false);
	private final DoubleProperty        refreshRate   = new SimpleDoubleProperty(60);

	private final ObservableList<LogRecord> logItems = FXCollections.observableArrayList();

	public BooleanProperty showTimeStampProperty() {
		return this.showTimestamp;
	}

	public ObjectProperty<Level> filterLevelProperty() {
		return this.filterLevel;
	}

	public BooleanProperty tailProperty() {
		return this.tail;
	}

	public BooleanProperty pausedProperty() {
		return this.paused;
	}

	public DoubleProperty refreshRateProperty() {
		return this.refreshRate;
	}

	public LogView(final Logger logger) {
		this.getStyleClass().add("log-view");

		final Timeline logTransfer = new Timeline(
				new KeyFrame(
						Duration.seconds(1),
						event -> {
							logger.getLog().drainTo(this.logItems);

							if (this.logItems.size() > LogView.MAX_ENTRIES)
								this.logItems.remove(0, this.logItems.size() - LogView.MAX_ENTRIES);

							if (this.tail.get())
								this.scrollTo(this.logItems.size());
						}
						)
				);
		logTransfer.setCycleCount(Animation.INDEFINITE);
		logTransfer.rateProperty().bind(this.refreshRateProperty());

		this.pausedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue && logTransfer.getStatus() == Animation.Status.RUNNING)
				logTransfer.pause();

			if (!newValue && logTransfer.getStatus() == Animation.Status.PAUSED && this.getParent() != null)
				logTransfer.play();
		});

		this.parentProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue == null)
				logTransfer.pause();
			else if (!this.paused.get())
				logTransfer.play();
		});

		this.filterLevel.addListener((observable, oldValue, newValue) -> {
			this.setItems(
					new FilteredList<LogRecord>(
							this.logItems,
							logRecord ->
							logRecord.getLevel().ordinal() >=
							this.filterLevel.get().ordinal()
							)
					);
		});
		this.filterLevel.set(Level.DEBUG);

		this.setCellFactory(param -> new ListCell<LogRecord>() {
			{
				LogView.this.showTimestamp.addListener(observable -> this.updateItem(this.getItem(), this.isEmpty()));
			}

			@Override
			protected void updateItem(final LogRecord item, final boolean empty) {
				super.updateItem(item, empty);

				this.pseudoClassStateChanged(LogView.debug, false);
				this.pseudoClassStateChanged(LogView.info, false);
				this.pseudoClassStateChanged(LogView.warn, false);
				this.pseudoClassStateChanged(LogView.error, false);

				if (item == null || empty) {
					this.setText(null);
					return;
				}

				final String context =
						(item.getContext() == null)
						? ""
								: item.getContext() + " ";

				if (LogView.this.showTimestamp.get()) {
					final String timestamp =
							(item.getTimestamp() == null)
							? ""
									: LogView.timestampFormatter.format(item.getTimestamp()) + " ";
					this.setText(timestamp + context + item.getMessage());
				} else
					this.setText(context + item.getMessage());

				switch (item.getLevel()) {
				case DEBUG:
					this.pseudoClassStateChanged(LogView.debug, true);
					break;

				case INFO:
					this.pseudoClassStateChanged(LogView.info, true);
					break;

				case WARN:
					this.pseudoClassStateChanged(LogView.warn, true);
					break;

				case ERROR:
					this.pseudoClassStateChanged(LogView.error, true);
					break;
				}
			}
		});
	}
}