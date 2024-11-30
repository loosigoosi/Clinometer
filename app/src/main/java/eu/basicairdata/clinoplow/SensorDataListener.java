package eu.basicairdata.clinoplow;

import android.hardware.Sensor;

public interface SensorDataListener {
    void onSensorChanged(SensorData data);

    void onAccuracyChanged(Sensor sensor, int accuracy);
}