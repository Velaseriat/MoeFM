package com.avanti.moefm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import android.app.Activity;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import com.gracenote.mmid.MobileSDK.GNConfig;
import com.gracenote.mmid.MobileSDK.GNOperations;
import com.gracenote.mmid.MobileSDK.GNSearchResponse;
import com.gracenote.mmid.MobileSDK.GNSearchResult;
import com.gracenote.mmid.MobileSDK.GNSearchResultReady;

public class MoeFMMainActivity extends Activity {
	private StreamMedia streamMedia;
	private SongFinishedListener songFinishedListener;
	private String currentSongString, currentArtworkString;
	//="http://nyan.90g.org/e/1/50/9f755b639fa518e7f5580b648c7b2f60.mp3";
	//= "http://moefou.90g.org/wiki_cover/000/00/29/000002938.jpg";
	private TextView title;
	private TextView artist;
	private TextView album;
	private TextView year;
	private TextView genre;
	private final String SITE_URL = "http://moe.fm/listen";
	private GNConfig config;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//Detect an sd card if not local
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_moe_fmmain);
		GetNewURL GNU = new GetNewURL();
		GNU.start();
		config = GNConfig.init("6553600-0C36743120EF5914BD597231A582BC68",this.getApplicationContext());
		songFinishedListener = new SongFinishedListener();
		title = (TextView) findViewById(R.id.titleText);
		artist = (TextView) findViewById(R.id.artistText);
		album = (TextView) findViewById(R.id.albumText);
		year = (TextView) findViewById(R.id.yearText);
		genre  = (TextView) findViewById(R.id.genreText);
		try {
			GNU.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		streamMedia = new StreamMedia(currentSongString, currentArtworkString, songFinishedListener, config);
		streamMedia.setArtworkView((WebView)findViewById(R.id.artworkView));
		streamMedia.initializeMMP();
		new DownloadTempFileForTagging().start();
	}	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	    //don't reload the current page when the orientation is changed
	    //Log.d(TAG, "onConfigurationChanged() Called");
	    super.onConfigurationChanged(newConfig);
	}
	public void play(View v){
		if (!streamMedia.isPlaying)
			streamMedia.playMMP();
		else
			streamMedia.pauseMMP();
	}
	public void save(View v){
		streamMedia.downloadMP3(null);
		//Log.i("Download: ", "Save function invoked");
	}
	public void next(View v){
		playNextSong();
	}
	class GetNewURL extends Thread{
		public void run(){
			try {
				URL sURL = new URL(SITE_URL);
				//Log.i("URL: ", SITE_URL);
				InputStream is = (InputStream) sURL.getContent();
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line = null;
				String mp3URL = "", jpgURL ="";
				int mp3Pos = 0, jpgPos = 0;

				while((line = br.readLine()) != null){
					if (line.contains(".mp3") || line.contains(".jpg")){
						mp3Pos = line.indexOf(".mp3");
						jpgPos = line.indexOf(".jpg", line.indexOf("\"large\":\"http"));
						for (int i = mp3Pos; i > 0; i--){
							if (line.substring(i, i+4).equals("http")){
								mp3URL = line.substring(i, mp3Pos +4).replaceAll("\\\\/", "/");
								currentSongString = mp3URL;
								//Log.i("HTML:", "MP3 url: " +  mp3URL);
								break;
							}
						}
						for (int i = jpgPos; i > 0; i--){
							if (line.substring(i, i+4).equals("http")){
								jpgURL = line.substring(i, jpgPos +4).replaceAll("\\\\/", "/");
								currentArtworkString = jpgURL;
								//Log.i("HTML:","JPG url: " + jpgURL);
								break;
							}
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	class DownloadTempFileForTagging extends Thread{
		public void run(){
			try {
				URL url = new URL(currentSongString);
				URLConnection conn = url.openConnection();
				conn.setUseCaches(false);
				// get the filename
				int lastSlash = url.toString().lastIndexOf('/');
				String outputMP3 = "file.bin";
				if(lastSlash >=0)
					outputMP3 = url.toString().substring(lastSlash + 1);
				if(outputMP3.equals(""))
					outputMP3 = "test.bin";
				// start download
				BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MoeFM/").mkdirs();
				File mp3File = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MoeFM/" + "temp" +  outputMP3);
				FileOutputStream fos = new FileOutputStream(mp3File);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				byte[] data = new byte[8192];
				int bytesRead = 0;
				while((bytesRead = bis.read(data, 0, data.length)) >= 0)
					bos.write(data, 0, bytesRead);
				bos.close();
				fos.close();
				bis.close();
				MP3SearchResultReady searchResultReady = new MP3SearchResultReady(mp3File);
				//Log.i("Gracenote:", "Created GN Object");
				searchResultReady.fingerprintFromFile();
				//Log.i("Gracenote:", "Finished creating GN Object of length: " + mp3File.length());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	class MP3SearchResultReady implements GNSearchResultReady{
		File mp3File;
		public MP3SearchResultReady(File mp3File){
			this.mp3File = mp3File;
		}
		//boolean fingerprintFromFile(File file){
		void fingerprintFromFile(){
			GNOperations.recognizeMIDFileFromFile (this, config, this.mp3File.getAbsolutePath());
		}
		@Override
		public void GNResultReady(GNSearchResult result) {
			GNSearchResponse response = result.getBestResponse();
			//TODO no matches
			if (response != null){
				//Log.i("Marker:", "Result is empty" );
				//else{
				if(response.getTrackTitle() != null){
					//Log.i("Tagging:", "Title:" + response.getTrackTitle());
					title.setText(response.getTrackTitle());
				}
				if(response.getAlbumArtist() != null){
					//Log.i("Tagging:", "Artist:" + response.getAlbumArtist());
					artist.setText(response.getAlbumArtist());
				}
				if(response.getAlbumTitle() != null){
					//Log.i("Tagging:", "Album:" + response.getAlbumTitle());
					album.setText(response.getAlbumTitle());
				}
				if(response.getAlbumReleaseYear() != null){
					//Log.i("Tagging:", "Year:" + response.getAlbumReleaseYear());
					year.setText(response.getAlbumReleaseYear());
				}
				if(response.getAlbumGenre()[0].getData() != null){
					//Log.i("Tagging:", "Genre:" + response.getAlbumGenre());
					genre.setText(response.getAlbumGenre()[0].getData());
				}else if(response.getTrackGenre()[0].getData() != null){
					//Log.i("Tagging:", "Genre:" + response.getAlbumGenre());
					genre.setText(response.getTrackGenre()[0].getData());
				}
				//Log.i("Tagging:", "Tagging successful");
				//Log.i("Tagging:", "Deleting of file successful.");
				mp3File.delete();
				//}
			}
		}
	}
	class SongFinishedListener implements OnCompletionListener{
		@Override
		public void onCompletion(MediaPlayer mp) {
			playNextSong();
		}
	}
	private void playNextSong() {
		//Log.i("Next", "Next() invoked");
		GetNewURL GNU = new GetNewURL();
		GNU.start();
		title.setText("");
		artist.setText("");
		album.setText("");
		year.setText("");
		genre.setText("");
		try {
			GNU.join();
			//clear metadata on screen
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		streamMedia.setNext(currentSongString, currentArtworkString);
		streamMedia.initializeMMP();
		new DownloadTempFileForTagging().start();
		//delete current MP3 file from field
		//reset text fields
		//delete tag information from field
	}
}
