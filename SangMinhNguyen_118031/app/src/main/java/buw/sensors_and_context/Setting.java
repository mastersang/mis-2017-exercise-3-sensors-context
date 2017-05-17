package buw.sensors_and_context;

/**
 * Created by ADMIN on 5/17/2017.
 */

public class Setting {
    public int SampleRate;
    public int FFTWindowSize;

    public static Setting getDefaultSetting() {
        Setting objSetting = new Setting();
        objSetting.SampleRate = 1;
        objSetting.FFTWindowSize = 5;
        return objSetting;
    }
}
