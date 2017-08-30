package com.example.alps.imuplot;


import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.content.Context;
import android.graphics.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.util.Redrawer;
import com.androidplot.xy.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int FILTER_SIZE = 80;
    private double STEP_LENGTH = 0.2;


    private List<Double> filterBuffer;

    private SensorManager sensorMgr = null;
    private SensorEventListener sensorEventListener;

    private double angleX = 0;
    private double angleY = 0;
    private int angleCount = 0;


    float[] mGravity = null;


    private Redrawer redrawer;

    private LineAndPointFormatter red = null;
    private SimpleXYSeries mapXYSeries = null;

    private LineAndPointFormatter blue = null;
    private SimpleXYSeries mapABSeries = null;


    private boolean click = true;

    private int detect = 0;
    private int detect0 = 0;
    private int steps = 0;

    private double a1 = 7;
    private double b1 = -2;
    private double a0 = 7;
    private double b0 = -2;

    float[] geomagneticRotVector = null;
    float[] gameRotVector = null;

    private double geomagneticRotAzimuth = 0;
    private double gameRotAzimuth = 0;
    private double headingOffset = 0;
    private double initialOffset = 0;

    private double heading = 0;
    private int flag = 0;
    private int flag1 =0;
    private int flag2 =0;


    private XYPlot tracePlot = null;

    private FileWriter metaWriter;
    private File metaDataDir;

    private TextView stepcountField;
    private TextView headingOffsetField;
    private TextView geomagneticRVField;
    private TextView gameRVField;
    private Button startStop;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stepcountField = (TextView) findViewById(R.id.stepCount);
        headingOffsetField = (TextView) findViewById(R.id.headingOffset);
        geomagneticRVField = (TextView) findViewById(R.id.geomagneticRV);
        gameRVField =(TextView) findViewById(R.id.gameRV);

        startStop = (Button) findViewById(R.id.startStop);

        filterBuffer = new ArrayList<Double>();

        for (int i = 0; i < FILTER_SIZE; i++) {
            filterBuffer.add(Double.valueOf(0));
        }


        sensorEventListener = this;

        tracePlot = (XYPlot) findViewById(R.id.tracePlot);

        Paint mPaint = new Paint();
        mPaint.setColor(Color.TRANSPARENT);

        Paint linePaint1 = new Paint();
        linePaint1.setColor(Color.rgb(242, 242, 242));

        tracePlot.setBackgroundPaint(mPaint);
        tracePlot.getGraph().setBackgroundPaint(mPaint);
        tracePlot.getGraph().setGridBackgroundPaint(mPaint);

        tracePlot.getGraph().setDomainGridLinePaint(linePaint1);
        tracePlot.getGraph().setRangeGridLinePaint(linePaint1);

        tracePlot.getGraph().setDomainSubGridLinePaint(linePaint1);
        tracePlot.getGraph().setRangeSubGridLinePaint(linePaint1);

        mapXYSeries = new SimpleXYSeries("a+m");
        red = new FastLineAndPointRenderer.Formatter(null,Color.rgb(200, 0, 0), null);
        tracePlot.addSeries(mapXYSeries, red);

        // start points
        mapXYSeries.addLast(2.9,-2.1);
        mapXYSeries.addLast(7,-1.7);

        mapABSeries = new SimpleXYSeries("a+g");
        blue = new FastLineAndPointRenderer.Formatter(Color.rgb(80, 150, 240), Color.rgb(80, 150, 240), null);
        tracePlot.addSeries(mapABSeries, blue);

        tracePlot.setRangeBoundaries(-10, 10, BoundaryMode.FIXED);
        tracePlot.setDomainBoundaries(-10, 10, BoundaryMode.FIXED);

        tracePlot.setDomainStepMode(StepMode.INCREMENT_BY_PIXELS);
        tracePlot.setDomainStepValue(10);

        tracePlot.setRangeStepMode(StepMode.INCREMENT_BY_PIXELS);
        tracePlot.setRangeStepValue(10);

        tracePlot.setDomainLabel("X");
        tracePlot.getDomainTitle().pack();
        tracePlot.setRangeLabel("Y");
        tracePlot.getRangeTitle().pack();

        tracePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#.#"));

        tracePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).
                setFormat(new DecimalFormat("#"));

        tracePlot.setOnTouchListener(new View.OnTouchListener() {

              public boolean onTouch(View v, MotionEvent me) {
                  if(flag == 0)
                  {
                      float touchX = tracePlot.screenToSeriesX(me.getX()).floatValue();
                      float touchY = tracePlot.screenToSeriesY(me.getY()).floatValue();

                      if(touchX > 6.5){
                          a1 = 7;
                          a0 = 7;
                          b1 = -1.7;
                          b0 = -1.7;
                      }
                      else if(touchY < 0){
                          a1 = 2.9;
                          a0 = 2.9;
                          b1 = -2.1;
                          b0 = -2.1;
                      }

                      Toast.makeText(MainActivity.this, "( "+String.valueOf(a1)+" , "+String.valueOf(b1)+" )", Toast.LENGTH_SHORT).show();

                      flag = 1;
                  }
                  return  true;
              }
        });

        metaDataDir = new File(this.getExternalFilesDir(null).getAbsolutePath(), String.valueOf(System.currentTimeMillis()+".csv"));

        startStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (click) {
                    click = false;
                    Toast.makeText(MainActivity.this, "Started...", Toast.LENGTH_SHORT).show();
                    plot(sensorEventListener);
                } else {
                    Toast.makeText(MainActivity.this, "Stopped...", Toast.LENGTH_SHORT).show();
                    click = true;
                    sensorMgr.unregisterListener(sensorEventListener);
                }


            }
        });

    }

    @Override
    public void onResume() {

        super.onResume();
    }

    @Override
    public void onDestroy() {

        redrawer.finish();
        super.onDestroy();
        sensorMgr.unregisterListener(this);

    }

    private void plot(SensorEventListener sensorEventListener) {

        sensorMgr = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

        sensorMgr.registerListener(sensorEventListener, sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        sensorMgr.registerListener(sensorEventListener, sensorMgr.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);
        sensorMgr.registerListener(sensorEventListener, sensorMgr.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);

        redrawer = new Redrawer(tracePlot, 200, true);
    }

    private double Mean(List X,int N) {

        double sum = 0.0;

        for (int i = 0; i < N; i++) {
            sum = sum + (Double) X.get(i);
        }

        return (sum / N);
    }

    @Override
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {


        final double threshold = 10.5;
        double x, y, z, a, fa = 0;

        if (sensorEvent.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR){
            geomagneticRotVector = sensorEvent.values;
            float R1[] = new float[9];
            float orientation1[] = new float[3];
            SensorManager.getRotationMatrixFromVector(R1,geomagneticRotVector);
            SensorManager.getOrientation(R1, orientation1);
            geomagneticRotAzimuth = (orientation1[0]);

            geomagneticRVField.setText(String.valueOf((int)(Math.toDegrees(geomagneticRotAzimuth))));
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR){
            gameRotVector = sensorEvent.values;
            float R2[] = new float[9];
            float orientation2[] = new float[3];
            SensorManager.getRotationMatrixFromVector(R2,gameRotVector);
            SensorManager.getOrientation(R2, orientation2);
            gameRotAzimuth = (orientation2[0]);

            if(flag1 == 0){
                initialOffset = gameRotAzimuth;
                flag1 = 1;
            }
            gameRotAzimuth = (gameRotAzimuth - initialOffset);

            gameRVField.setText(String.valueOf((int)(Math.toDegrees(gameRotAzimuth))));
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR || sensorEvent.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {

            if (flag2 == 0) {
                headingOffset = ((geomagneticRotAzimuth) - (gameRotAzimuth));

//                LogData(headingOffset);

                angleX += Math.cos((headingOffset));
                angleY += Math.sin((headingOffset));
                angleCount++;

                if (steps == 6) {

                    angleY = angleY / angleCount;
                    angleX = angleX / angleCount;

                    headingOffset = ((Math.atan2(angleY, angleX)));
                    flag2 = 1;
                }
            }

            headingOffsetField.setText(String.valueOf((int) (Math.toDegrees(headingOffset))));
        }



        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            mGravity = sensorEvent.values;
            x = mGravity[0];
            y = mGravity[1];
            z = mGravity[2];

            a = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));

            filterBuffer.remove(0);
            filterBuffer.add(Double.valueOf(a));

            fa = Mean(filterBuffer,FILTER_SIZE);
//            LogData(0,fa);


            // Zero Crossing Threshold
            if (fa > threshold) {
                detect = 1;
            } else {
                detect = 0;
            }

            if (detect - detect0 == 1) {

                steps++;

                mapABSeries.addLast(a1,b1);

                stepcountField.setText(String.valueOf(steps));

                heading = ((gameRotAzimuth+headingOffset));

                a1 = a0+ STEP_LENGTH*Math.sin((heading));
                b1 = b0+ STEP_LENGTH*Math.cos((heading));

                a0 = a1;
                b0 = b1;
            }

            detect0 = detect;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void LogData(double data1) {

        try {
            metaWriter = new FileWriter(metaDataDir,true);
            metaWriter.write(String.valueOf(data1)+ "\n");
            metaWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}


