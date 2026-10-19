package com.example.zooapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public class SensorActivity extends AppCompatActivity {

    public static final UUID ZOO_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Spinner spAnimals;
    private TextView tvAnimalRequirements, tvSimTemp, tvSimHum, tvSimCo2, tvAlertStatus, tvBtStatus, tvDbRecords;
    private Button btnSimulateNow;

    private DatabaseHelper dbHelper;
    private BluetoothServerSocket serverSocket;
    private boolean isServerRunning = true;

    private Animal[] catalogoAnimales;
    private Animal animalSeleccionado;

    private final Handler simHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private static final long ONE_HOUR_MS = 3600000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        dbHelper = new DatabaseHelper(this);
        catalogoAnimales = Animal.getCatalogo();

        spAnimals = findViewById(R.id.spAnimals);
        tvAnimalRequirements = findViewById(R.id.tvAnimalRequirements);
        tvSimTemp = findViewById(R.id.tvSimTemp);
        tvSimHum = findViewById(R.id.tvSimHum);
        tvSimCo2 = findViewById(R.id.tvSimCo2);
        tvAlertStatus = findViewById(R.id.tvAlertStatus);
        tvBtStatus = findViewById(R.id.tvBtStatus);
        tvDbRecords = findViewById(R.id.tvDbRecords);
        btnSimulateNow = findViewById(R.id.btnSimulateNow);

        ArrayAdapter<Animal> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catalogoAnimales);
        spAnimals.setAdapter(adapter);

        spAnimals.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                animalSeleccionado = catalogoAnimales[position];
                tvAnimalRequirements.setText(animalSeleccionado.getRequerimientosTexto());
                cargarMedicionActualVista();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSimulateNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateAndSaveSimulation("SIMULACION_MANUAL");
            }
        });

        generateAndSaveSimulation("INICIO_APP");

        simHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                generateAndSaveSimulation("MEDICION_HORARIA");
                simHandler.postDelayed(this, ONE_HOUR_MS);
            }
        }, ONE_HOUR_MS);

        startBluetoothServer();
        refreshLocalRecords();
    }

    private void cargarMedicionActualVista() {
        if (animalSeleccionado == null) return;
        Cursor c = dbHelper.getCurrentMeasurementByAnimal(animalSeleccionado.getNombre());
        if (c != null && c.moveToFirst()) {
            double temp = c.getDouble(c.getColumnIndex("temperatura"));
            double hum = c.getDouble(c.getColumnIndex("humedad"));
            int co2 = c.getInt(c.getColumnIndex("co2"));

            tvSimTemp.setText("Temp: " + temp + " °C");
            tvSimHum.setText("Humedad: " + hum + " %");
            tvSimCo2.setText("CO2: " + co2 + " ppm");

            actualizarTarjetaAlertas(temp, hum, co2);
            c.close();
        }
    }

    private void actualizarTarjetaAlertas(double temp, double hum, int co2) {
        if (animalSeleccionado == null) return;
        String mensajeAlertas = animalSeleccionado.evaluarAlertas(temp, hum, co2);
        tvAlertStatus.setText(mensajeAlertas);

        if (animalSeleccionado.isNormal(temp, hum, co2)) {
            tvAlertStatus.setBackgroundResource(R.drawable.bg_alert_ok);
            tvAlertStatus.setTextColor(getResources().getColor(R.color.alert_ok_text));
        } else {
            tvAlertStatus.setBackgroundResource(R.drawable.bg_alert_danger);
            tvAlertStatus.setTextColor(getResources().getColor(R.color.alert_danger_text));
        }
    }

    private void generateAndSaveSimulation(String origen) {
        if (animalSeleccionado == null) animalSeleccionado = catalogoAnimales[0];

        double deltaTemp = (random.nextDouble() - 0.5) * 8.0;
        double temp = Math.round((animalSeleccionado.getTempMin() + deltaTemp) * 10.0) / 10.0;

        double deltaHum = (random.nextDouble() - 0.5) * 20.0;
        double hum = Math.round((animalSeleccionado.getHumMin() + deltaHum) * 10.0) / 10.0;

        int co2 = animalSeleccionado.getCo2Max() - 100 + random.nextInt(400);

        tvSimTemp.setText("Temp: " + temp + " °C");
        tvSimHum.setText("Humedad: " + hum + " %");
        tvSimCo2.setText("CO2: " + co2 + " ppm");

        actualizarTarjetaAlertas(temp, hum, co2);

        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        dbHelper.saveMeasurementAndHistory(animalSeleccionado.getNombre(), animalSeleccionado.getHabitat(), temp, hum, co2, date, origen);
        refreshLocalRecords();
    }

    public void refreshLocalRecords() {
        Cursor c = dbHelper.getAllCurrentMeasurements();
        StringBuilder sb = new StringBuilder();

        if (c != null) {
            int animCol = c.getColumnIndex("animal");
            int habCol = c.getColumnIndex("habitat");
            int tempCol = c.getColumnIndex("temperatura");
            int humCol = c.getColumnIndex("humedad");
            int co2Col = c.getColumnIndex("co2");
            int dateCol = c.getColumnIndex("fecha");

            while (c.moveToNext()) {
                sb.append("• ").append(c.getString(animCol))
                        .append(" (").append(c.getString(habCol)).append(")\n")
                        .append("  T: ").append(c.getDouble(tempCol)).append("°C | ")
                        .append("H: ").append(c.getDouble(humCol)).append("% | ")
                        .append("CO2: ").append(c.getInt(co2Col)).append("ppm\n")
                        .append("  Hora: ").append(c.getString(dateCol)).append("\n\n");
            }
            c.close();
        }
        tvDbRecords.setText(sb.length() > 0 ? sb.toString() : "Sin mediciones registradas.");
    }

    private void startBluetoothServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    runOnUiThread(() -> tvBtStatus.setText("Bluetooth desactivado."));
                    return;
                }

                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord("ZOOAPP", ZOO_UUID);
                    runOnUiThread(() -> tvBtStatus.setText("Servidor Bluetooth Activo"));

                    while (isServerRunning) {
                        BluetoothSocket socket = serverSocket.accept();
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                        String line = in.readLine();
                        if (line != null) {
                            if (line.startsWith("GET_CURRENT")) {
                                String animal = line.split(";")[1];
                                Cursor c = dbHelper.getCurrentMeasurementByAnimal(animal);
                                if (c != null && c.moveToFirst()) {
                                    out.println(c.getDouble(c.getColumnIndex("temperatura")) + ";" +
                                            c.getDouble(c.getColumnIndex("humedad")) + ";" +
                                            c.getInt(c.getColumnIndex("co2")) + ";" +
                                            c.getString(c.getColumnIndex("fecha")));
                                    c.close();
                                } else {
                                    out.println("NO_DATA");
                                }
                                out.flush();
                            } else if (line.startsWith("GET_HISTORY")) {
                                String animal = line.split(";")[1];
                                Cursor c = dbHelper.getHistoryByAnimal(animal);
                                StringBuilder sb = new StringBuilder();
                                if (c != null) {
                                    while (c.moveToNext()) {
                                        sb.append(c.getDouble(c.getColumnIndex("temperatura"))).append(";")
                                                .append(c.getDouble(c.getColumnIndex("humedad"))).append(";")
                                                .append(c.getInt(c.getColumnIndex("co2"))).append(";")
                                                .append(c.getString(c.getColumnIndex("origen"))).append(";")
                                                .append(c.getString(c.getColumnIndex("fecha"))).append("|");
                                    }
                                    c.close();
                                }
                                out.println(sb.toString());
                                out.flush();
                            } else if (line.startsWith("UPDATE")) {
                                String[] parts = line.split(";");
                                if (parts.length >= 5) {
                                    final String animal = parts[1];
                                    final double temp = Double.parseDouble(parts[2]);
                                    final double hum = Double.parseDouble(parts[3]);
                                    final int co2 = Integer.parseInt(parts[4]);

                                    String habitat = "Hábitat";
                                    for (Animal a : catalogoAnimales) {
                                        if (a.getNombre().equals(animal)) {
                                            habitat = a.getHabitat();
                                            break;
                                        }
                                    }

                                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                                    dbHelper.saveMeasurementAndHistory(animal, habitat, temp, hum, co2, date, "AJUSTE_MONITOR");

                                    out.println("SUCCESS");
                                    out.flush();

                                    runOnUiThread(() -> {
                                        refreshLocalRecords();
                                        if (animalSeleccionado != null && animalSeleccionado.getNombre().equals(animal)) {
                                            tvSimTemp.setText("Temp: " + temp + " °C");
                                            tvSimHum.setText("Humedad: " + hum + " %");
                                            tvSimCo2.setText("CO2: " + co2 + " ppm");
                                            actualizarTarjetaAlertas(temp, hum, co2);
                                        }
                                    });
                                } else {
                                    out.println("ERROR");
                                    out.flush();
                                }
                            }
                        }
                        Thread.sleep(100);
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isServerRunning = false;
        simHandler.removeCallbacksAndMessages(null);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }
}