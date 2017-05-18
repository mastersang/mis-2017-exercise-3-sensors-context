package buw.sensors_and_context;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.IdRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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

import java.security.Permission;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener, SeekBar.OnSeekBarChangeListener,
        IAxisValueFormatter, View.OnClickListener, RadioGroup.OnCheckedChangeListener, LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback {
    private static int MAX_DATA_COUNT = 10;
    private static int MIN_JOGGING_SPEED = 5;
    private static int MIN_BIKING_SPEED = 11;
    private static int MIN_MAX_SPEED = 41;
    private static String SETTING = "SETTING";
    private static String SELECT_JOGGING_SONG = "SELECT JOGGING SONG";

    private SensorManager objSensorManager;
    private long lngLastTime = System.currentTimeMillis();
    private int intEntryCount = 0;
    private int intCurrentSongIndex = 0;
    private double dblCurrentXSpeed = 0;
    private double dblCurrentYSpeed = 0;
    private double dblCurrentZSpeed = 0;
    private double dblCurrentAccelerometerSpeed = 0;
    private double dblCurrentLocationSpeed = -1;
    private List<Entry> lstX = new ArrayList<>();
    private List<Entry> lstY = new ArrayList<>();
    private List<Entry> lstZ = new ArrayList<>();
    private List<Entry> lstMagnitude = new ArrayList<>();
    private List<Entry> lstAllMagnitude = new ArrayList<>();
    private List<Song> lstSong;
    private Setting objSetting;
    private Song objJoggingSong;
    private Song objBikingSong;
    private Song objCurrentSong;
    private MediaPlayer objMediaPlayer;
    private boolean bolSelectedSongs = false;

    // Toast wrapper
    private void showToast(final String strMessage) {
        Toast objToast = Toast.makeText(this, strMessage, Toast.LENGTH_SHORT);
        objToast.show();
    }

    //region Initialise

    private void initialisePermissions() {
        if (!checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                !checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 1306);
        } else {
            LocationManager objLocationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
            objLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
            initialise();
        }
    }

    //Wrapper
    private boolean checkPermission(String strPermission) {
        return ContextCompat.checkSelfPermission(this.getApplicationContext(), strPermission) == PackageManager.PERMISSION_GRANTED;
    }

    private void initialise() {
        initialiseTabButtons();
        initialiseSetting();
        initialiseSensor();
        initialiseSeekBars();
        initialiseChart();
        initialiseSongList();
    }

    private void initialiseTabButtons() {
        Button btnSensor = (Button) findViewById(R.id.btnSensor);
        initialiseButton(btnSensor);
        Button btnMusic = (Button) findViewById(R.id.btnMusic);
        initialiseButton(btnMusic);
        onClick(btnSensor);
    }

    private void initialiseButton(Button btn) {
        btn.setBackgroundColor(Color.BLUE);
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

    private void initialiseSensor() {
        objSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor objSensor = objSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        objSensorManager.registerListener(this, objSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void initialiseSeekBars() {
        SeekBar skbSampleRate = (SeekBar) findViewById(R.id.skbSampleRate);
        setSeekBar(skbSampleRate, objSetting.SampleRate, 0);
        SeekBar skbWindowSize = (SeekBar) findViewById(R.id.skbWindowSize);
        setSeekBar(skbWindowSize, objSetting.FFTWindowSize, 0);
        SeekBar skbJoggingSpeed = (SeekBar) findViewById(R.id.skbJoggingSpeed);
        setSeekBar(skbJoggingSpeed, objSetting.JoggingSpeed - MIN_JOGGING_SPEED, 15 - MIN_JOGGING_SPEED);
        SeekBar skbBikingSpeed = (SeekBar) findViewById(R.id.skbBikingSpeed);
        setSeekBar(skbBikingSpeed, objSetting.BikingSpeed - MIN_BIKING_SPEED, 40 - MIN_BIKING_SPEED);
        SeekBar skbMaxSpeed = (SeekBar) findViewById(R.id.skbMaxSpeed);
        setSeekBar(skbMaxSpeed, objSetting.MaxSpeed - MIN_MAX_SPEED, 100 - MIN_MAX_SPEED);
        updateSeekBar(null);
        skbSampleRate.setOnSeekBarChangeListener(this);
        skbWindowSize.setOnSeekBarChangeListener(this);
        skbJoggingSpeed.setOnSeekBarChangeListener(this);
        skbBikingSpeed.setOnSeekBarChangeListener(this);
        skbMaxSpeed.setOnSeekBarChangeListener(this);
    }

    // Wrapper
    private void setSeekBar(SeekBar skb, int intProgress, int intMax) {
        skb.setProgress(intProgress);

        if (intMax > 0) {
            skb.setMax(intMax);
        }
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

    //region Initialise song list

    private void initialiseSongList() {
        RadioGroup rdgSongList = (RadioGroup) findViewById(R.id.rdgSongList);
        rdgSongList.setOnCheckedChangeListener(this);
        getSongs();

        for (int i = 0; i < lstSong.size(); ++i) {
            Song objSong = lstSong.get(i);
            int intID = View.generateViewId();
            LayoutInflater objLayoutInflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View objView = objLayoutInflater.inflate(R.layout.song_list_item, null);
            objSong.RadioButtonID = intID;
            RadioButton rdbSelect = (RadioButton) objView.findViewById(R.id.rdbSelect);
            rdbSelect.setId(intID);
            rdbSelect.setText(objSong.SongName);
            ViewGroup objViewGroup = (ViewGroup) findViewById(R.id.rdgSongList);
            objViewGroup.addView(objView, i, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void getSongs() {
        Cursor cursor = getCursor();
        lstSong = new ArrayList<>();

        try {
            while (cursor.moveToNext()) {
                Song objSong = new Song();
                objSong.SongName = cursor.getString(2);
                objSong.ID = cursor.getInt(0);
                lstSong.add(objSong);
            }
        } catch (Exception ex) {
            Log.e("getSongs", "Exception", ex);
        } finally {
            cursor.close();
        }
    }

    private Cursor getCursor() {
        String[] arrProjection = getProjection();
        String strSelection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        Cursor objCursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrProjection,
                strSelection,
                null,
                null);

        return objCursor;
    }

    private String[] getProjection() {
        String[] arrProjection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION
        };

        return arrProjection;
    }

    //endregion

    //end region

    //region Seekbars

    private void updateSeekBar(SeekBar seekBar) {
        int intProgress = updateSeekBarLabel(R.id.lblSampleRate, R.id.skbSampleRate, 0);
        objSetting.SampleRate = intProgress;
        intProgress = updateSeekBarLabel(R.id.lblWindowSize, R.id.skbWindowSize, 0);
        objSetting.FFTWindowSize = intProgress;
        intProgress = updateSeekBarLabel(R.id.lblJoggingSpeed, R.id.skbJoggingSpeed, MIN_JOGGING_SPEED);
        objSetting.JoggingSpeed = intProgress;
        intProgress = updateSeekBarLabel(R.id.lblBikingSpeed, R.id.skbBikingSpeed, MIN_BIKING_SPEED);
        objSetting.BikingSpeed = intProgress;
        intProgress = updateSeekBarLabel(R.id.lblMaxSpeed, R.id.skbMaxSpeed, MIN_MAX_SPEED);
        objSetting.MaxSpeed = intProgress;

        // Save setting to shared preferences to use them again at a later time
        saveSetting();

        // If user changes sample rate or window size, redraw charts
        if (seekBar != null && (seekBar.getId() == R.id.lblSampleRate || seekBar.getId() == R.id.lblWindowSize)) {
            updateChart();
        }
    }

    // Wrapper
    private void checkAndSetMin(SeekBar seekBar, int intMin) {
        if (seekBar.getProgress() < intMin) {
            seekBar.setProgress(intMin);
        }
    }

    private int updateSeekBarLabel(int intLabelID, int intSeekBarID, int intMin) {
        TextView lbl = (TextView) findViewById(intLabelID);
        SeekBar skb = (SeekBar) findViewById(intSeekBarID);
        int intProgress = skb.getProgress() + intMin;
        lbl.setText("" + intProgress);
        return intProgress;
    }

    private void saveSetting() {
        Gson objGson = new Gson();
        String strJSON = objGson.toJson(objSetting);
        SharedPreferences objPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor objEditor = objPreferences.edit();
        objEditor.putString(SETTING, strJSON);
        objEditor.commit();
    }

    //endregion

    //region Buttons

    private void greyOutButton(final View objButton) {
        objButton.setAlpha(.5f);
    }

    private void greyInButton(final View objButton) {
        objButton.setAlpha(1f);
    }

    private void clickButton(View v) {
        int intID = v.getId();

        if (intID == R.id.btnSelectSongs) {
            selectSongs();
        } else {
            selectTab(v, intID);
        }
    }

    private void selectTab(View v, int intID) {
        greyInButton(v);
        LinearLayout pnlSensor = (LinearLayout) findViewById(R.id.pnlSensor);
        LinearLayout pnlMusic = (LinearLayout) findViewById(R.id.pnlMusic);
        Button btnMusic = (Button) findViewById(R.id.btnMusic);
        Button btnSensor = (Button) findViewById(R.id.btnSensor);

        switch (intID) {
            case R.id.btnSensor:
                greyOutButton(btnMusic);
                pnlSensor.setVisibility(View.VISIBLE);
                pnlMusic.setVisibility(View.INVISIBLE);
                break;

            case R.id.btnMusic:
                greyOutButton(btnSensor);
                pnlSensor.setVisibility(View.INVISIBLE);
                pnlMusic.setVisibility(View.VISIBLE);
                break;

            default:
                break;
        }
    }

    private void selectSongs() {
        Button btnSelectSongs = (Button) findViewById(R.id.btnSelectSongs);

        if (btnSelectSongs.getText().toString().equalsIgnoreCase(SELECT_JOGGING_SONG)) {
            if (objJoggingSong == null) {
                showToast("Please select at least 1 song!");
            } else {
                btnSelectSongs.setText("Select biking song");
                RadioGroup rdgSongList = (RadioGroup) findViewById(R.id.rdgSongList);
                rdgSongList.clearCheck();
                objBikingSong = null;
            }
        } else if (objBikingSong == null) {
            showToast("Please select at least 1 song!");
        } else {
            LinearLayout pnlSelectSong = (LinearLayout) findViewById(R.id.pnlSelectSong);
            pnlSelectSong.setVisibility(View.INVISIBLE);
            LinearLayout pnlStatus = (LinearLayout) findViewById(R.id.pnlStatus);
            pnlStatus.setVisibility(View.VISIBLE);
            bolSelectedSongs = true;
        }
    }

    //endregion

    //region Sensor

    private void sensorChanged(SensorEvent event) {
        long lngNow = System.currentTimeMillis();
        long lngTimeDifference = lngNow - lngLastTime;

        if (objSetting.SampleRate > 0) {
            // Only add new point to graph if time has passed the update interval
            if (lngTimeDifference > 1000 / objSetting.SampleRate) {
                lngLastTime = lngNow;
                addNewEntriesToChart(event);

                if (intEntryCount > 0) {
                    calculateCurrentSpeeds(lngTimeDifference);
                    checkAndPlayMusic();
                }
            }
        } else {
            updateFFT();
        }
    }

    private void checkAndPlayMusic() {
        // Only play music if user has selected songs
        if (bolSelectedSongs) {
            double dblSpeed = dblCurrentAccelerometerSpeed;

            if (dblCurrentLocationSpeed != -1) {
                dblSpeed = dblCurrentLocationSpeed;
            }

            if (dblSpeed < objSetting.JoggingSpeed || dblSpeed >= objSetting.MaxSpeed) { // Sitting or casually jogging
                pauseMusic();
            } else {
                if (objMediaPlayer != null && !objMediaPlayer.isPlaying()) {
                    objMediaPlayer.start();
                }

                if (dblSpeed < objSetting.BikingSpeed) { // Jogging
                    if (objCurrentSong != objJoggingSong) {
                        playSong(objJoggingSong);
                    }
                } else if (dblSpeed < objSetting.MaxSpeed) { // Biking
                    if (objCurrentSong != objBikingSong) {
                        playSong(objBikingSong);
                    }
                }
            }
        }
    }

    private void pauseMusic() {
        TextView lblCurrentSong = (TextView) findViewById(R.id.lblCurrentSong);

        if (objMediaPlayer != null) {
            objMediaPlayer.pause();
            lblCurrentSong.setText("Pausing song [" + objCurrentSong.SongName + "]");
        } else {
            lblCurrentSong.setText("Not playing any song");
        }
    }

    private void playSong(Song objSong) {
        if (objMediaPlayer != null) {
            objMediaPlayer.stop();
            objMediaPlayer.release();
            objMediaPlayer = null;
            objCurrentSong = null;
        }

        Uri objURI = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, objSong.ID);
        objMediaPlayer = MediaPlayer.create(this, objURI);
        objMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        objMediaPlayer.setLooping(true);
        objMediaPlayer.start();
        TextView lblCurrentSong = (TextView) findViewById(R.id.lblCurrentSong);
        lblCurrentSong.setText("Playing song [" + objSong.SongName + "]");
        objCurrentSong = objSong;
    }

    private void calculateCurrentSpeeds(long lngTimeDifference) {
        // calculate current speed using last acceleration on each axis and time passed, then apply formula v = sqrt(vx^2 + vy^2 + vz^2)
        double dblSecond = lngTimeDifference / 1000.1;
        dblCurrentXSpeed += lstX.get(lstX.size() - 1).getY() * dblSecond * 3.6;
        dblCurrentYSpeed += lstX.get(lstX.size() - 1).getY() * dblSecond * 3.6;
        dblCurrentZSpeed += lstX.get(lstX.size() - 1).getY() * dblSecond * 3.6;
        dblCurrentAccelerometerSpeed = Math.sqrt(
                Math.pow(dblCurrentXSpeed, 2)
                        + Math.pow(dblCurrentYSpeed, 2)
                        + Math.pow(dblCurrentZSpeed, 2));
        TextView lblCurrentSpeed = (TextView) findViewById(R.id.lblCurrentSpeed);
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.000");
        double dblLocationSpeed = dblCurrentLocationSpeed;

        if (dblLocationSpeed == -1) {
            dblLocationSpeed = 0;
        }

        lblCurrentSpeed.setText("Calculated speed: " + decimalFormat.format(dblCurrentAccelerometerSpeed) + " km/h\r\n" +
                "Location speed: " + decimalFormat.format(dblLocationSpeed) + " km/h");
    }

    private void addNewEntriesToChart(SensorEvent event) {
        // Get x, y, z and calculate magnitude, then add each of them to their respective entry list
        ++intEntryCount;
        float x = event.values[0];
        addDataToList(lstX, x);
        float y = event.values[1];
        addDataToList(lstY, y);
        float z = event.values[2] - 9.80991f;
        addDataToList(lstZ, z);
        float magnitude = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
        addDataToList(lstMagnitude, magnitude);
        lstAllMagnitude.add(new Entry(lstAllMagnitude.size(), magnitude));
        updateChart();
    }

    // Wrap adding data to entry list
    private void addDataToList(List<Entry> lstEntry, float fltValue) {
        // Only keep the latest entries in the chart
        if (lstEntry.size() == MAX_DATA_COUNT) {
            lstEntry.remove(0);
        }

        lstEntry.add(new Entry(intEntryCount, fltValue));
    }

    //region Update charts

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

    //endregion

    //endregion

    //region Events

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            initialisePermissions();
        } catch (Exception ex) {
            Log.e("onCreate", "Exception", ex);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            Sensor objSensor = event.sensor;

            if (objSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                sensorChanged(event);
            }
        } catch (Exception ex) {
            Log.e("onSensorChanged", "Exception", ex);
        }
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
            updateSeekBar(seekBar);
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

    @Override
    public void onClick(View v) {
        try {
            clickButton(v);
        } catch (Exception ex) {
            Log.e("onClick", "Exception", ex);
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        try {
            Button btnSelectSongs = (Button) findViewById(R.id.btnSelectSongs);

            for (Song objSong : lstSong) {
                if (objSong.RadioButtonID == checkedId) {
                    if (btnSelectSongs.getText().toString().equalsIgnoreCase(SELECT_JOGGING_SONG)) {
                        objJoggingSong = objSong;
                    } else {
                        objBikingSong = objSong;
                    }

                    break;
                }
            }
        } catch (Exception ex) {
            Log.e("onCheckedChanged", "Exception", ex);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasSpeed()) {
            dblCurrentLocationSpeed = location.getSpeed() * 3.6;
        } else {
            dblCurrentLocationSpeed = -1;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        try {
            boolean bolGrantedAll = true;

            for (int i : grantResults) {
                if (i == PackageManager.PERMISSION_DENIED) {
                    showToast("Please grant the app all the required permissions!");
                    bolGrantedAll = false;
                    break;
                }
            }

            if (bolGrantedAll) {
                initialise();
            }
        } catch (Exception ex) {
            Log.e("onRequestPermissions", "Exception", ex);
        }
    }

    //endregion
}
