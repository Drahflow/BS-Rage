package name.drahflow.bsrage;

import android.app.Activity;

import android.os.*;
import android.app.*;
import android.graphics.*;
import android.widget.*;
import android.view.*;
import android.content.*;
import android.util.*;
import android.database.*;
import android.provider.*;
import android.net.*;
import android.text.*;
import android.location.*;
import org.apache.http.impl.client.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.*;
import org.apache.http.util.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Rage extends Activity {
	public final int RES_IMAGE_CAPTURE = 0;
	public final String BRAUNSCHWEIG_URL = "https://www.braunschweig.de/send.php";

	public class MultipartUtility {
		private final String boundary = "===" + System.currentTimeMillis() + "===";
		private static final String LINE_FEED = "\r\n";
		private HttpURLConnection httpConn;
		private String charset;
		private OutputStream outputStream;
		private PrintWriter writer;

		public MultipartUtility(String requestURL, String charset) throws IOException {
			this.charset = charset;

			URL url = new URL(requestURL);
			httpConn = (HttpURLConnection) url.openConnection();
			httpConn.setUseCaches(false);
			httpConn.setDoOutput(true);
			httpConn.setDoInput(true);
			httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			httpConn.setRequestProperty("User-Agent", "BS Rage App");
			outputStream = httpConn.getOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);
		}

		public void addFormField(String name, String value) {
			writer.append("--" + boundary).append(LINE_FEED);
			writer.append("Content-Disposition: form-data; name=\"" + name + "\"").append(LINE_FEED);
			writer.append("Content-Type: text/plain; charset=" + charset).append(LINE_FEED);
			writer.append(LINE_FEED);
			writer.append(value).append(LINE_FEED);
		}

		public void addFilePart(String fieldName, File uploadFile) throws IOException {
			String fileName = uploadFile.getName();
			writer.append("--" + boundary).append(LINE_FEED);
			writer.append("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" +
					fileName + "\"").append(LINE_FEED);
			writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(fileName)).append(LINE_FEED);
			writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
			writer.append(LINE_FEED);
			writer.flush();

			FileInputStream inputStream = new FileInputStream(uploadFile);
			byte[] buffer = new byte[4096];
			int bytesRead = -1;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			outputStream.flush();
			inputStream.close();

			writer.append(LINE_FEED);
		}

		public void addHeaderField(String name, String value) {
			writer.append(name + ": " + value).append(LINE_FEED);
		}

		public String finish() throws IOException {
			StringBuilder ret = new StringBuilder();

			writer.append(LINE_FEED).flush();
			writer.append("--" + boundary + "--").append(LINE_FEED);
			writer.close();

			int status = httpConn.getResponseCode();
			if (status == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
				String line = null;
				while ((line = reader.readLine()) != null) {
					ret.append(line).append("\n");
				}
				reader.close();
				httpConn.disconnect();
			} else {
				throw new IOException("Server returned non-OK status: " + status);
			}

			return ret.toString();
		}
	}

	class RageView extends LinearLayout {
		private Context ctx;

		private EditText first_name;
		private EditText last_name;
		private EditText phone;
		private EditText email;
		private Spinner problem;
		private EditText details;
		private Button photo;
		private ImageView photo_view;
		private Button gps;
		private EditText location;
		private LocationManager locations;
		private Button send;
		private Button reset;

		String captured_image = null;
		Bitmap captured_bmp = null;

		private String[] PROBLEMS = {
			"Straßenschäden",
			"Geh- oder Radwegschäden",
			"Verkehrszeichen/Straßenschilder beschädigt",
			"Straßenbeleuchtung defekt",
			"Ampelanlage defekt",
			"Parkscheinautomat defekt",
			"Brunnenanlage verschmutzt",
			"Öffentliche Toiletten-Anlage verschmutzt, zerstört, beschädigt oder defekt",
			"Pflanzen beschädigt",
			"Baum beschädigt",
			"Spielgerät defekt",
			"Spielplatz verunreinigt",
			"... anderes"
		};
		private String[] PROBLEM_VALUES = {
			"strassenschaeden",
		  "gehweg_radwegschaeden",
		  "verkehrszeichen_beschaedigt",
			"strassenbeleuchtung_defekt",
		 	"ampelanlage_defekt",
		 	"parkscheinautomat_defekt",
			"brunnenanlage_verschmutzt",
		 	"wc_anlage",
			"pflanzen_beschaedigt",
		 	"baum_beschaedigt",
			"spielgeraet_defekt",
		 	"spielplatz_verunreinigt",
			"andere_schaeden"
		};

		public RageView(Context ctx) {
			super(ctx);
			this.ctx = ctx;

			locations = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);

			setOrientation(VERTICAL);

			problem = new Spinner(ctx);
			problem.setAdapter(new SpinnerAdapter() {
				public View getDropDownView(int i, View template, ViewGroup parent) {
					TextView ret = template instanceof TextView?
						(TextView)template: new TextView(RageView.this.ctx);
					ret.setText(PROBLEMS[i]);
					ret.setTextColor(Color.BLACK);
					return ret;
				}
				public View getView(int i, View template, ViewGroup parent) {
					return getDropDownView(i, template, parent);
				}

				public int getCount() { return PROBLEMS.length; }
				public Object getItem(int i) { return PROBLEMS[i]; }
				public long getItemId(int i) { return i; }
				public boolean hasStableIds() { return true; }
				public boolean isEmpty() { return false; }
				public int getViewTypeCount() { return 1; }
				public int getItemViewType(int i) { return 0; }

				public void registerDataSetObserver(DataSetObserver o) { }
				public void unregisterDataSetObserver(DataSetObserver o) { }
			});
			addView(problem);
			details = makeInputField("Details: ");

			first_name = makeInputField("Vorname: ");
			last_name = makeInputField("Nachname: ");
			phone = makeInputField("Telefon: ");
			email = makeInputField("Email: ");

			{
				LinearLayout row = new LinearLayout(ctx);
				row.setOrientation(HORIZONTAL);

				TextView lbl = new TextView(ctx);
				lbl.setText("Standort: ");
				row.addView(lbl);

				gps = new Button(ctx);
				gps.setText("Hier");
				gps.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						locations.requestSingleUpdate(LocationManager.GPS_PROVIDER,
						new LocationListener() {
							public void onLocationChanged(Location location) {
								RageView.this.location.setText("Ort (GPS): " + location);
							}
							public void onProviderDisabled(String provider) { }
							public void onProviderEnabled(String provider) { }
							public void onStatusChanged(String provider, int status, Bundle extras) { }
						}, null);

						gps.setText("Standort wird ermittelt...");
					}
				});
				row.addView(gps);

				location = new EditText(ctx);
				row.addView(location, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

				addView(row);
			}

			photo = new Button(ctx);
			photo.setText("Foto hinzufügen");
			photo.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if(photo.getText().toString().equals("Foto hinzufügen")) {
						Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); 
						RageView.this.captured_image = System.currentTimeMillis() + ".jpg";
						File file = new File(Environment.getExternalStorageDirectory(), captured_image); 
						captured_image = file.getAbsolutePath();
						Uri outputFileUri = Uri.fromFile(file); 
						intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri); 
						intent.putExtra("return-data", true);
						Rage.this.startActivityForResult(intent, RES_IMAGE_CAPTURE);
					} else {
						File file = new File(Environment.getExternalStorageDirectory(), captured_image); 
						file.delete();
						captured_image = null;
						photo_view.setImageBitmap(null);
						photo.setText("Foto hinzufügen");
					}
				}
			});
			addView(photo);

			photo_view = new ImageView(ctx);
			photo_view.setAdjustViewBounds(true);
			addView(photo_view);

			send = new Button(ctx);
			send.setText("Absenden");
			send.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					send.setEnabled(false);
					submit();
				}
			});
			addView(send);

			reset = new Button(ctx);
			reset.setText("Reset");
			reset.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					send.setBackgroundResource(android.R.drawable.btn_default);
					send.setEnabled(true);
					send.setText("Absenden");
				}
			});
			addView(reset);
		}

		private EditText makeInputField(String label) {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(HORIZONTAL);

			TextView lbl = new TextView(ctx);
			lbl.setText(label);
			EditText edit = new EditText(ctx);

			row.addView(lbl);
			row.addView(edit, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

			addView(row);

			return edit;
		}

		public void setCapturedPhoto(Bitmap bmp) {
			photo_view.setImageBitmap(bmp);
		  photo.setText("Foto löschen");
		}

		public String getCapturedImage() {
			return captured_image;
		}

		private void submit() {
			try {
				MultipartUtility utility = new MultipartUtility(BRAUNSCHWEIG_URL, "windows-1252");
				utility.addFormField("tpl", "schadensmeldung_iub.php");
				String selectedProblem = ((TextView)problem.getSelectedView()).getText().toString();
				for(int i = 0; i < PROBLEMS.length; ++i) {
					if(PROBLEMS[i].equals(selectedProblem)) {
						utility.addFormField("schadensmeldung", PROBLEM_VALUES[i]);
					}
				}
				utility.addFormField("copytosender", "true");
				utility.addFormField("antwort", "antwort");
				if(!send.getText().toString().equals("Absenden")) {
					utility.addFormField("send", "true");
					utility.addFormField("ack", "no");
					utility.addFormField("submit", "Daten senden");
				}
				utility.addFormField("Datum", new Date().toString());
				utility.addFormField("Vorname", first_name.getText().toString());
				utility.addFormField("Name", last_name.getText().toString());
				utility.addFormField("Telefon", phone.getText().toString());
				utility.addFormField("E-Mail", email.getText().toString());
				if(details.getText().toString().length() > 10) {
					utility.addFormField("Beschreibung", details.getText().toString() + "\n\n" + "Ort: " + location.getText().toString());
				}
				if(captured_image != null) {
					utility.addFilePart("Anhang", new File(captured_image));
				}

				String result = utility.finish();
				result = result.replaceAll("\n\n", "\n");
				result = result.replaceAll("\n\n", "\n");
				result = result.replaceAll("\n", "<br />");
				send.setText(Html.fromHtml(result));

				if(result.contains("Stelle gesendet werden sollen")) {
					send.setBackgroundResource(android.R.drawable.btn_default);
				} else if(result.contains("Stelle gesendet worden")) {
					send.setBackgroundColor(Color.GREEN);
					send.setEnabled(false);
				} else {
					send.setBackgroundColor(Color.RED);
				}
			} catch (ClientProtocolException e) {
				send.setText(e.toString());
			} catch (IOException e) {
				send.setText(e.toString());
			}
		}
	}

	private RageView view;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		view = new RageView(this);

		ScrollView scroll = new ScrollView(this);
		scroll.addView(view);

		setContentView(scroll);

	  SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		view.first_name.setText(prefs.getString("first_name", ""));
		view.last_name.setText(prefs.getString("last_name", ""));
		view.phone.setText(prefs.getString("phone", ""));
		view.email.setText(prefs.getString("email", ""));
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) { 
			case RES_IMAGE_CAPTURE: 
				switch(resultCode) {
					case 0: break; // user cancelled
					case -1: Bitmap captured_bmp = BitmapFactory.decodeFile(view.getCapturedImage());
									 view.setCapturedPhoto(captured_bmp);
									 break;
				}
				break;
		}
	}

	@Override protected void onPause() {
		super.onPause();

	  SharedPreferences.Editor prefs = getPreferences(MODE_PRIVATE).edit();
		prefs.putString("first_name", view.first_name.getText().toString());
		prefs.putString("last_name", view.last_name.getText().toString());
		prefs.putString("phone", view.phone.getText().toString());
		prefs.putString("email", view.email.getText().toString());
		prefs.commit();
	}
}
