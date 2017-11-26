package com.movesense.samples.connectivityapisample;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.TimePicker;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {
    private TimeSetListener mCallback;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new TimePickerDialog(getActivity(), this, 13, 37,true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (TimeSetListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement TimeSetListener");
        }
    }

    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        mCallback.onTimeSet(hourOfDay, minute);
    }

    public interface TimeSetListener {
        void onTimeSet(int hourOfDay, int minute);
    }
}
