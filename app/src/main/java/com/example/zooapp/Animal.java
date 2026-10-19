package com.example.zooapp;

public class Animal {
    private String nombre;
    private String habitat;
    private double tempMin, tempMax;
    private double humMin, humMax;
    private int co2Max;

    public Animal(String nombre, String habitat, double tempMin, double tempMax, double humMin, double humMax, int co2Max) {
        this.nombre = nombre;
        this.habitat = habitat;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.humMin = humMin;
        this.humMax = humMax;
        this.co2Max = co2Max;
    }

    public String getNombre() { return nombre; }
    public String getHabitat() { return habitat; }
    public double getTempMin() { return tempMin; }
    public double getTempMax() { return tempMax; }
    public double getHumMin() { return humMin; }
    public double getHumMax() { return humMax; }
    public int getCo2Max() { return co2Max; }

    public String getRequerimientosTexto() {
        return "Rango Ideal:\n• Temp: " + tempMin + "°C a " + tempMax + "°C\n• Humedad: " + humMin + "% a " + humMax + "%\n• CO2 Máx: " + co2Max + " ppm";
    }

    public boolean isNormal(double temp, double hum, int co2) {
        return temp >= tempMin && temp <= tempMax && hum >= humMin && hum <= humMax && co2 <= co2Max;
    }

    public String evaluarAlertas(double temp, double hum, int co2) {
        if (isNormal(temp, hum, co2)) {
            return "✅ ESTADO ÓPTIMO: Parámetros en rango seguro.";
        }

        StringBuilder sb = new StringBuilder("⚠️ ALERTA EN HÁBITAT:\n");
        if (temp < tempMin) sb.append("• Temp BAJA (Mín ").append(tempMin).append("°C)\n");
        if (temp > tempMax) sb.append("• Temp ALTA (Máx ").append(tempMax).append("°C)\n");
        if (hum < humMin) sb.append("• Humedad BAJA (Mín ").append(humMin).append("%)\n");
        if (hum > humMax) sb.append("• Humedad ALTA (Máx ").append(humMax).append("%)\n");
        if (co2 > co2Max) sb.append("• CO2 CRÍTICO (> ").append(co2Max).append(" ppm)\n");

        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return nombre + " (" + habitat + ")";
    }

    public static Animal[] getCatalogo() {
        return new Animal[]{
                new Animal("Iguana Verde", "Terrario Tropical", 28.0, 34.0, 70.0, 85.0, 800),
                new Animal("Pingüino de Humboldt", "Recinto Subantártico", 10.0, 16.0, 50.0, 65.0, 600),
                new Animal("León Africano", "Sabana", 20.0, 26.0, 40.0, 60.0, 1000),
                new Animal("Rana Dardo", "Selva Húmeda", 22.0, 26.0, 80.0, 95.0, 500)
        };
    }
}