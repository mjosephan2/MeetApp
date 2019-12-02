package com.example.meetapp;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.example.meetapp.utility.TimeSlot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Array;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FreeTimeGenerator extends AppCompatActivity implements suggestedTimesRecyclerViewAdapter.ItemClickListener{
    String event_id, duration, date_to, date_from;
    JSONArray rParticipants;
    ArrayList<TimeSlot> freeTimes;
    suggestedTimesRecyclerViewAdapter adapter;
    RecyclerView suggestedTimesRecyclerView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_time_generated);
        Intent intent = getIntent();
        rParticipants = new JSONArray();
        suggestedTimesRecyclerView = findViewById(R.id.suggested_times_rv);
        event_id = intent.getStringExtra("event_id");
        duration = intent.getStringExtra("duration");
        date_to = intent.getStringExtra("date_to");
        date_from = intent.getStringExtra("date_from");
        Log.d("event_id",event_id);
        getDetails(FreeTimeGenerator.this,getString(R.string.api_get_par_busytime),event_id);
    }
    private void getDetails(Context context, String url,String event_id){
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(Request.Method.GET,
                url+event_id,null, new Response.Listener<JSONArray>() {

                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("Response", response.toString());
                        response = rParticipants;
                        populateRv(generateFreeTimes());
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("Error",error.toString());

                    }
                });

        // Access the RequestQueue through your singleton class.
        queue.add(jsonObjectRequest);

    }
    public ArrayList<TimeSlot> generateFreeTimes(){

        String startOfDateRange = date_from;
        String endOfDateRange = date_to;
        int minTime = Integer.valueOf(duration);

//        System.out.println("these are the participants");
//        System.out.println(event.getParticipants());
        JSONArray participants = rParticipants;
        ArrayList<TimeSlot> TimeSlots = generateTimeSlots(startOfDateRange, endOfDateRange);
//        System.out.println("These are the time slots");
//        printTimeSlots(TimeSlots);

        ArrayList<TimeSlot> updatedTimeSlots = updateTimeSlotScores(TimeSlots, participants);
//        System.out.println("These are the updated time slot scores");
//        printTimeSlots(updatedTimeSlots);

        ArrayList<TimeSlot> revisedTimeSlots = reviseTimeSlots(updatedTimeSlots);
//        System.out.println("These are the revised time slot scores");
//        printTimeSlots(revisedTimeSlots);

        ArrayList<TimeSlot> sortedTimeSlots = sortTimeSlots(revisedTimeSlots, minTime);
//        System.out.println("These are the sorted time slots");
//        printTimeSlots(sortedTimeSlots);

        return sortedTimeSlots;
    }

    public ArrayList<TimeSlot> generateTimeSlots(String startOfDateRange, String endOfDateRange){
        Calendar startCal = TimeSlot.convertDateToCalendar(startOfDateRange);
        Calendar endCal = TimeSlot.convertDateToCalendar(endOfDateRange);
        endCal.add(Calendar.DATE, +1);
        ArrayList<TimeSlot> r  = new ArrayList<TimeSlot>();
        int x = 0;
        while (!TimeSlot.isCalEqual(startCal, endCal)){
            TimeSlot ts = new TimeSlot(startCal);
            r.add(ts);
            Calendar nStartCal = (Calendar)startCal.clone();
            nStartCal.add(Calendar.MINUTE, +30);
            startCal = nStartCal;
            x++;
        }
        System.out.println("This is final x: " + x);
        return r;
    }
    private ArrayList<TimeSlot> updateTimeSlotScores (ArrayList<TimeSlot> TimeSlots, JSONArray participants){

        for (TimeSlot ts: TimeSlots){
            Calendar TimeSlotStartTime = ts.getStartTime();

            int x = 0;
            while ( x< participants.length()){
                try {
                    JSONObject participant = participants.getJSONObject(x);
                    String name = participant.getString("username");
                    int priority = participant.getInt("priority");
                    JSONArray schedule = participant.getJSONArray("schedule");

                    int s = 0;
                    while (s < schedule.length()){
                        String startTime = schedule.getJSONObject(s).getString("date_from");
                        String endTime = schedule.getJSONObject(s).getString("date_to");
                        Calendar startCal = TimeSlot.convertTimeToCalendar(startTime);
                        Calendar endCal = TimeSlot.convertTimeToCalendar(endTime);

                        if (TimeSlot.isBetween(startCal, TimeSlotStartTime, endCal)){
                            ts.addAbsentee(name, priority);
                        }
                        s++;
                    }
                } catch (JSONException e) {e.printStackTrace();}
                x++;
            }
        }

        return TimeSlots;
    }

    private ArrayList<TimeSlot> reviseTimeSlots(ArrayList<TimeSlot> TimeSlots){
        ArrayList<TimeSlot> revisedTimeSlots = new ArrayList<TimeSlot>();
        int x = 0;
        while (x < TimeSlots.size()){
            int s = revisedTimeSlots.size();
            if (s != 0){  // if the reviseTimeSlots is not empty...
                if (TimeSlots.get(x).getAbsenteesString().equals(revisedTimeSlots.get(s-1).getAbsenteesString())){
                    revisedTimeSlots.get(s-1).setEndTime(TimeSlots.get(x).getEndTime());
                } else {
                    revisedTimeSlots.add(TimeSlots.get(x));
                }
            } else{
                revisedTimeSlots.add(TimeSlots.get(x));
            }
            x++;
        }
        return revisedTimeSlots;
    }

    private ArrayList<TimeSlot> sortTimeSlots (ArrayList<TimeSlot> TimeSlots, int minTime){
        ArrayList<TimeSlot> sortedTimeSlots = new ArrayList<TimeSlot>();
        int x = 0;
        int s = TimeSlots.size();
        while (x < s){
            int maxScore = -99999999;
            boolean addIntoSortedTimeSlots = false;
            TimeSlot maxTimeSlot = new TimeSlot();
            for (TimeSlot ts: TimeSlots){
                if (ts.getScore() > maxScore && ts.getDuration()>= minTime){
                    maxTimeSlot = ts;
                    maxScore = ts.getScore();
                    addIntoSortedTimeSlots = true;
                }
            }
            if (addIntoSortedTimeSlots){
                sortedTimeSlots.add(maxTimeSlot);
                TimeSlots.remove(maxTimeSlot);
            }
            x++;
        }
        return sortedTimeSlots;
    }


    // test method to log all the time slots
    private void printTimeSlots(ArrayList<TimeSlot> TimeSlots){
        System.out.println("Here are the TimeSlots");
        for (TimeSlot ts: TimeSlots){
            System.out.println(ts.toString());
        }
    }
    @Override
    public void onItemClick(View view, int position) {
        Dialog mDialog = new Dialog(this);
        String[] current  = adapter.getItem(position).toString().replace("\n"," ").split(",");
        Log.d("currentclick", Arrays.toString(current));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        Calendar start = Calendar.getInstance();
        Calendar eventend = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        try {
            start.setTime(sdf.parse(current[0]));
            end.setTime(sdf.parse(current[1]));
            eventend.setTime(sdf.parse(current[0]));
            eventend.add(Calendar.HOUR,Integer.valueOf(duration));

            while( !start.after(end) && !eventend.after(end)){
                Log.v("split timings",start.getTime().toString() +" , " + eventend.getTime().toString());
                start.add(Calendar.MINUTE, 30);
                eventend.add(Calendar.MINUTE,30);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }




    }
    public void populateRv(ArrayList<TimeSlot> data) {
        // set up the RecyclerView

        suggestedTimesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new suggestedTimesRecyclerViewAdapter(this, data);
        adapter.setClickListener(this);
        suggestedTimesRecyclerView.setAdapter(adapter);
    }

    class Timing{
        private String date;
        private String timestart;
        private String timeend;
    }
    class Schedules{
        private String dateFrom;
        private String dateTo;
    }

}