package it.sephiroth.android.test.multisharing;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.aviary.test.sharing.R;

public class MultipleSharingActivity extends Activity {

	static final String LOG_TAG = "share";

	private static final int ACTION_REQUEST_SHARE = 1;

	ImageView imageView;
	Button button;
	Uri uri;
	
	/** queue containing the list of activities to launch */
	List<ResolveInfo> mQueue = new LinkedList<ResolveInfo>();

	@Override
	public void onCreate( Bundle savedInstanceState ) {
		Log.i( LOG_TAG, "onCreate: " + savedInstanceState );

		super.onCreate( savedInstanceState );
		
		setContentView( R.layout.main );

		// if restored from a saved instance, recreate the queue 
		// and continue processing intents
		if ( null != savedInstanceState ) {
			if ( savedInstanceState.containsKey( "QUEUE" ) ) {
				ResolveInfo[] queue = (ResolveInfo[]) savedInstanceState.getParcelableArray( "QUEUE" );
				uri = Uri.parse( savedInstanceState.getString( "URI" ) );
				Log.d( LOG_TAG, "queue: " + queue );
				Log.d( LOG_TAG, "uri: " + uri );
				resumeProcess( queue );
			}
		} else {
			Intent intent = getIntent();
			if( handleIntent( intent ) ){
				multipleShare( uri );
			}
		}

		if ( null == uri ) {
			uri = pickRandomImage();
		}

		button.setOnClickListener( new OnClickListener() {

			@Override
			public void onClick( View v ) {
				multipleShare( uri );
			}
		} );
	}
	
	private boolean handleIntent( final Intent intent ){
		if( intent != null && Intent.ACTION_SEND.equals( intent.getAction()) ){
			Bundle extras = intent.getExtras();
			if( null != extras ){
				uri = (Uri) extras.get( Intent.EXTRA_STREAM );
				return true;
			}
		}
		return false;
	}
	
	
	@Override
	protected void onNewIntent( Intent intent ) {
		Log.i( LOG_TAG, "onNewIntent: " + intent );
		super.onNewIntent( intent );
		
		if( handleIntent( intent )){
			multipleShare( uri );
		}
	}

	@Override
	protected void onResume() {
		Log.i( LOG_TAG, "onResume" );
		if ( null != uri ) {
			imageView.setImageBitmap( decodeBitmap( uri ) );
		}
		super.onResume();
	}

	private Bitmap decodeBitmap( final Uri uri ) {
		ParcelFileDescriptor pd;
		try {
			pd = getContentResolver().openFileDescriptor( uri, "r" );
		} catch ( FileNotFoundException e ) {
			e.printStackTrace();
			return null;
		}

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPurgeable = true;
		Bitmap bitmap = BitmapFactory.decodeFileDescriptor( pd.getFileDescriptor(), null, options );
		if ( null != bitmap ) {
			int maxsize = Math.max( bitmap.getWidth(), bitmap.getHeight() );
			if ( maxsize > 800 ) {
				int destWidth, destHeight;

				if ( bitmap.getWidth() > bitmap.getHeight() ) {
					destWidth = 800;
					destHeight = (int) ( (float) bitmap.getHeight() / ( (float) bitmap.getWidth() / 800 ) );
				} else {
					destHeight = 800;
					destWidth = (int) ( (float) bitmap.getWidth() / ( (float) bitmap.getHeight() / 800 ) );
				}
				
				Bitmap bitmap2 = ThumbnailUtils.extractThumbnail( bitmap, destWidth, destHeight );
				bitmap.recycle();
				return bitmap2;
			}
			return bitmap;
		}
		return null;
	}

	@Override
	protected void onSaveInstanceState( Bundle outState ) {
		Log.i( LOG_TAG, "onSaveInstanceState" );
		super.onSaveInstanceState( outState );

		if ( mQueue.size() > 0 ) {
			Log.d( LOG_TAG, "saving queue of " + mQueue.size() + " elements" );
			outState.putParcelableArray( "QUEUE", mQueue.toArray( new ResolveInfo[mQueue.size()] ) );
			outState.putString( "URI", uri.toString() );
		}
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();

		imageView = (ImageView) findViewById( R.id.image );
		button = (Button) findViewById( R.id.button );
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );

