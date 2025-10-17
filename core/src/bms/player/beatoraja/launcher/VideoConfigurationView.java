package bms.player.beatoraja.launcher;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.MainLoader;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.Resolution;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.util.Pair;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

public class VideoConfigurationView implements Initializable {
	@FXML
	private ComboBox<Resolution> resolution;
	@FXML
	private ComboBox<Config.DisplayMode> displayMode;
	@FXML
	private ComboBox<String> bgaOp;
	@FXML
	private ComboBox<String> bgaExpand;

	@FXML
	private CheckBox vSync;

	@FXML
	private Spinner<Integer> maxFps;
	@FXML
	private Spinner<Integer> missLayerTime;

	@FXML
	private ComboBox<String> monitor;
	
	@FXML
	private Spinner<Double> hranInverseTime;
	@FXML
	private Spinner<Double> hranOffset;
	@FXML
	private Spinner<Double> jacksInverseTime;
	@FXML
	private Spinner<Double> jacksOffset;
	@FXML
	private Spinner<Double> murizaraInverseTime;
	@FXML
	private Spinner<Double> murizaraOffset;
	@FXML
	private Spinner<Double> csInverseTime;
	@FXML
	private Spinner<Double> csOffset;
	@FXML
	private Spinner<Integer> scratchReallocationThreshold;
	@FXML
	private Spinner<Integer> avoidLNFactor;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
		updateResolutions();

		displayMode.getItems().setAll(Config.DisplayMode.values());
		monitor.getItems().setAll(Arrays.stream(Lwjgl3ApplicationConfiguration.getMonitors()).map(monitor -> String.format("%s [%s, %s]", monitor.name, Integer.toString(monitor.virtualX), Integer.toString(monitor.virtualY))).toList());
    }

    public void update(Config config) {
		displayMode.setValue(config.getDisplaymode());
		resolution.setValue(config.getResolution());
		vSync.setSelected(config.isVsync());
		monitor.setValue(config.getMonitorName());
		bgaOp.getSelectionModel().select(config.getBga());
		bgaExpand.getSelectionModel().select(config.getBgaExpand());
		maxFps.getValueFactory().setValue(config.getMaxFramePerSecond());
	}

	public void updatePlayer(PlayerConfig player) {
		missLayerTime.getValueFactory().setValue(player.getMisslayerDuration());
		hranInverseTime.getValueFactory().setValue(player.getHranInverseTime());
		hranOffset.getValueFactory().setValue(player.getHranOffset());
		jacksInverseTime.getValueFactory().setValue(player.getJacksInverseTime());
		jacksOffset.getValueFactory().setValue(player.getJacksOffset());
		murizaraInverseTime.getValueFactory().setValue(player.getMurizaraInverseTime());
		murizaraOffset.getValueFactory().setValue(player.getMurizaraOffset());
		csInverseTime.getValueFactory().setValue(player.getCSInverseTime());
		csOffset.getValueFactory().setValue(player.getCSOffset());
		scratchReallocationThreshold.getValueFactory().setValue(player.getScratchReallocationThreshold());
		avoidLNFactor.getValueFactory().setValue(player.getAvoidLNFactor());
	}

	public void commit(Config config) {
		config.setResolution(resolution.getValue());
		config.setDisplaymode(displayMode.getValue());
		config.setVsync(vSync.isSelected());
		config.setMonitorName(monitor.getValue());
		config.setBga(bgaOp.getSelectionModel().getSelectedIndex());
		config.setBgaExpand(bgaExpand.getSelectionModel().getSelectedIndex());
		config.setMaxFramePerSecond(maxFps.getValue());
	}

	public void commitPlayer(PlayerConfig player) {
		player.setMisslayerDuration(missLayerTime.getValue());
		player.setHranInverseTime(hranInverseTime.getValue());
		player.setHranOffset(hranOffset.getValue());
		player.setJacksInverseTime(jacksInverseTime.getValue());
		player.setJacksOffset(jacksOffset.getValue());
		player.setMurizaraInverseTime(murizaraInverseTime.getValue());
		player.setMurizaraOffset(murizaraOffset.getValue());
		player.setCSInverseTime(csInverseTime.getValue());
		player.setCSOffset(csOffset.getValue());
		player.setScratchReallocationThreshold(scratchReallocationThreshold.getValue());
		player.setAvoidLNFactor(avoidLNFactor.getValue());
	}

	@FXML
	public void updateResolutions() {
		Resolution oldValue = resolution.getValue();
		resolution.getItems().clear();

		if (displayMode.getValue() == Config.DisplayMode.FULLSCREEN) {
			Graphics.DisplayMode[] displays = MainLoader.getAvailableDisplayMode();
			for(Resolution r : Resolution.values()) {
				for(Graphics.DisplayMode display : displays) {
					if(display.width == r.width && display.height == r.height) {
						resolution.getItems().add(r);
						break;
					}
				}
			}
		} else {
			Graphics.DisplayMode display = MainLoader.getDesktopDisplayMode();
			for(Resolution r : Resolution.values()) {
				if (r.width <= display.width && r.height <= display.height) {
					resolution.getItems().add(r);
				}
			}
		}
		resolution.setValue(resolution.getItems().contains(oldValue)
				? oldValue : resolution.getItems().get(resolution.getItems().size() - 1));
	}
}
