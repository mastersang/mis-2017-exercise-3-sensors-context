package buw.sensors_and_context;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.google.gson.Gson;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener, SeekBar.OnSeekBarChangeListener,
        IAxisValueFormatter, View.OnClickListener {
    private static int MAX_DATA_COUNT = 10;
    private static String SETTING = "SETTING";

    private SensorManager objSensorManager;
    private long lngLastTime = System.currentTimeMillis();
    private int intEntryCount = 0;
    private List<Entry> lstX = new ArrayList<>();
    private List<Entry> lstY = new ArrayList<>();
    private List<Entry> lstZ = new ArrayList<>();
    private List<Entry> lstMagnitude = new ArrayList<>();
    private List<Entry> lstAllMagnitude = new ArrayList<>();
    private Setting objSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Initialise settings
            initialiseSetting();

            // Initialise sensor
            objSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor objSensor = objSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            objSensorManager.registerListener(this, objSensor, SensorManager.SENSOR_DELAY_NORMAL);

            initialiseSeekBars();
            initialiseChart();
        } catch (Exception ex) {
            Log.e("onCreate", "Exception", ex);
        }
    }

    private void initialiseTabButtons() {
        Button btnSensor = (Button) findViewById(R.id.btnSensor);
        initialiseButton(btnSensor);
        Button btnMusic = (Button) findViewById(R.id.btnMusic);
        initialiseButton(btnMusic);
    }

    // Wrapper
    private void initialiseButton(Button btn) {
        btn.setBackground((ContextCompat.getDrawable(this, R.color.colorTab)));
        btn.setTextColor(Color.WHITE);
    }

    private void initialiseSetting() {
        // Read setting from shared preferences, if it is not set then get default setting
        SharedPreferences objPreferences = getPreferences(MODE_PRIVATE);
        String strJSON = objPreferences.getString(SETTING, "");

        if (TextUtils.isEmpty(strJSON)) {
            objSetting = Setting.getDefaultSetting();
        } else {
            Gson objGson = new Gson();
            objSetting = objGson.fromJson(strJSON, Setting.class);
        }
    }

    private void initialiseSeekBars() {
        SeekBar skbSampleRate = (SeekBar) findViewById(R.id.skbSampleRate);
        skbSampleRate.setProgress(objSetting.SampleRate);

        SeekBar skbWindowSize = (SeekBar) findViewById(R.id.skbWindowSize);
        skbWindowSize.setProgress(objSetting.FFTWindowSize);

        skbSampleRate.setOnSeekBarChangeListener(this);
        skbWindowSize.setOnSeekBarChangeListener(this);
        updateSeekBar();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            Sensor objSensor = event.sensor;

            if (objSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                long lngNow = System.currentTimeMillis();

                // Only add new point to graph if time has passed the update interval
                if ((lngNow - lngLastTime) > 1000 / objSetting.SampleRate) {
                    // Get x, y, z and calculate magnitude, then add each of them to their respective entry list
                    ++intEntryCount;
                    lngLastTime = lngNow;
                    float x = event.values[0];
                    addDataToList(lstX, x);
                    float y = event.values[1];
                    addDataToList(lstY, y);
                    float z = event.values[2];
                    addDataToList(lstZ, z);
                    float magnitude = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
                    addDataToList(lstMagnitude, magnitude);
                    lstAllMagnitude.add(new Entry(lstAllMagnitude.size(), magnitude));
                    updateChart();
                }
            }
        } catch (Exception ex) {
            Log.e("onSensorChanged", "Exception", ex);
        }
    }

    // Wrap adding data to entry list
    private void addDataToList(List<Entry> lstEntry, float fltValue) {
        // Only keep the latest entries in the chart
        if (lstEntry.size() == MAX_DATA_COUNT) {
            lstEntry.remove(0);
        }

        lstEntry.add(new Entry(intEntryCount, fltValue));
    }

    //region Initialise chart

    private void initialiseChart() {
        LineChart objChart = (LineChart) findViewById(R.id.chart);
        objChart.setTouchEnabled(false);
        objChart.setBackgroundColor(Color.BLACK);
        objChart.setDrawGridBackground(false);
        XAxis objXAxis = objChart.getXAxis();
        objXAxis.setTextColor(Color.rgb(255, 181, 71));
        YAxis objYAxis = objChart.getAxisLeft();
        objYAxis.setTextColor(Color.rgb(255, 181, 71));

        // Set Axis
        setAxis(objChart);
        setLegend(objChart);

        LineChart objFFT = (LineChart) findViewById(R.id.fft);
        objFFT.setTouchEnabled(false);
        objFFT.setDrawGridBackground(false);
        objFFT.setDescription(null);
        setAxis(objFFT);
    }

    private void setAxis(LineChart objChart) {
        // X Axis
        XAxis objXAxis = objChart.getXAxis();
        objXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        objXAxis.setDrawAxisLine(true);
        objXAxis.setDrawGridLines(false);
        objXAxis.setValueFormatter(this);

        // Y Axis
        YAxis objYAxis = objChart.getAxisLeft();
        objYAxis.setDrawAxisLine(true);
        objYAxis.setDrawGridLines(false);
        objYAxis.setValueFormatter(this);

        objChart.getAxisRight().setEnabled(false);
    }

    private void setLegend(LineChart objChart) {
        Legend objLegend = objChart.getLegend();
        objLegend.setTextColor(Color.WHITE);
        objLegend.setTextSize(15);
        objLegend.setXEntrySpace(20);
        LegendEntry objLegendEntryX = getLegendEntry(Color.RED, "X");
        LegendEntry objLegendEntryY = getLegendEntry(Color.GREEN, "Y");
        LegendEntry objLegendEntryZ = getLegendEntry(Color.rgb(66, 161, 244), "Z");
        LegendEntry objLegendEntryMagnitude = getLegendEntry(Color.WHITE, "Magnitude");
        objLegend.setCustom(new LegendEntry[]{objLegendEntryX, objLegendEntryY, objLegendEntryZ, objLegendEntryMagnitude});
    }

    // Wrap create legend entry
    private LegendEntry getLegendEntry(int intColor, String strLabel) {
        LegendEntry objLegendEntry = new LegendEntry();
        objLegendEntry.formColor = intColor;
        objLegendEntry.label = strLabel;
        return objLegendEntry;
    }

    // endregion

    private void updateChart() {
        LineChart objChart = (LineChart) findViewById(R.id.chart);
        LineData objLineData = new LineData();
        addDataSets(objLineData, lstX, "X", Color.RED);
        addDataSets(objLineData, lstY, "Y", Color.GREEN);
        addDataSets(objLineData, lstZ, "Z", Color.rgb(66, 161, 244));
        addDataSets(objLineData, lstMagnitude, "Magnitude", Color.WHITE);
        objChart.setData(objLineData);
        objChart.notifyDataSetChanged();
        objChart.invalidate();
        updateFFT();
    }

    private void updateFFT() {
        int n = (int) Math.pow(2, objSetting.FFTWindowSize);

        double[] x = new double[n];
        int intStart = lstAllMagnitude.size() - n;

        if (intStart < 0) {
            intStart = 0;
        }

        int intIndex = 0;

        for (int i = intStart; i < lstAllMagnitude.size() && i < n; ++i) {
            x[intIndex] = lstAllMagnitude.get(i).getY();
            ++intIndex;
        }

        double[] y = new double[n];
        FFT objFFT = new FFT(n);
        objFFT.fft(x, y);
        List<Entry> lstFFT = new ArrayList<>();

        for (int i = 0; i < x.length; ++i) {
            Entry objEntry = new Entry();
            objEntry.setX(i + 1);
            float magnitude = (float) Math.sqrt(Math.pow(x[i], 2) + Math.pow(y[i], 2));
            objEntry.setY(magnitude);
            lstFFT.add(objEntry);
        }

        LineChart objFFTChart = (LineChart) findViewById(R.id.fft);
        LineData objFFTData = new LineData();
        addDataSets(objFFTData, lstFFT, "Magnitude", Color.MAGENTA);
        objFFTChart.setData(objFFTData);
        objFFTChart.notifyDataSetChanged();
        objFFTChart.invalidate();
    }

    // Wrap adding data sets
    private void addDataSets(LineData objLineData, List<Entry> lstData, String strLabel, int intColor) {
        LineDataSet objDataSet = new LineDataSet(lstData, strLabel);
        objDataSet.setColor(intColor);
        objDataSet.setValueTextColor(intColor);
        objDataSet.setValueTextSize(10);
        objLineData.addDataSet(objDataSet);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public String getFormattedValue(float value, AxisBase axis) {
        try {
            DecimalFormat objDecimalFormat = new DecimalFormat("#,##0");
            return objDecimalFormat.format(value);
        } catch (Exception ex) {
            Log.e("getFormattedValue", "Exception", ex);
            return "";
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        try {
            updateSeekBar();
        } catch (Exception ex) {
            Log.e("onProgressChanged", "Exception", ex);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void updateSeekBar() {
        TextView lblSampleRate = (TextView) findViewById(R.id.lblSampleRate);
        SeekBar skbSampleRate = (SeekBar) findViewById(R.id.skbSampleRate);
        lblSampleRate.setText("" + skbSampleRate.getProgress());
        objSetting.SampleRate = skbSampleRate.getProgress();

        TextView lblWindowSize = (TextView) findViewById(R.id.lblWindowSize);
        SeekBar skbWindowSize = (SeekBar) findViewById(R.id.skbWindowSize);
        lblWindowSize.setText("" + skbWindowSize.getProgress());
        objSetting.FFTWindowSize = skbWindowSize.getProgress();

        // Save setting to shared preferences to use them again at a later time
        saveSetting();
    }

    private void saveSetting() {
        Gson objGson = new Gson();
        String strJSON = objGson.toJson(objSetting);
        SharedPreferences objPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor objEditor = objPreferences.edit();
        objEditor.putString(SETTING, strJSON);
        objEditor.commit();
    }

    @Override
    public void onClick(View v) {
        greyInButton(v);
        int intID = v.getId();

        switch (intID) {
            case R.id.btnSensor:
                Button btnMusic = (Button) findViewById(R.id.btnMusic);
                greyOutButton(btnMusic);
                LinearLayout pnlSensor = (LinearLayout) findViewById(R.id.pnlSensor);
                pnlSensor.setVisibility(View.VISIBLE);
                break;

            case R.id.btnMusic:
                Button btnSensor = (Button) findViewById(R.id.btnSensor);
                greyOutButton(btnSensor);
                pnlSensor = (LinearLayout) findViewById(R.id.pnlSensor);
                pnlSensor.setVisibility(View.INVISIBLE);
                break;

            default:
                break;
        }
    }


    private void greyOutButton(final View objButton) {
        objButton.setAlpha(.5f);
    }

    private void greyInButton(final View objButton) {
        objButton.setAlpha(1f);
    }
}
