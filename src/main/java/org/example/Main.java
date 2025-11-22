package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Recognizer;
import org.vosk.Model;


import javax.sound.sampled.*;

public class Main extends Application {

    private static final String MODEL_PATH = "src/main/resources/model/vosk-model-ru-0.42"; // Убедитесь, что путь правильный
    private TextArea textArea;
    private boolean isRecording = false;
    private TargetDataLine line;
    private Model model;
    private Recognizer recognizer;
    private Button btn;

    @Override
    public void start(Stage primaryStage) {
        LibVosk.setLogLevel(LogLevel.INFO);

        btn = new Button("Start Recording");
        textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: 'Segoe UI';");
        VBox root = new VBox(btn, textArea);
        Scene scene = new Scene(root, 600, 400);

        btn.setOnAction(e -> toggleRecording());

        primaryStage.setTitle("Vosk Speech Recognition");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (isRecording) {
                stopRecording();
            }
        });
        primaryStage.show();
    }

    private void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            if (model == null) {
                model = new Model(MODEL_PATH);
            }
            recognizer = new Recognizer(model, 16000.0f);

            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            isRecording = true;
            btn.setText("Stop Recording");

            new Thread(this::processAudio).start();

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> textArea.appendText("Error: " + e.getMessage() + "\n"));
        }
    }

    private void processAudio() {
        byte[] buffer = new byte[4096];
        try {
            while (isRecording) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String resultJson = recognizer.getResult();
                        System.out.println("Raw result (String): " + resultJson);

                        // Логируем байты JSON
                        byte[] jsonBytes = resultJson.getBytes("ISO-8859-1"); // Это "как JVM видит строку"
                        System.out.println("Raw result (Bytes as ISO-8859-1): " + java.util.Arrays.toString(jsonBytes));

                        // Попробуем прочитать как UTF-8
                        String asUTF8 = new String(jsonBytes, "UTF-8");
                        System.out.println("Raw result (as UTF-8): " + asUTF8);

                        String recognizedText = parseResult(asUTF8); // Передаём строку, прочитанную как UTF-8

                        if (recognizedText != null && !recognizedText.isEmpty()) {
                            Platform.runLater(() -> {
                                textArea.appendText("Результат: " + recognizedText + "\n");
                            });
                        }
                    } else {
                        String partialResult = recognizer.getPartialResult();
                        System.out.println("Raw partial: " + partialResult);

                        String partialText = parsePartialResult(partialResult);
                        if (!partialText.isEmpty()) {
                            System.out.println("Partial: " + partialText);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> textArea.appendText("Processing error: " + e.getMessage() + "\n"));
        }
    }

    private String parseResult(String resultJson) {
        try {
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(resultJson, JsonObject.class);
            String text = json.get("text").getAsString();
            System.out.println("Raw JSON text (Gson): [" + text + "]");
            System.out.println("Raw first char (int): " + (int) text.charAt(0));

            // Попробуем исправить кодировку, если нужно
            String fixed = fixEncodingIfNecessary(text);
            System.out.println("Fixed text (Gson): [" + fixed + "]");
            System.out.println("Fixed first char (int): " + (int) fixed.charAt(0));

            return fixed;
        } catch (Exception e) {
            System.err.println("Error parsing JSON with Gson: " + e.getMessage());
            return "";
        }
    }

    private String parsePartialResult(String partialResult) {
        try {
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(partialResult, JsonObject.class);
            return json.get("partial").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private String fixEncodingIfNecessary(String garbledText) {
        // Проверим, содержит ли строка "ёлочки"
        if (garbledText != null && garbledText.matches(".*[Р-ЯЁ][А-яё].*")) {
            try {
                byte[] bytes = garbledText.getBytes("ISO-8859-1");
                return new String(bytes, "UTF-8");
            } catch (Exception e) {
                return garbledText;
            }
        }
        return garbledText;
    }
    private void stopRecording() {
        isRecording = false;
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (recognizer != null) {
            recognizer.close();
        }
        Platform.runLater(() -> btn.setText("Start Recording"));
    }

    @Override
    public void stop() {
        if (isRecording) {
            stopRecording();
        }
        if (model != null) {
            model.close();
        }
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        System.out.println("System encoding: " + System.getProperty("file.encoding"));
        System.out.println("Default charset: " + java.nio.charset.Charset.defaultCharset());
        launch(args);
    }
}