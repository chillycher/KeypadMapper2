/*   Copyright (C) 2010 Nic Roets

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
 */
package org.osm.keypadmapper2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.TimeZone;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

final class Gps implements LocationListener {
	public PowerManager.WakeLock wl;
	public double lon = -200, lat, bearing;
	public int record = 0, osmid = 0;
	public TextView tw = null;
	public RandomAccessFile out, osm;
	public java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public LocationManager lm;

	public void onProviderDisabled(String provider) {
	}

	public void onProviderEnabled(String provider) {
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	public void onLocationChanged(Location location) {
		if (lon < -180 && tw != null)
			tw.setText(R.string.ready);
		lon = location.getLongitude();
		lat = location.getLatitude();
		bearing = location.getBearing();
		try {
			out.seek(out.getFilePointer() - 24); // Overwrite </trkseg>\n</trk>\n</gpx>\n
			out.writeBytes("<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">\n"
					+ (location.hasAltitude() ? "<ele>" + (int) location.getAltitude() + "</ele>\n" : "") + "<time>"
					+ (f.format(new java.util.Date(location.getTime()))) + "Z</time>\n</trkpt>\n</trkseg>\n</trk>\n</gpx>\n");
		} catch (IOException e) {
		}
	}
}

public class KeypadMapper2Activity extends Activity implements OnClickListener {
	final boolean wildlife = false;
	private String val = "";
	static Gps gps = null;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.actionmenu, menu);
		return true;
	}

	void SetButtons(ViewGroup btnGroup) {
		for (int i = 0; i < btnGroup.getChildCount(); i++) {
			if (ViewGroup.class.isInstance(btnGroup.getChildAt(i))) {
				SetButtons((ViewGroup) btnGroup.getChildAt(i));
			} else if (Button.class.isInstance(btnGroup.getChildAt(i))) {
				Button button = (Button) btnGroup.getChildAt(i);
				button.setOnClickListener(this);
			} else if (ImageButton.class.isInstance(btnGroup.getChildAt(i))) {
				ImageButton imageButton = (ImageButton) btnGroup.getChildAt(i);
				imageButton.setOnClickListener(this);
			}
		}
	}

	private void Do(double fwd, double left) { // fwd and left are distances in 111111 meter
		try {
			gps.osm.seek(gps.osm.getFilePointer() - 7); // Overwrite </osm>\n
			gps.osm.writeBytes("<node id='"
					+ --gps.osmid
					+ "' visible='true' lat='"
					+ (gps.lat + Math.sin(Math.PI / 180 * gps.bearing) * left + Math.cos(Math.PI / 180 * gps.bearing) * fwd)
					+ "' lon='"
					+ (gps.lon + (Math.sin(Math.PI / 180 * gps.bearing) * fwd - Math.cos(Math.PI / 180 * gps.bearing) * left)
							/ Math.cos(Math.PI / 180 * gps.lat)) + "'>\n <tag k='addr:housenumber' v='" + val + "'/>\n</node>\n</osm>\n");
			val = "";
		} catch (IOException e) {
			val = getString(R.string.errorFileOpen);
		}
	}

	public void onClick(View v) {
		if (v == findViewById(R.id.button_C)) {
			val = "";
		} else if (v == findViewById(R.id.button_DEL)) {
			if (val.length() > 0)
				val = val.substring(0, val.length() - 1);
		} else if (v == findViewById(R.id.button_L)) {
			Do(0, 25.0 / 111111);
		} else if (v == findViewById(R.id.button_F)) {
			Do(50.0 / 111111, 0);
		} else if (v == findViewById(R.id.button_R)) {
			Do(0, -25.0 / 111111);
		} else
			val = val + v.getTag();
		gps.tw.setText(val);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		if (gps != null)
			gps.tw = (TextView) findViewById(R.id.text);
		else {
			gps = new Gps();
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			gps.wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WildlifeSurvey");
			gps.wl.acquire();
			gps.tw = (TextView) findViewById(R.id.text);
			gps.out = null;
			gps.f.setTimeZone(TimeZone.getTimeZone("UTC"));
			gps.lm = (LocationManager) getSystemService(LOCATION_SERVICE);
			try {
				File sd = Environment.getExternalStorageDirectory();
				gps.osm = new RandomAccessFile(new File(sd, "/" + (new Date()).getTime() + ".osm"), "rw");
				gps.osm.writeBytes("<?xml version='1.0' encoding='UTF-8'?>\n" + "<osm version='0.6' generator='KeypadMapper'>\n</osm>\n");
				gps.out = new RandomAccessFile(new File(sd, "/" + (new Date()).getTime() + ".gpx"), "rw");
				gps.out.writeBytes(
						"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
						"<gpx\n" + 
						"version=\"1.0\"\n" + 
						"creator=\"KeypadMapper\"\n" + 
						"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
						"xmlns=\"http://www.topografix.com/GPX/1/0\"\n"	+ 
						"xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\n" + 
						"<trk>\n" + 
						"<trkseg>\n" +
						"</trkseg>\n" +
						"</trk>\n" +
						"</gpx>\n");
			} catch (FileNotFoundException e) {
				gps.tw.setText(R.string.errorFileOpen);
				// Assert.assertNotNull ("Error writing the file!", gps.out);
			} catch (IOException e) {
				gps.tw.setText(R.string.errorFileOpen);
			}
		}
		SetButtons((ViewGroup) findViewById(R.id.buttonGroup));
	}

	@Override
	public void onResume() {
		gps.tw = (TextView) findViewById(R.id.text);
		super.onResume();
		if (gps.record == 0) {
			gps.wl.acquire();
			gps.lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 6f, gps);
			gps.record = 1;
			gps.lon = -200;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		gps.tw = null;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.actionStop:
			gps.wl.release();
			gps.lm.removeUpdates(gps);
			gps.record = 0;
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}
