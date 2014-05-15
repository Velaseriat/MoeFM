package com.avanti.moefm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.blinkenlights.jid3.ID3Exception;
import org.blinkenlights.jid3.MP3File;
import org.blinkenlights.jid3.v2.APICID3V2Frame;
import org.blinkenlights.jid3.v2.ID3V2_3_0Tag;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagOptionSingleton;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.webkit.WebView;

import com.avanti.moefm.MoeFMMainActivity.SongFinishedListener;
import com.gracenote.mmid.MobileSDK.GNConfig;
import com.gracenote.mmid.MobileSDK.GNOperations;
import com.gracenote.mmid.MobileSDK.GNSearchResponse;
import com.gracenote.mmid.MobileSDK.GNSearchResult;
import com.gracenote.mmid.MobileSDK.GNSearchResultReady;

public class StreamMedia{
	private String mp3URLString, jpgURLString;
	private String prevMP3URLString, prevJPGURLString;
	private File mp3File, jpgFile;
	private MediaPlayer mediaPlayer;
	private SongFinishedListener songFinishedListener;
	private MoeMP3Player moeMP3Player;
	private WebView webView;
	public boolean isPlaying;
	private GNConfig config;

	public StreamMedia (String mp3URLString, String jpgURLString, SongFinishedListener songFinishedListener, GNConfig config){
		this.mp3URLString = mp3URLString;
		this.jpgURLString = jpgURLString;
		this.songFinishedListener = songFinishedListener;
		this.mediaPlayer = new MediaPlayer();
		this.moeMP3Player = new MoeMP3Player();
		this.isPlaying = false;
		this.config = config;
	}
	public void setArtworkView(WebView webView){
		this.webView = webView;
		webView.getSettings().setBuiltInZoomControls(true);
		webView.getSettings().setLoadWithOverviewMode(true);
		webView.getSettings().setUseWideViewPort(true);
		webView.loadUrl(this.jpgURLString);

	}
	class MoeMP3Player extends Thread{
		public void run(){
			try {
				isPlaying = true;
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mediaPlayer.setDataSource(mp3URLString);
				mediaPlayer.prepare();
				mediaPlayer.setOnCompletionListener(songFinishedListener);
				playPlayer();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		public void playPlayer(){
			isPlaying = true;
			mediaPlayer.start();
		}
		public void pausePlayer(){
			isPlaying = false;
			mediaPlayer.pause();
		}
		public void resetPlayer(){
			isPlaying = false;
			mediaPlayer.reset();
		}
	}
	class DownloadMP3Operator extends Thread implements GNSearchResultReady{
		GNSearchResponse response;
		MP3File oMediaFile;
		ID3V2_3_0Tag id3;
		public DownloadMP3Operator(GNSearchResponse response){
			this.response = response;
		}
		public void run(){
			URL url;
			URLConnection conn;
			int lastSlash;
			String outputMP3;
			BufferedInputStream bis;
			BufferedOutputStream bos;
			FileOutputStream fos;
			try {
				url = new URL(mp3URLString);
				conn = url.openConnection();
				conn.setUseCaches(false);
				//Log.i("Download:", "");
				// get the filename
				lastSlash = url.toString().lastIndexOf('/');
				outputMP3 = "file.bin";
				if(lastSlash >=0)
					outputMP3 = url.toString().substring(lastSlash + 1);
				if(outputMP3.equals(""))
					outputMP3 = "test.bin";
				//Log.i("Download:", "Has filename: " + outputMP3);

				// start download
				bis = new BufferedInputStream(conn.getInputStream());
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MoeFM/").mkdirs();
				mp3File = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MoeFM/" +  outputMP3);
				//outFile = new File(Environment.getExternalStorageDirectory() + "/" +  fileName);
				fos = new FileOutputStream(mp3File);
				bos = new BufferedOutputStream(fos);
				byte[] data = new byte[8192];
				int bytesRead = 0;
				while((bytesRead = bis.read(data, 0, data.length)) >= 0)
					bos.write(data, 0, bytesRead);
				bos.close();
				fos.close();
				bis.close();

				//Log.i("Download:", "MP3 File: " + mp3File.getName());
				InputStream is = new URL(jpgURLString).openStream();
				//Log.i("Download:", "Response from " + jpgURLString + is.available());

				ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
				byte[] b = new byte[1024];
				int bytesRead3 = 0;
				while ((bytesRead3 = is.read(b)) != -1)
					bos2.write(b, 0, bytesRead3);
				byte[] bytes = bos2.toByteArray();
				oMediaFile = new MP3File(mp3File);
				//Log.i("Download:", "");
				id3 = new ID3V2_3_0Tag();
				APICID3V2Frame apic;
				apic = new APICID3V2Frame("image/jpg", APICID3V2Frame.PictureType.FrontCover, "", bytes);
				is.close();
				bos2.close();
				//Log.i("Download:", "Before creating the file");
				id3.addAPICFrame(apic);
				oMediaFile.setID3Tag(id3);
				oMediaFile.sync();
				//Log.i("Download:", "Set artwork");
				GNOperations.recognizeMIDFileFromFile (this, config, mp3File.getAbsolutePath());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ID3Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		@Override
		public void GNResultReady(GNSearchResult result) {
			if (result != null){
				//Log.i("Marker:", "Result is empty" );
				//else{
				try {
					TagOptionSingleton.getInstance().setAndroid(true);
					AudioFile audioFile = AudioFileIO.read(mp3File);
					Tag newTag = audioFile.getTag();
					this.response = result.getBestResponse();
					//Log.i("Download:", "Response not null");
					//Log.i("Download:", response.toString());
					if(response.getTrackTitle() != null){
						//Log.i("Download:", response.getTrackTitle());
						//id3.setTitle(response.getTrackTitle().toString());
						newTag.setField(FieldKey.TITLE, response.getTrackTitle());
						////Log.i("Download:", "Post: " + id3.getTitle());
					}
					if(response.getAlbumArtist() != null){
						//Log.i("Download:", response.getAlbumArtist());
						//id3.setArtist(response.getArtist().toString());
						newTag.setField(FieldKey.ALBUM_ARTIST, response.getAlbumArtist());
						////Log.i("Download:", "Post: " + id3.getArtist());
					}
					if(response.getArtist() != null){
						//Log.i("Download:", response.getAlbumArtist());
						//id3.setArtist(response.getArtist().toString());
						newTag.setField(FieldKey.ARTIST, response.getArtist());
						////Log.i("Download:", "Post: " + id3.getArtist());
					}
					if(response.getAlbumTitle() != null){
						//Log.i("Download:", response.getAlbumTitle());
						//id3.setAlbum(response.getAlbumTitle().toString());
						newTag.setField(FieldKey.ALBUM, response.getAlbumTitle());
						////Log.i("Download:", "Post: " + id3.getAlbum());
					}
					if(response.getAlbumGenre()[0].getData() != null){
						//Log.i("Download:", response.getAlbumGenre()[0].getData() );
						//id3.setGenre(response.getAlbumGenre()[0].getData().toString());
						newTag.setField(FieldKey.GENRE, response.getAlbumGenre()[0].getData());
						////Log.i("Download:", "Post: " + id3.getGenre());
					}
					else if(response.getTrackGenre()[0].getData() != null){
						//Log.i("Download:", response.getTrackGenre()[0].getData());
						//id3.setGenre(response.getAlbumGenre()[0].getData().toString());
						newTag.setField(FieldKey.GENRE, response.getTrackGenre()[0].getData());
						////Log.i("Download:", "Post: " + id3.getGenre());
					}
					if(response.getAlbumReleaseYear() != null){
						//Log.i("Download:", response.getAlbumReleaseYear());
						//id3.setYear((Integer.parseInt(response.getAlbumReleaseYear())));
						newTag.setField(FieldKey.YEAR, response.getAlbumReleaseYear());
						////Log.i("Download:", "Post: " + id3.getGenre());
					}
					audioFile.commit();
				} catch (CannotReadException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TagException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ReadOnlyFileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidAudioFrameException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CannotWriteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//Log.i("Downloading:", "Response process successful");
			}
			//Log.i("Download:", "Successful embedding");
			//Enable save button AND save MP3 file in field AND save tag information
		}
	}
	class DownloadJPGOperator extends Thread{
		public void run(){
			URL url;
			URLConnection conn;
			int lastSlash;
			String outputJPG;
			BufferedInputStream bis;
			BufferedOutputStream bos;
			FileOutputStream fos;

			try {
				url = new URL(jpgURLString);
				conn = url.openConnection();
				conn.setUseCaches(false);
				// get the filename
				lastSlash = url.toString().lastIndexOf('/');
				outputJPG = "file.bin";
				if(lastSlash >=0)
					outputJPG = url.toString().substring(lastSlash + 1);
				if(outputJPG.equals(""))
					outputJPG = "test.bin";
				// start download
				bis = new BufferedInputStream(conn.getInputStream());
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MoeFM/").mkdirs();
				jpgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MoeFM/" +  outputJPG);
				fos = new FileOutputStream(jpgFile);
				bos = new BufferedOutputStream(fos);
				byte[] data2 = new byte[8192];
				int bytesRead2 = 0;
				while((bytesRead2 = bis.read(data2, 0, data2.length)) >= 0)
					bos.write(data2, 0, bytesRead2);
				bos.close();
				fos.close();
				bis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	public void initializeMMP(){
		moeMP3Player.run();
	}
	public void playMMP(){
		moeMP3Player.playPlayer();
	}
	public void pauseMMP(){
		moeMP3Player.pausePlayer();
	}
	public void resetMMP(){
		moeMP3Player.resetPlayer();
	}
	public String getPreviousMP3(){
		return this.prevMP3URLString;
	}
	public String getPreviousJPG(){
		return this.prevJPGURLString;
	}
	public void setNext(String mp3URLString, String jpgURLString){
		this.prevMP3URLString = this.mp3URLString;
		this.prevJPGURLString = this.jpgURLString;
		this.mp3URLString = mp3URLString;
		this.jpgURLString = jpgURLString;
		moeMP3Player.resetPlayer();
		webView.loadUrl(this.jpgURLString);
		//disable Save button
		//delete current MP3
		//delete current tag
		
	}
	public void downloadMP3(GNSearchResponse response){
		new DownloadMP3Operator(response).start();
	}
	public void downloadJPG(String jpgURLString){
		new DownloadJPGOperator().start();
	}
}