		if ( requestCode == ACTION_REQUEST_SHARE ) {
			Log.i( LOG_TAG, "onActivityResult: " + resultCode + ", with data: " + data );
			nextIntent();
		}

	}

	/**
	 * Create the Multi share dialog 
	 * @param uri
	 */
	private void multipleShare( Uri uri ) {
		
		// get the list of sharing apps
		List<ResolveInfo> list = getActivities();
		for ( ResolveInfo info : list ) {
			Log.d( LOG_TAG, "app: " + info.loadLabel( getPackageManager() ) );
		}

		final Dialog dialog = new Dialog( this );
		dialog.setTitle( "Select Applications" );
		dialog.setContentView( R.layout.share_dialog );
		dialog.setCancelable( false );
		dialog.show();

		final ListView view = (ListView) dialog.findViewById( R.id.listView );
		Button button_ok = (Button) dialog.findViewById( R.id.button_ok );
		Button button_cancel = (Button) dialog.findViewById( R.id.button_cancel );

		button_cancel.setOnClickListener( new OnClickListener() {

			@Override
			public void onClick( View v ) {
				dialog.dismiss();
			}
		} );

		button_ok.setOnClickListener( new OnClickListener() {

			@Override
			public void onClick( View v ) {
				startProcess( ( (ShareAdapter) view.getAdapter() ).mCheckedList );
				dialog.dismiss();
			}
		} );

		view.setAdapter( new ShareAdapter( this, -1, -1, list ) );
	}
	

	/**
	 * Start the share process
	 * @param items
	 */
	private void startProcess( HashMap<ResolveInfo, Boolean> items ) {
		Log.i( LOG_TAG, "startProcess..." );
		synchronized ( mQueue ) {
			mQueue.clear();
			mQueue.addAll( items.keySet() );
		}
		nextIntent();
	}
	
	private void resumeProcess( ResolveInfo[] queue ) {
		if( queue != null ){
			synchronized ( mQueue ) {
				mQueue.clear();
				for( int i = 0; i < queue.length; i++ ){
					mQueue.add( queue[i] );
				}
			}
			nextIntent();
		}
	}

	/**
	 * Execute the next item in the queue
	 */
	protected void nextIntent() {
		Log.i( LOG_TAG, "nextIntent: " + mQueue.size() );

		if ( mQueue.size() > 0 ) {
			ResolveInfo item = mQueue.remove( 0 );

			Intent intent = new Intent( Intent.ACTION_SEND );
			intent.setComponent( new ComponentName( item.activityInfo.packageName, item.activityInfo.name ) );
			intent.putExtra( Intent.EXTRA_STREAM, uri );
			intent.setType( "image/*" );
			Log.d( LOG_TAG, "launching: " + item.activityInfo.packageName + "/" + item.activityInfo.name );
			Log.d( LOG_TAG, "uri: " + uri );
			startActivityForResult( intent, ACTION_REQUEST_SHARE );
		} else {
			Log.i( LOG_TAG, "completed!" );
			Toast.makeText( this, "Completed!", Toast.LENGTH_SHORT ).show();
		}

	}

	class ShareAdapter extends ArrayAdapter<ResolveInfo> implements OnCheckedChangeListener {

		LayoutInflater inflater;
		PackageManager pm;
		HashMap<ResolveInfo, Boolean> mCheckedList = new HashMap<ResolveInfo, Boolean>();

		public ShareAdapter( Context context, int resource, int textViewResourceId, List<ResolveInfo> objects ) {
			super( context, resource, textViewResourceId, objects );
			inflater = LayoutInflater.from( getContext() );
			pm = getContext().getPackageManager();
		}

		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {
			ResolveInfo info = getItem( position );

			View view = inflater.inflate( R.layout.share_item, parent, false );
			ImageView image = (ImageView) view.findViewById( R.id.image );
			TextView text = (TextView) view.findViewById( R.id.text );
			CheckBox checkbox = (CheckBox) view.findViewById( R.id.checkbox );
			checkbox.setOnCheckedChangeListener( this );
			checkbox.setTag( Integer.valueOf( position ) );
			checkbox.setChecked( mCheckedList.containsKey( info ) );

			text.setText( info.loadLabel( pm ) );
			image.setImageDrawable( info.loadIcon( pm ) );

			return view;
		}

		@Override
		public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
			Integer position = (Integer) buttonView.getTag();
			ResolveInfo info = getItem( position );
			if ( isChecked ) {
				mCheckedList.put( info, true );
			} else {
				mCheckedList.remove( info );
			}

			Log.d( LOG_TAG, "checked: " + mCheckedList.size() );
		}

	}

	private List<ResolveInfo> getActivities() {
		Intent intent = new Intent( Intent.ACTION_SEND );
		intent.setType( "image/*" );
		List<ResolveInfo> list = getPackageManager().queryIntentActivities( intent, PackageManager.MATCH_DEFAULT_ONLY );
		
		final String pkg = this.getPackageName();

		// Remove the current activity from the list
		Iterator<ResolveInfo> iterator = list.iterator();
		while( iterator.hasNext() ){
			ResolveInfo current = iterator.next();
			if( current.activityInfo.packageName.equals( pkg )) {
				iterator.remove();
			}
		}
		
		return list;
	}

	/**
	 * Pick a random image from the user gallery
	 * 
	 * @return
	 */
	private Uri pickRandomImage() {
		Cursor c = getContentResolver().query( Images.Media.EXTERNAL_CONTENT_URI, new String[] { ImageColumns.DATA },
				ImageColumns.SIZE + ">?", new String[] { "90000" }, null );
		Uri uri = null;

		if ( c != null ) {
			int total = c.getCount();
			int position = (int) ( Math.random() * total );
			Log.d( LOG_TAG, "pickRandomImage. total images: " + total + ", position: " + position );
			if ( total > 0 ) {
				if ( c.moveToPosition( position ) ) {
					String data = c.getString( c.getColumnIndex( Images.ImageColumns.DATA ) );
					uri = Uri.parse( "file://" + data );
					Log.d( LOG_TAG, uri.toString() );
				}
			}
			c.close();
		}
		return uri;
	}
}