/*
 * CalibrationActivity - Java Class for Android
 * Created by G.Capelli (BasicAirData) on 3/6/2020
 *
 * This file is part of BasicAirData Clinometer for Android.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package eu.basicairdata.clinometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.preference.PreferenceManager;


public class CalibrationActivity extends AppCompatActivity implements SensorEventListener {

    private Vibrator vibrator;

    private final static int ACCELEROMETER_UPDATE_INTERVAL_MICROS = 10000;
    private final static float MIN_CALIBRATION_PRECISION = 0.05f;
    private final static int SIZE_OF_MEANVARIANCE = 300;                    // 4 seconds


    MeanVariance MVGravity0 = new MeanVariance(SIZE_OF_MEANVARIANCE);
    MeanVariance MVGravity1 = new MeanVariance(SIZE_OF_MEANVARIANCE);
    MeanVariance MVGravity2 = new MeanVariance(SIZE_OF_MEANVARIANCE);

    private float[][] Mean = new float[3][7];               // The Mean values of vectors

    private float[] CalibrationOffset = new float[3];      // The Offsets of accelerometers
    private float[] CalibrationGain = new float[3];        // The Gains of accelerometers
    private float[] CalibrationAngle = new float[3];       // The calibration angles

    private AppCompatButton buttonNext;
    private ProgressBar progressBar;
    private ImageView imageViewMain;
    private TextView textViewStepDescription;
    private TextView textViewLastCalibration;
    private TextView textViewProgress;

    private int CurrentStep = 0;                // The current step of the wizard;

    private static final int STEP_1         = 0;    // Step 1 of 7      Lay flat and press next
    private static final int STEP_1_CAL     = 1;    // Calibrating...   Don't move the device
    private static final int STEP_2         = 2;    // Step 2 of 7      Rotate 180° and press next
    private static final int STEP_2_CAL     = 3;    // Calibrating...   Don't move the device
    private static final int STEP_3         = 4;    // Step 3 of 7      Lay on the left side and press next
    private static final int STEP_3_CAL     = 5;    // Calibrating...   Don't move the device
    private static final int STEP_4         = 6;    // Step 4 of 7      Rotate 180° and press next
    private static final int STEP_4_CAL     = 7;    // Calibrating...   Don't move the device
    private static final int STEP_5         = 8;    // Step 5 of 7      Lay vertical and press next
    private static final int STEP_5_CAL     = 9;    // Calibrating...   Don't move the device
    private static final int STEP_6         = 10;   // Step 6 of 7      Rotate 180° upside-down and press next
    private static final int STEP_6_CAL     = 11;   // Calibrating...   Don't move the device
    private static final int STEP_7         = 12;   // Step 7 of 7      Press next and lay face down
    private static final int STEP_7_CAL     = 13;   // Calibrating...   Don't move the device
    private static final int STEP_COMPLETED = 14;   // Calibration completed (Message Box)

    private static final float STANDARD_GRAVITY = 9.807f;

    private static final int DISCARD_FIRST_SAMPLES = 20;
    private int SamplesDiscarded = 0;

    ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE);

    private SensorManager mSensorManager;
    private Sensor mRotationSensor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setContentView(R.layout.activity_calibration);

        buttonNext = findViewById(R.id.id_button_next);
        progressBar = findViewById(R.id.id_progressBar);
        textViewStepDescription = findViewById(R.id.id_textview_step_description);
        textViewLastCalibration = findViewById(R.id.id_textview_last_calibration);
        textViewProgress = findViewById(R.id.id_textview_progress);
        imageViewMain = findViewById(R.id.id_imageViewMain);

        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CurrentStep++;
                StartStep();
            }
        });

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mRotationSensor == null) Log.d("SpiritLevel", "NO ACCELEROMETER FOUND!");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.contains("prefCalibrationAngle0")) {
            textViewLastCalibration.setText(getString(R.string.calibration_active_calibration) + "\n"
                    + getString(R.string.calibration_active_calibration_gains)
                    + String.format(" = %1.3f; %1.3f; %1.3f", preferences.getFloat("prefCalibrationGain0", 0), preferences.getFloat("prefCalibrationGain1", 0), preferences.getFloat("prefCalibrationGain2", 0))
                    + "\n"
                    + getString(R.string.calibration_active_calibration_offsets)
                    + String.format(" = %1.3f; %1.3f; %1.3f", preferences.getFloat("prefCalibrationOffset0", 0), preferences.getFloat("prefCalibrationOffset1", 0), preferences.getFloat("prefCalibrationOffset2", 0))
                    + "\n"
                    + getString(R.string.calibration_active_calibration_angles)
                    + String.format(" = %1.2f°; %1.2f°; %1.2f°", preferences.getFloat("prefCalibrationAngle0", 0), preferences.getFloat("prefCalibrationAngle1", 0), preferences.getFloat("prefCalibrationAngle2", 0))
            );
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if ((int) (CurrentStep / 2) * 2 != CurrentStep) CurrentStep--;
        Log.d("CalibrationActivity", "CurrentStep = " + CurrentStep);

        StartStep();

        //mSensorManager.registerListener(this, mRotationSensor, ACCELEROMETER_UPDATE_INTERVAL_MICROS);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }


    private void StartStep() {
        progressBar.setProgress(0);
        progressBar.setSecondaryProgress(0);
        textViewProgress.setText("");
        textViewLastCalibration.setVisibility(View.INVISIBLE);

        switch (CurrentStep) {
            case STEP_1:
                textViewLastCalibration.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_01);
                textViewStepDescription.setText(R.string.calibration_step1);
                break;
            case STEP_2:
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_02);
                textViewStepDescription.setText(R.string.calibration_step2);
                break;
            case STEP_3:
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_03);
                textViewStepDescription.setText(R.string.calibration_step3);
                break;
            case STEP_4:
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_04);
                textViewStepDescription.setText(R.string.calibration_step4);
                break;
            case STEP_5:
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_05);
                textViewStepDescription.setText(R.string.calibration_step5);
                break;
            case STEP_6:
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_06);
                textViewStepDescription.setText(R.string.calibration_step6);
                break;
            case STEP_7:
                progressBar.setVisibility(View.INVISIBLE);
                buttonNext.setVisibility(View.VISIBLE);
                textViewProgress.setVisibility(View.INVISIBLE);
                imageViewMain.setImageResource(R.mipmap.cal_07);
                textViewStepDescription.setText(R.string.calibration_step7);
                break;
            case STEP_1_CAL:
            case STEP_2_CAL:
            case STEP_3_CAL:
            case STEP_4_CAL:
            case STEP_5_CAL:
            case STEP_6_CAL:
            case STEP_7_CAL:
                progressBar.setVisibility(View.VISIBLE);
                buttonNext.setVisibility(View.INVISIBLE);
                textViewProgress.setVisibility(View.VISIBLE);
                textViewStepDescription.setText(R.string.calibration_calibrating);
                MVGravity0.Reset();
                MVGravity1.Reset();
                MVGravity2.Reset();
                SamplesDiscarded = 0;
                mSensorManager.registerListener(this, mRotationSensor, ACCELEROMETER_UPDATE_INTERVAL_MICROS);
                break;
            case STEP_COMPLETED:
                // Calculations
                Log.d("SpiritLevel","-- MEAN NOT CORRECTED ----------------------------------------------------");
                Log.d("SpiritLevel", String.format("Mean0  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][0], Mean[1][0], Mean[2][0]));
                Log.d("SpiritLevel", String.format("Mean1  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][1], Mean[1][1], Mean[2][1]));
                Log.d("SpiritLevel", String.format("Mean2  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][2], Mean[1][2], Mean[2][2]));
                Log.d("SpiritLevel", String.format("Mean3  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][3], Mean[1][3], Mean[2][3]));
                Log.d("SpiritLevel", String.format("Mean4  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][4], Mean[1][4], Mean[2][4]));
                Log.d("SpiritLevel", String.format("Mean5  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][5], Mean[1][5], Mean[2][5]));
                Log.d("SpiritLevel", String.format("Mean6  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][6], Mean[1][6], Mean[2][6]));

                // Calibration offset and Gain (https://www.digikey.it/it/articles/using-an-accelerometer-for-inclination-sensing)

                CalibrationOffset[0] = (Mean[0][2] + Mean[0][3]) / 2;
                CalibrationOffset[1] = (Mean[1][4] + Mean[1][5]) / 2;
                CalibrationOffset[2] = (Mean[2][0] + Mean[2][6]) / 2;

                CalibrationGain[0] = (Mean[0][2] - Mean[0][3]) / (STANDARD_GRAVITY * 2);
                CalibrationGain[1] = (Mean[1][4] - Mean[1][5]) / (STANDARD_GRAVITY * 2);
                CalibrationGain[2] = (Mean[2][0] - Mean[2][6]) / (STANDARD_GRAVITY * 2);

                // Estimation of the third axis
//                CalibrationGain[2] = (CalibrationGain[0] + CalibrationGain[1]) / 2;
//                CalibrationOffset[2] = (Mean[2][0] + Mean[2][0]) / 2 - (CalibrationGain[2] * STANDARD_GRAVITY);

                Log.d("SpiritLevel","-- ACCELEROMETERS ----------------------------------------------------------");
                Log.d("SpiritLevel", String.format("Offset  =  %+1.4f  %+1.4f  %+1.4f", CalibrationOffset[0], CalibrationOffset[1], CalibrationOffset[2]));
                Log.d("SpiritLevel", String.format("Gain    =  %+1.4f  %+1.4f  %+1.4f", CalibrationGain[0], CalibrationGain[1], CalibrationGain[2]));

                // Apply the Gain and Offset Correction to measurement

                for (int i = 0; i < 7; i++) {
                    Mean[0][i] = (Mean[0][i] - CalibrationOffset[0]) / CalibrationGain[0];
                    Mean[1][i] = (Mean[1][i] - CalibrationOffset[1]) / CalibrationGain[1];
                    Mean[2][i] = (Mean[2][i] - CalibrationOffset[2]) / CalibrationGain[2];
                }

                Log.d("SpiritLevel","-- MEAN CORRECTED --------------------------------------------------------");
                Log.d("SpiritLevel", String.format("Mean0  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][0], Mean[1][0], Mean[2][0]));
                Log.d("SpiritLevel", String.format("Mean1  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][1], Mean[1][1], Mean[2][1]));
                Log.d("SpiritLevel", String.format("Mean2  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][2], Mean[1][2], Mean[2][2]));
                Log.d("SpiritLevel", String.format("Mean3  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][3], Mean[1][3], Mean[2][3]));
                Log.d("SpiritLevel", String.format("Mean4  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][4], Mean[1][4], Mean[2][4]));
                Log.d("SpiritLevel", String.format("Mean5  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][5], Mean[1][5], Mean[2][5]));
                Log.d("SpiritLevel", String.format("Mean6  =  %+1.4f  %+1.4f  %+1.4f", Mean[0][6], Mean[1][6], Mean[2][6]));

                // Calculation of Angles

                float[][] Angle = new float[3][7];

                Log.d("SpiritLevel","-- ANGLES ----------------------------------------------------------------------");
                for (int i = 0; i < 7; i++) {
                    Angle[0][i] = (float) (Math.toDegrees(Math.asin(Mean[0][i]
                            / Math.sqrt(Mean[0][i] * Mean[0][i] + Mean[1][i] * Mean[1][i] + Mean[2][i] * Mean[2][i]))));
                    Angle[1][i] = (float) (Math.toDegrees(Math.asin(Mean[1][i]
                            / Math.sqrt(Mean[0][i] * Mean[0][i] + Mean[1][i] * Mean[1][i] + Mean[2][i] * Mean[2][i]))));
                    Angle[2][i] = (float) (Math.toDegrees(Math.asin(Mean[2][i]
                            / Math.sqrt(Mean[0][i] * Mean[0][i] + Mean[1][i] * Mean[1][i] + Mean[2][i] * Mean[2][i]))));
                    Log.d("SpiritLevel", String.format("Angles =  %+1.4f°  %+1.4f°  %+1.4f°", Angle[0][i], Angle[1][i], Angle[2][i]));
                }

                CalibrationAngle[2] =  (Angle[0][0] + Angle[0][1])/2;       // Angle 0 = X axis
                CalibrationAngle[1] = -(Angle[1][0] + Angle[1][1])/2;       // Angle 1 = Y axis
                CalibrationAngle[0] = -(Angle[1][3] + Angle[1][2])/2;       // Angle 2 = Z axis

                Log.d("SpiritLevel","-- CALIBRATION ANGLES ----------------------------------------------------------");
                Log.d("SpiritLevel", String.format("Cal.Angles =  %+1.4f°  %+1.4f°  %+1.4f°", CalibrationAngle[0], CalibrationAngle[1], CalibrationAngle[2]));


//                Angle[0][i] = (float) (180 / Math.PI * Math.asin((MVGravity0.getMeanValue() / Math.max(gravityXYZ, 0.01f))));
//                Angle[1][i] = (float) (180 / Math.PI * Math.asin((MVGravity1.getMeanValue() / Math.max(gravityXYZ, 0.01f))));
//                Angle[2][i] = (float) (180 / Math.PI * Math.asin((MVGravity2.getMeanValue() / Math.max(gravityXYZ, 0.01f))));
//
//                CalibrationAngle[2] = (Angle[0][0] + Angle[0][1])/2;        // Angle 0 = X axe
//                CalibrationAngle[1] = -(Angle[1][0] + Angle[1][1])/2;       // Angle 1 = Y axe
//                CalibrationAngle[0] = -(Angle[1][3] + Angle[1][2])/2;       // Angle 2 = Z axe
//
//                Log.d("SpiritLevel", String.format("CAL   =  %+1.4f°  %+1.4f°  %+1.4f°", CalibrationAngle[0], CalibrationAngle[1], CalibrationAngle[2]));
//                Log.d("SpiritLevel","------------------------------------------------------");
//
//                //Log.d("SpiritLevel", String.format("GRAVX =  %+1.4f°  %+1.4f°", GravityXp, GravityXn));
//                Log.d("SpiritLevel","------------------------------------------------------");
//

                Log.d("SpiritLevel","----------------------------------------------------------------------------");

                // Write Calibration Angles into Preferences
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putFloat("prefCalibrationAngle0", CalibrationAngle[0]);
                editor.putFloat("prefCalibrationAngle1", CalibrationAngle[1]);
                editor.putFloat("prefCalibrationAngle2", CalibrationAngle[2]);
                editor.putFloat("prefCalibrationGain0", CalibrationGain[0]);
                editor.putFloat("prefCalibrationGain1", CalibrationGain[1]);
                editor.putFloat("prefCalibrationGain2", CalibrationGain[2]);
                editor.putFloat("prefCalibrationOffset0", CalibrationOffset[0]);
                editor.putFloat("prefCalibrationOffset1", CalibrationOffset[1]);
                editor.putFloat("prefCalibrationOffset2", CalibrationOffset[2]);
                editor.putLong("prefCalibrationTime", System.currentTimeMillis());
                editor.commit();

                finish();
        }
    }


    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((int) (CurrentStep / 2) * 2 == CurrentStep) {
                // Stop Calibration
                mSensorManager.unregisterListener(this);
            } else {

                if (SamplesDiscarded < DISCARD_FIRST_SAMPLES) {
                    SamplesDiscarded++;
                    return;
                }

                // Calibration
                //Log.d("CalibrationActivity", "CALIBRATION");

                MVGravity0.LoadSample(event.values[0]);
                MVGravity1.LoadSample(event.values[1]);
                MVGravity2.LoadSample(event.values[2]);

                textViewProgress.setText(String.format("Progress %1.0f%%   Tolerance %1.3f", MVGravity0.percentLoaded(), MVGravity0.getTolerance()));
                int progress1 = (int) (10 * MVGravity0.percentLoaded());
                int progress2 = (int) (Math.min(1000, Math.max(0, 1000 - 1000 *(MVGravity0.getTolerance() / MIN_CALIBRATION_PRECISION))));
                progressBar.setSecondaryProgress(Math.max(progress1, progress2));
                progressBar.setProgress(Math.min(progress1, progress2));


                // DEVICE MOVED

                if (MVGravity0.isReady() && (MVGravity0.getTolerance() > MIN_CALIBRATION_PRECISION)) {
                    MVGravity0.Reset();
                    MVGravity1.Reset();
                    MVGravity2.Reset();
                }

                // END OF CALIBRATION STEP

                if (MVGravity0.percentLoaded() == 100) {
                    mSensorManager.unregisterListener(this);

                    int i = (int) (CurrentStep / 2);

                    Mean[0][i] = MVGravity0.getMeanValue(SIZE_OF_MEANVARIANCE-100);
                    Mean[1][i] = MVGravity1.getMeanValue(SIZE_OF_MEANVARIANCE-100);
                    Mean[2][i] = MVGravity2.getMeanValue(SIZE_OF_MEANVARIANCE-100);

                    Beep();

                    CurrentStep++;
                    StartStep();
                }
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void Beep() {
        //toneGen1.startTone(ToneGenerator.TONE_SUP_PIP,150);
        vibrator.vibrate(250);
        toneGen1.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
    }
}