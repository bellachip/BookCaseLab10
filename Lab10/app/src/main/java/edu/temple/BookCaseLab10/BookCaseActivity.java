package edu.temple.BookCaseLab10;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import edu.temple.audiobookplayer.AudiobookService;

public class BookCaseActivity extends AppCompatActivity implements BookListFragment.OnListClickListener, BookDetailsFragment.onPlayClick {
    SharedPreferences pref;
    SharedPreferences.Editor editor;
    String internalFilename = "bookmark";
    File file;

    FragmentManager fm;
    Fragment current1;
    Fragment current2;

    int currentBookPosition = 0;
    Boolean onePane;

    ArrayList<Book> books;
    EditText etSearch;
    Button btnSearch;
    String search = "";

    int nowPlayingBookID;
    String nowPlayingTitle;
    String nowPlayingAuthor;
    int nowPlayingDuration;
    int nowPlayingPosition;
    Boolean nowPlaying;



    TextView tvNowPlaying;
    Button ibtnPlayPause;

    Button ibtnStop;

    SeekBar sbNowPlaying;

    AudiobookService.MediaControlBinder mcb;
    Intent audioBookPlayerIntent;
    boolean connected;
    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mcb = (AudiobookService.MediaControlBinder) service;
            connected = true;
            mcb.setProgressHandler(progressHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connected = false;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            mcb.stop();
            unbindService(connection);
        }
    }

    public void updateNowPlaying() {
        if (nowPlayingTitle == null) {
            nowPlayingTitle = "";
            nowPlayingAuthor = "";
        }
        String nowPlayingText = "Now Playing: " + nowPlayingTitle + " - " + nowPlayingAuthor;
        tvNowPlaying.setText(nowPlayingText);
//        if (nowPlaying) {
//            ibtnPlayPause.setImageResource(R.drawable.btnplay); //change to play image
//        } else {
//            ibtnPlayPause.setImageResource(R.drawable.btnpause); //change to pause image
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("KEY_ID", nowPlayingBookID);
        outState.putString("KEY_TITLE", nowPlayingTitle);
        outState.putString("KEY_AUTHOR", nowPlayingAuthor);
        outState.putInt("KEY_DURATION", nowPlayingDuration);
        outState.putInt("KEY_POSITION", nowPlayingPosition);
        outState.putBoolean("KEY_NOWPLAYING", nowPlaying);
        outState.putBoolean("KEY_CONNECTED", connected);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookcase);
        //BookDetailsFragment bookDetailsFragment = new BookDetailsFragment(this);

        file = new File(getFilesDir(), internalFilename);

        fm = getSupportFragmentManager();

        onePane = (findViewById(R.id.container2) == null);
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search = etSearch.getText().toString();
                searchBooks(search);
            }
        });

        if (nowPlaying == null) {
            nowPlaying = false;
        }

        pref = getPreferences(MODE_PRIVATE);
        nowPlayingBookID = pref.getInt("KEY_ID", -1);
        nowPlayingTitle = pref.getString("KEY_TITLE", null);
        nowPlayingAuthor = pref.getString("KEY_AUTHOR", null);
        nowPlayingDuration = pref.getInt("KEY_DURATION", -1);
        nowPlayingPosition = pref.getInt("KEY_POSITION", -1);
        nowPlaying = pref.getBoolean("KEY_NOWPLAYING", false);

        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        sbNowPlaying = findViewById(R.id.sbNowPlaying);
        sbNowPlaying.setProgress(nowPlayingPosition);
        sbNowPlaying.setMax(nowPlayingDuration);


        sbNowPlaying.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                nowPlayingPosition = progress;
                if (fromUser) {
                    if (connected) {
                        mcb.seekTo(nowPlayingPosition);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        ibtnPlayPause = findViewById(R.id.ibtnPlayPause);
        ibtnStop = findViewById(R.id.ibtnStop);
        ibtnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playpause();
            }
        });
        ibtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        if (savedInstanceState == null) {
            search = pref.getString("KEY_SEARCH","");
            etSearch.setText(search);
            searchBooks(search);
        } else {
            updateView();
        }

        audioBookPlayerIntent = new Intent(BookCaseActivity.this, edu.temple.audiobookplayer.AudiobookService.class);
        if (!connected) {
            bindService(audioBookPlayerIntent, connection, Context.BIND_AUTO_CREATE);
        }
        updateNowPlaying();
    }

    // Calls the public method in details fragment to display the selected book
    @Override
    public void displayBook(int index) {
        ((BookDetailsFragment) getSupportFragmentManager().findFragmentById(R.id.container2)).displayBook(books.get(index));
    }

    // Empties out the ArrayList of books, then searches retrieves and populates it with book from api
    public void searchBooks(final String query) {
        books = new ArrayList<>();
        Thread t = new Thread() {
            @Override
            public void run() {
                URL url;
                try {
                    url = new URL(" https://kamorris.com/lab/audlib/booksearch.php?search=" + query);
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(
                                    url.openStream()));
                    String response = "", tmpResponse;
                    tmpResponse = r.readLine();
                    while (tmpResponse != null) {
                        response = response + tmpResponse;
                        tmpResponse = r.readLine();
                    }
                    JSONArray booksArray = new JSONArray(response);
                    Message msg = Message.obtain();
                    msg.obj = booksArray;
                    responseHandler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
        editor = pref.edit();
        editor.putString("KEY_SEARCH", query);
        editor.commit();
    }

    Handler responseHandler = new Handler((new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            JSONArray responseArray = (JSONArray) msg.obj;
            if (responseArray.length() > 0) {
                try {
                    for (int i = 0; i < responseArray.length(); i++) {
                        int book_id = responseArray.getJSONObject(i).getInt("book_id");
                        String title = responseArray.getJSONObject(i).getString("title");
                        String author = responseArray.getJSONObject(i).getString("author");
                        int published = responseArray.getJSONObject(i).getInt("published");
                        String cover_url = responseArray.getJSONObject(i).getString("cover_url");
                        int duration = responseArray.getJSONObject(i).getInt("duration");
                        books.add(new Book(book_id, title, author, published, cover_url, duration));
                    }
                    updateView();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                search = "";
                Toast t = Toast.makeText(getApplicationContext(), "No results found...\n\nTry something else", Toast.LENGTH_SHORT);
                t.setGravity(Gravity.CENTER, 0, 0);
                View view = t.getView();
                view.getBackground().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                TextView text = view.findViewById(android.R.id.message);
                text.setTextColor(Color.WHITE);
                t.show();
            }
            return false;
        }
    }));


    // called to change what is displayed on screen
    public void updateView() {
        current1 = fm.findFragmentById(R.id.container1); // gets references to existing fragments if any
        current2 = fm.findFragmentById(R.id.container2);

        if (onePane) { //if portrait
            if (current1 instanceof BookListFragment) // check if previously had a BookListFragment(landscape mode)
                books = ((BookListFragment) current1).getBooks();
            ViewPagerFragment vp = ViewPagerFragment.newInstance(books);
            fm.beginTransaction().replace(R.id.container1, vp).commit();

            if (current2 instanceof BookDetailsFragment) // check if a book has previously been selected in from list view
                currentBookPosition = ((BookListFragment) current1).getPosition();
            else currentBookPosition = 0;
            //vp.gotoBook(currentBookPosition);
        } else { // landscape
            if (current1 instanceof ViewPagerFragment) {
                books = ((ViewPagerFragment) current1).getBooks();
                currentBookPosition = ((ViewPagerFragment) current1).getPosition();
            }
            BookListFragment bl = BookListFragment.newInstance(books);
            fm.beginTransaction().replace(R.id.container1, bl).commit();
            fm.beginTransaction().replace(R.id.container2, BookDetailsFragment.newInstance(books.get(currentBookPosition))).commit();
        }
    }

    public void saveNowPlaying() {
        editor = pref.edit();
        editor.putInt("KEY_ID", nowPlayingBookID);
        editor.putString("KEY_TITLE", nowPlayingTitle);
        editor.putString("KEY_AUTHOR", nowPlayingAuthor);
        editor.putInt("KEY_DURATION", nowPlayingDuration);
        editor.putInt("KEY_POSITION", nowPlayingPosition);
        editor.putBoolean("KEY_NOWPLAYING", nowPlaying);
        editor.commit();
    }

    @Override
    public void play(Book book) {
        startService(audioBookPlayerIntent);
        if (connected) {
            nowPlayingBookID = book.getId();
            nowPlayingTitle = book.getTitle();
            nowPlayingAuthor = book.getAuthor();
            nowPlayingDuration = book.getDuration();
            sbNowPlaying.setProgress(0);
            mcb.play(nowPlayingBookID);
            nowPlaying = mcb.isPlaying();
            sbNowPlaying.setMax(0);
            sbNowPlaying.setMax(nowPlayingDuration);
            updateNowPlaying();
            saveNowPlaying();
        }
    }

    @Override
    public void play(Book book, File mp3) {
        Log.d("mp3", "Playing from " + book.getTitle() + "mp3");
        startService(audioBookPlayerIntent);
        if (connected) {
            nowPlayingBookID = book.getId();
            nowPlayingTitle = book.getTitle();
            nowPlayingAuthor = book.getAuthor();
            nowPlayingDuration = book.getDuration();
            sbNowPlaying.setProgress(0);
            mcb.play(mp3);
            nowPlaying = mcb.isPlaying();
            sbNowPlaying.setMax(0);
            sbNowPlaying.setMax(nowPlayingDuration);
            updateNowPlaying();
            saveNowPlaying();
        }
    }

    public void playpause() {
        if (connected) {
            nowPlaying = mcb.isPlaying();
            mcb.pause();
            updateNowPlaying();
        }
    }

    public void stop() {
        if (connected) {
            mcb.stop();
            nowPlayingBookID = 0;
            nowPlayingTitle = "";
            nowPlayingAuthor = "";
            nowPlayingDuration = 0;
            sbNowPlaying.setProgress(0);
            nowPlaying = mcb.isPlaying();
            updateNowPlaying();
            stopService(audioBookPlayerIntent);
        }
    }

    Handler progressHandler = new Handler((new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg != null) {
                if (msg.obj instanceof AudiobookService.BookProgress) {
                    AudiobookService.BookProgress nowPlayingProgressObj = (AudiobookService.BookProgress) msg.obj;
                    if (nowPlayingProgressObj.getProgress() < nowPlayingDuration) {
                        nowPlayingPosition = nowPlayingProgressObj.getProgress();
                        if (connected) {
                            sbNowPlaying.setProgress(nowPlayingPosition);
                        }
                    }
                    if (nowPlayingProgressObj.getProgress() >= nowPlayingDuration) { //reached end of book
                        if (connected) {
                            stop();
                        }
                    }
                }
            }
            return false;
        }
    }));

}
