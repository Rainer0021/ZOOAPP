package com.example.zooapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etUser, etPassword;
    private Button btnLoginSensor, btnLoginMonitor;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        dbHelper = new DatabaseHelper(this);

        etUser = findViewById(R.id.etUser);
        etPassword = findViewById(R.id.etPassword);
        btnLoginSensor = findViewById(R.id.btnLoginSensor);
        btnLoginMonitor = findViewById(R.id.btnLoginMonitor);

        btnLoginSensor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (authenticate()) {
                    startActivity(new Intent(LoginActivity.this, SensorActivity.class));
                }
            }
        });

        btnLoginMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (authenticate()) {
                    startActivity(new Intent(LoginActivity.this, MonitorActivity.class));
                }
            }
        });
    }

    private boolean authenticate() {
        String u = etUser.getText().toString().trim();
        String p = etPassword.getText().toString().trim();

        if (u.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa usuario y contraseña", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (dbHelper.validateUser(u, p)) {
            return true;
        } else {
            Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}