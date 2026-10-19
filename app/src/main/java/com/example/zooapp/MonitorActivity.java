package com.example.zooapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MonitorActivity extends AppCompatActivity {

    private Spinner spDevices, spSelectAnimal;
    private SeekBar sbTemp, sbHum, sbCo2;
    private TextView tvLabelTemp, tvLabelHum, tvLabelCo2;
    private Button btnFetchCurrent, btnSendUpdate, btnFetchHistory;
    private TextView tvCurrentStatus, tvAlertMonitorStatus, tvHistoryData;

    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private Animal[] catalogoAnimales;
    private Animal animalSeleccionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        catalogoAnimales = Animal.getCatalogo();

        spDevices = findViewById(R.id.spDevices);
        spSelectAnimal = findViewById(R.id.spSelectAnimal);

        sbTemp = findViewById(R.id.sbTemp);
        sbHum = findViewById(R.id.sbHum);
        sbCo2 = findViewById(R.id.sbCo2);

        tvLabelTemp = findViewById(R.id.tvLabelTemp);
        tvLabelHum = findViewById(R.id.tvLabelHum);
        tvLabelCo2 = findViewById(R.id.tvLabelCo2);

        btnFetchCurrent = findViewById(R.id.btnFetchCurrent);
        btnSendUpdate = findViewById(R.id.btnSendUpdate);
        btnFetchHistory = findViewById(R.id.btnFetchHistory);

        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);
        tvAlertMonitorStatus = findViewById(R.id.tvAlertMonitorStatus);
        tvHistoryData = findViewById(R.id.tvHistoryData);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        loadPairedDevices();

        ArrayAdapter<Animal> animalAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catalogoAnimales);
        spSelectAnimal.setAdapter(animalAdapter);

        spSelectAnimal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                animalSeleccionado = catalogoAnimales[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Actualización dinámica de etiquetas según la posición de la barra
        sbTemp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double val = progress / 10.0;
                tvLabelTemp.setText("Temperatura a ajustar: " + String.format(Locale.US, "%.1f", val) + " °C");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbHum.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double val = progress / 10.0;
                tvLabelHum.setText("Humedad a ajustar: " + String.format(Locale.US, "%.1f", val) + " %");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbCo2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLabelCo2.setText("CO2 a ajustar: " + progress + " ppm");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnFetchCurrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchCurrentMeasurement();
            }
        });

        btnSendUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendUpdate();
            }
        });

        btnFetchHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchHistoryData();
            }
        });
    }

    private void loadPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Activa el Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<String> deviceNames = new ArrayList<>();

        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device);
                deviceNames.add(device.getName() + "\n(" + device.getAddress() + ")");
            }
        } else {
            deviceNames.add("No hay dispositivos vinculados");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, deviceNames);
        spDevices.setAdapter(adapter);
    }

    private BluetoothDevice getSelectedDevice() {
        int pos = spDevices.getSelectedItemPosition();
        if (pos >= 0 && pos < deviceList.size()) {
            return deviceList.get(pos);
        }
        return null;
    }

    private void actualizarBannerAlertaMonitor(double temp, double hum, int co2) {
        if (animalSeleccionado == null) return;
        String msj = animalSeleccionado.evaluarAlertas(temp, hum, co2);
        tvAlertMonitorStatus.setText(msj);

        if (animalSeleccionado.isNormal(temp, hum, co2)) {
            tvAlertMonitorStatus.setBackgroundResource(R.drawable.bg_alert_ok);
            tvAlertMonitorStatus.setTextColor(getResources().getColor(R.color.alert_ok_text));
        } else {
            tvAlertMonitorStatus.setBackgroundResource(R.drawable.bg_alert_danger);
            tvAlertMonitorStatus.setTextColor(getResources().getColor(R.color.alert_danger_text));
        }
    }

    private void fetchCurrentMeasurement() {
        final BluetoothDevice device = getSelectedDevice();
        if (device == null || animalSeleccionado == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SensorActivity.ZOO_UUID);
                    socket.connect();

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println("GET_CURRENT;" + animalSeleccionado.getNombre());
                    out.flush();

                    final String response = in.readLine();
                    Thread.sleep(100);
                    socket.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response != null && !response.equals("NO_DATA")) {
                                String[] fields = response.split(";");
                                if (fields.length >= 4) {
                                    double temp = Double.parseDouble(fields[0]);
                                    double hum = Double.parseDouble(fields[1]);
                                    int co2 = Integer.parseInt(fields[2]);

                                    // Posiciona los deslizadores automáticamente según los datos recibidos
                                    sbTemp.setProgress((int) Math.round(temp * 10.0));
                                    sbHum.setProgress((int) Math.round(hum * 10.0));
                                    sbCo2.setProgress(co2);

                                    tvCurrentStatus.setText("Estado Actual (" + fields[3] + "):\n" +
                                            "Temp: " + temp + "°C | Hum: " + hum + "% | CO2: " + co2 + "ppm");

                                    actualizarBannerAlertaMonitor(temp, hum, co2);
                                }
                            } else {
                                tvCurrentStatus.setText("Sin datos registrados para " + animalSeleccionado.getNombre());
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MonitorActivity.this, "Error de Lectura: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void sendUpdate() {
        final BluetoothDevice device = getSelectedDevice();

        // Extrae los valores directamente de las barras deslizables
        final double temp = sbTemp.getProgress() / 10.0;
        final double hum = sbHum.getProgress() / 10.0;
        final int co2 = sbCo2.getProgress();

        final String tempStr = String.valueOf(temp);
        final String humStr = String.valueOf(hum);
        final String co2Str = String.valueOf(co2);

        if (device == null || animalSeleccionado == null) {
            Toast.makeText(this, "Selecciona dispositivo y animal", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SensorActivity.ZOO_UUID);
                    socket.connect();

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println("UPDATE;" + animalSeleccionado.getNombre() + ";" + tempStr + ";" + humStr + ";" + co2Str);
                    out.flush();

                    final String result = in.readLine();
                    Thread.sleep(100);
                    socket.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ("SUCCESS".equals(result)) {
                                Toast.makeText(MonitorActivity.this, "Ajuste aplicado correctamente", Toast.LENGTH_SHORT).show();

                                String fechaHora = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                                tvCurrentStatus.setText("Estado Actual (" + fechaHora + "):\n" +
                                        "Temp: " + temp + "°C | Hum: " + hum + "% | CO2: " + co2 + "ppm");

                                actualizarBannerAlertaMonitor(temp, hum, co2);
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MonitorActivity.this, "Error de Envío: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void fetchHistoryData() {
        final BluetoothDevice device = getSelectedDevice();
        if (device == null || animalSeleccionado == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SensorActivity.ZOO_UUID);
                    socket.connect();

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println("GET_HISTORY;" + animalSeleccionado.getNombre());
                    out.flush();

                    final String response = in.readLine();
                    Thread.sleep(100);
                    socket.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response != null && !response.isEmpty()) {
                                String[] records = response.split("\\|");
                                StringBuilder sb = new StringBuilder();

                                for (String rec : records) {
                                    String[] f = rec.split(";");
                                    if (f.length >= 5) {
                                        sb.append("• ").append(f[4]).append(" [").append(f[3]).append("]\n")
                                                .append("  Temp: ").append(f[0]).append("°C | Hum: ").append(f[1]).append("% | CO2: ").append(f[2]).append("ppm\n\n");
                                    }
                                }
                                tvHistoryData.setText(sb.toString());
                            } else {
                                tvHistoryData.setText("Sin historial para este animal.");
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MonitorActivity.this, "Error de Historial: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}